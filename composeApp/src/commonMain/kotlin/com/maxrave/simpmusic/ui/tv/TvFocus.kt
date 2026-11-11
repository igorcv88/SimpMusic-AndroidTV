package com.maxrave.simpmusic.ui.tv

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.TraversableNode
import androidx.compose.ui.node.findNearestAncestor
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

/**
 * How a focused element is marked on TV (spec, "Focus"). Focus is shown by scale, light and
 * elevation and a fine translucent rim aligned with the content.
 *
 * - [Card]: artwork cards. Scale 1.025 and a 24 dp glow in the artwork's tint.
 * - [Control]: buttons, chips, rail and top-bar items. Scale 1.015; the fill and content colours are
 *   swapped by the control itself (see `TvButtons.kt`), so nothing is drawn here.
 * - [Row]: list rows. Scale 1.008 and a `Surface2` backing in the row's shape.
 * - [Backing]: P1 call sites not yet migrated (`tvFocusIndicator()`). Scale 1.015 and a `Surface2`
 *   backing.
 */
enum class TvFocusStyle { Card, Control, Row, Backing }

fun focusScaleFor(style: TvFocusStyle): Float =
    when (style) {
        TvFocusStyle.Card -> 1.025f
        TvFocusStyle.Control -> 1.015f
        TvFocusStyle.Row -> 1.008f
        TvFocusStyle.Backing -> 1.015f
    }

/**
 * Marks focus on TV in [style]. Off TV it returns the receiver unchanged.
 *
 * It goes before the element's own `clip(...)` and before whatever makes it focusable, so that the
 * glow is not cut by that clip and the focus events of that target reach it.
 *
 * What it guarantees:
 * - **Layout never moves.** The scale is a graphics-layer property; the element measures and places
 *   exactly as without it. The focused element is raised in z-order so it draws over its neighbours.
 * - **One scale per element.** When one focus mark wraps another on the same focus target — a
 *   `Card` around the `Backing` that `tvClickable` adds, for example — only the outermost one
 *   shows; the inner one stays at rest. That is what keeps a card at 1.025 instead of multiplying nested scales.
 * - **Interruptible.** The spring starts from the current value and velocity. Holding a D-pad key
 *   (about 20 focus changes a second) retargets one running animation per element.
 * - **Reduced motion.** With the animator scale at 0 the mark is applied in the same frame.
 * - **No work per frame in composition.** The progress is read only in the layer and draw phases.
 *
 * @param shape the outline of the backing ([TvFocusStyle.Row], [TvFocusStyle.Backing]) and of the
 * glow ([TvFocusStyle.Card]). Null picks the style's default.
 * @param glow the [TvFocusStyle.Card] glow colour, normally the artwork's tint. Null draws a plain
 * shadow.
 */
fun Modifier.tvFocus(
    style: TvFocusStyle,
    shape: Shape? = null,
    glow: Color? = null,
): Modifier =
    composed {
        if (!LocalTvDevice.current) return@composed this
        val motion = LocalTvMotion.current
        val state = remember { TvFocusState() }
        this
            .tvRememberFocus()
            .then(TvFocusLayerElement(state, style, motion.focusSpring, motion.reduced))
            .then(TvFocusDrawElement(state, style, shape, glow))
    }

/**
 * Put on a [TvFocusStyle.Card]'s artwork: the glow is then drawn around the artwork instead of
 * around the whole card (artwork plus its title and subtitle). Off TV it returns the receiver.
 */
fun Modifier.tvFocusGlowAnchor(): Modifier =
    composed {
        if (LocalTvDevice.current) this.then(TvGlowAnchorElement) else this
    }

/** What the layer node and the draw node of one [tvFocus] share. Read in layer and draw only. */
private class TvFocusState {
    var progress by mutableFloatStateOf(0f)
    var active by mutableStateOf(false)

    /** The nearest focus mark above this one, resolved when this one gains focus. */
    var outer: TvFocusState? = null

    /** The glow anchor's bounds in the draw node's coordinates; null means the node's own bounds. */
    var glowBounds: Rect? = null

    /** True while a focus mark above this one is showing: this one must not add a second scale. */
    fun isOverridden(): Boolean {
        var state = outer
        while (state != null) {
            if (state.active) return true
            state = state.outer
        }
        return false
    }

    fun shownProgress(): Float = if (isOverridden()) 0f else progress

    fun reset() {
        progress = 0f
        active = false
        outer = null
        glowBounds = null
    }
}

private object TvFocusLayerKey

private object TvFocusDrawKey

private data class TvFocusLayerElement(
    val state: TvFocusState,
    val style: TvFocusStyle,
    val spec: AnimationSpec<Float>,
    val reduced: Boolean,
) : ModifierNodeElement<TvFocusLayerNode>() {
    override fun create() = TvFocusLayerNode(state, style, spec, reduced)

    override fun update(node: TvFocusLayerNode) {
        node.state = state
        node.style = style
        node.spec = spec
        node.updateReducedMotion(reduced)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "tvFocus"
        properties["style"] = style
    }
}

/**
 * Tracks focus, runs the spring and applies the scale. It is a layout modifier only so that it can
 * place its content in a layer and with a z-index: the constraints and the size pass through.
 */
private class TvFocusLayerNode(
    var state: TvFocusState,
    var style: TvFocusStyle,
    var spec: AnimationSpec<Float>,
    private var reduced: Boolean,
) : Modifier.Node(),
    FocusEventModifierNode,
    LayoutModifierNode,
    TraversableNode {
    override val traverseKey: Any = TvFocusLayerKey

    private var animation: Job? = null
    private var velocity = 0f

    fun updateReducedMotion(value: Boolean) {
        if (reduced == value) return
        reduced = value
        if (value) animateTo(if (state.active) 1f else 0f)
    }

    // Allocated once: read by the layer on every animation frame.
    private val layerBlock: GraphicsLayerScope.() -> Unit = {
        val scale = 1f + (focusScaleFor(style) - 1f) * state.shownProgress()
        scaleX = scale
        scaleY = scale
    }

    override fun onFocusEvent(focusState: FocusState) {
        val focused = focusState.isFocused
        if (focused == state.active) return
        if (focused) {
            state.outer = (findNearestAncestor(TvFocusLayerKey) as? TvFocusLayerNode)?.state
        }
        state.active = focused
        animateTo(if (focused) 1f else 0f)
    }

    private fun animateTo(target: Float) {
        animation?.cancel()
        if (reduced || !isAttached) {
            velocity = 0f
            state.progress = target
            return
        }
        animation =
            coroutineScope.launch {
                animate(
                    initialValue = state.progress,
                    targetValue = target,
                    initialVelocity = velocity,
                    animationSpec = spec,
                ) { value, currentVelocity ->
                    state.progress = value
                    velocity = currentVelocity
                }
                velocity = 0f
            }
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            // The focused element draws over its neighbours, whichever side they are on.
            val zIndex = if (state.active && !state.isOverridden()) 1f else 0f
            placeable.placeWithLayer(0, 0, zIndex = zIndex, layerBlock = layerBlock)
        }
    }

    // A lazy list reuses the node for another item: it must not come back focused.
    override fun onReset() {
        animation?.cancel()
        velocity = 0f
        state.reset()
    }

    override fun onDetach() {
        animation?.cancel()
        velocity = 0f
    }
}

private data class TvFocusDrawElement(
    val state: TvFocusState,
    val style: TvFocusStyle,
    val shape: Shape?,
    val glow: Color?,
) : ModifierNodeElement<TvFocusDrawNode>() {
    override fun create() = TvFocusDrawNode(state, style, shape, glow)

    override fun update(node: TvFocusDrawNode) {
        val shapeChanged = node.shape != shape || node.style != style
        node.state = state
        node.style = style
        node.shape = shape
        node.glow = glow
        if (shapeChanged) node.clearOutline()
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "tvFocusDraw"
    }
}

/**
 * Draws the backing or the glow behind the content. It sits below [TvFocusLayerNode] in the chain,
 * so what it draws is inside the scaled layer and grows with the element.
 */
private class TvFocusDrawNode(
    var state: TvFocusState,
    var style: TvFocusStyle,
    var shape: Shape?,
    var glow: Color?,
) : Modifier.Node(),
    DrawModifierNode,
    TraversableNode {
    override val traverseKey: Any = TvFocusDrawKey

    private var outline: Outline? = null
    private var outlineSize = Size.Unspecified
    private var outlineDirection = LayoutDirection.Ltr
    private var outlineDensity = 0f
    private var haloStroke: Stroke? = null

    fun clearOutline() {
        outline = null
    }

    /** Called by [TvGlowAnchorNode] whenever the artwork is placed. */
    fun onGlowAnchorPlaced(anchor: LayoutCoordinates) {
        if (!isAttached) return
        val bounds = requireLayoutCoordinates().localBoundingBoxOf(anchor, clipBounds = false)
        if (bounds != state.glowBounds) {
            state.glowBounds = bounds
            clearOutline()
            invalidateDraw()
        }
    }

    override fun ContentDrawScope.draw() {
        val progress = state.shownProgress()
        if (progress > 0.001f) {
            when (style) {
                TvFocusStyle.Card -> {
                    drawGlow(progress)
                }

                TvFocusStyle.Row, TvFocusStyle.Backing -> {
                    drawOutline(resolveOutline(size), TvPalette.FocusFill, alpha = progress.coerceIn(0f, 1f))
                }

                // The control swaps its own fill and content colour.
                TvFocusStyle.Control -> {
                    Unit
                }
            }
        }
        drawContent()
        if (progress > 0.001f) {
            val bounds = if (style == TvFocusStyle.Card) state.glowBounds ?: Rect(Offset.Zero, size) else Rect(Offset.Zero, size)
            translate(bounds.left, bounds.top) {
                drawOutline(
                    resolveOutline(bounds.size),
                    brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.72f), Color.White.copy(alpha = 0.18f))),
                    alpha = progress.coerceIn(0f, 1f),
                    style = Stroke(1.dp.toPx()),
                )
            }
        }
    }

    private fun DrawScope.drawGlow(progress: Float) {
        val bounds = state.glowBounds ?: Rect(Offset.Zero, size)
        if (bounds.isEmpty) return
        val corner =
            when (val resolved = resolveOutline(bounds.size)) {
                is Outline.Rounded -> resolved.roundRect.topLeftCornerRadius.x
                is Outline.Rectangle -> 0f
                is Outline.Generic -> ((shape ?: TvShapes.Card) as? TvSquircleShape)?.radius?.toPx() ?: TV_CARD_CORNER.toPx()
            }
        val stroke =
            haloStroke?.takeIf { outlineDensity == density }
                ?: Stroke(TvDimens.FocusGlow.toPx() / HALO_RINGS).also {
                    haloStroke = it
                    outlineDensity = density
                }
        drawSoftHalo(
            bounds = bounds,
            cornerPx = corner,
            color = glow ?: Color.White,
            alpha = CARD_GLOW_ALPHA * progress.coerceIn(0f, 1f),
            ring = stroke,
            dropPx = CARD_GLOW_DROP.toPx(),
        )
    }

    private fun DrawScope.resolveOutline(forSize: Size): Outline {
        val cached = outline
        if (cached != null && outlineSize == forSize && outlineDirection == layoutDirection) return cached
        val resolvedShape =
            shape ?: when (style) {
                TvFocusStyle.Card -> TvShapes.Card
                TvFocusStyle.Row -> TvShapes.Small
                TvFocusStyle.Control -> TvShapes.Control
                TvFocusStyle.Backing -> defaultBackingShape(forSize)
            }
        return resolvedShape.createOutline(forSize, layoutDirection, this).also {
            outline = it
            outlineSize = forSize
            outlineDirection = layoutDirection
        }
    }
}

private object TvGlowAnchorElement : ModifierNodeElement<TvGlowAnchorNode>() {
    override fun create() = TvGlowAnchorNode()

    override fun update(node: TvGlowAnchorNode) = Unit

    override fun hashCode(): Int = 0x7476 // "tv"

    override fun equals(other: Any?): Boolean = other === this

    override fun InspectorInfo.inspectableProperties() {
        name = "tvFocusGlowAnchor"
    }
}

private class TvGlowAnchorNode :
    Modifier.Node(),
    LayoutAwareModifierNode {
    override fun onPlaced(coordinates: LayoutCoordinates) {
        (findNearestAncestor(TvFocusDrawKey) as? TvFocusDrawNode)?.onGlowAnchorPlaced(coordinates)
    }
}

private const val CARD_GLOW_ALPHA = 0.16f
private const val HALO_RINGS = 12
private val CARD_GLOW_DROP = 4.dp
private val TV_CARD_CORNER = 16.dp
private val TV_BACKING_MAX_CORNER = 16.dp

/**
 * A soft halo around [bounds], drawn as concentric rings whose alpha falls off with the square of
 * the distance. API 30 has no RenderEffect and a platform elevation shadow is almost invisible on
 * a near-black page, so the blur is approximated; at 3 m twelve 2 dp rings read as one gradient.
 * It allocates nothing: [ring] is the caller's cached stroke, one ring wide.
 *
 * It is drawn outside the element's bounds, so the element must not sit behind a clip of its own.
 */
internal fun DrawScope.drawSoftHalo(
    bounds: Rect,
    cornerPx: Float,
    color: Color,
    alpha: Float,
    ring: Stroke,
    dropPx: Float = 0f,
) {
    if (alpha <= 0f) return
    val step = ring.width
    val rings = HALO_RINGS
    for (i in 0 until rings) {
        val falloff = 1f - i / rings.toFloat()
        val grow = (i + 0.5f) * step
        drawRoundRect(
            color = color,
            topLeft = Offset(bounds.left - grow, bounds.top - grow + dropPx),
            size = Size(bounds.width + grow * 2f, bounds.height + grow * 2f),
            cornerRadius = CornerRadius(max(0f, cornerPx + grow)),
            alpha = alpha * falloff * falloff,
            style = ring,
        )
    }
}

/**
 * The backing of a P1 call site that named no shape: a circle on square elements, a capsule on
 * pills and a 16 dp corner otherwise, because most of them sit behind a `clip(...)` that would cut
 * the corners off a rectangle.
 */
private fun Density.defaultBackingShape(size: Size): Shape {
    val shortSide = minOf(size.width, size.height)
    val isSquare = shortSide > 0f && abs(size.width - size.height) / shortSide < 0.1f
    if (isSquare) return TvShapes.Small
    return RoundedCornerShape(minOf(shortSide / 2f, TV_BACKING_MAX_CORNER.toPx()))
}
