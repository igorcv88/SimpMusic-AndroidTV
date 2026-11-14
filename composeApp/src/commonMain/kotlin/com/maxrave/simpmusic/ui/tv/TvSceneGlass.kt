package com.maxrave.simpmusic.ui.tv

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Shared TV material images. API 30 publishes an isolated sampled content frame through
 * [backdrop]; other tiers retain the eligible ambient artwork path. Each surface samples the
 * corresponding root coordinates without running its own blur.
 */
@Stable
class TvScene {
    internal var backdrop by mutableStateOf<TvBackdrop?>(null)
    /** Only the destination actually drawing this artwork may sample it. */
    var artworkAllowed by mutableStateOf(false)
    private var requestedArtwork: String? = null
    val canSampleArtwork: Boolean get() = artworkAllowed && image != null

    fun beginArtwork(model: String?) {
        if (requestedArtwork == model) return
        requestedArtwork = model
        // Retain neither the previous tint nor its luminance during an asynchronous load.
        show(null, 0f)
    }

    fun acceptsArtwork(model: String): Boolean = requestedArtwork == model

    var image: ImageBitmap? by mutableStateOf(null)
        private set

    /** Mean relative luminance of [image], 0..1. Decides how much the glass is smoked for white text. */
    var luminance by mutableFloatStateOf(0f)
        private set

    fun show(
        image: ImageBitmap?,
        luminance: Float,
    ) {
        this.image = image
        this.luminance = luminance.coerceIn(0f, 1f)
    }
}

val LocalTvScene = staticCompositionLocalOf<TvScene?> { null }

/**
 * - [Panel]: a standing surface (the open rail, a menu, a dialog). Always drawn.
 * - [Focus]: the neutral translucent surface a focused control or row gains.
 */
enum class TvGlassKind { Panel, Focus }

/**
 * How much black is laid over the scene so that white text keeps its contrast. A dark scene needs
 * almost none; a bright one is smoked down to [target]. Focused glass is allowed to be brighter.
 */
internal fun glassVeilAlpha(
    sceneLuminance: Float,
    focused: Boolean,
): Float {
    val lifted = sceneLuminance.coerceIn(0f, 1f) * if (focused) FOCUS_BRIGHTNESS else REST_BRIGHTNESS
    val target = if (focused) FOCUS_TARGET_LUMINANCE else REST_TARGET_LUMINANCE
    if (lifted <= target) return MIN_VEIL
    return (1f - target / lifted).coerceIn(MIN_VEIL, MAX_VEIL)
}

/** Per-surface cache: everything here depends only on the surface's size and outline. */
internal class TvGlassCache {
    private var size = Size.Unspecified
    private var density = 0f
    private var outline: Outline? = null
    var path: Path? = null
        private set
    var sheen: Brush? = null
        private set
    var edge: Brush? = null
        private set
    var edgeStroke: Stroke? = null
        private set

    fun prepare(
        scope: DrawScope,
        outline: Outline,
        forSize: Size,
    ) {
        if (this.outline === outline && size == forSize && density == scope.density) return
        this.outline = outline
        size = forSize
        density = scope.density
        path = Path().apply { addOutline(outline) }
        val sheenEnd = min(forSize.height * SHEEN_SHARE, with(scope) { SHEEN_MAX.toPx() })
        sheen = Brush.verticalGradient(listOf(SHEEN_TOP, Color.Transparent), startY = 0f, endY = sheenEnd)
        // Lit from above: bright along the top, faint down the sides, catching light again at the bottom.
        edge =
            Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.48f),
                0.32f to Color.White.copy(alpha = 0.16f),
                0.75f to Color.White.copy(alpha = 0.06f),
                1f to Color.White.copy(alpha = 0.20f),
                startY = 0f,
                endY = forSize.height,
            )
        edgeStroke = Stroke(with(scope) { EDGE_WIDTH.toPx() })
    }
}

/**
 * Draws the glass in [outline], back to front: the scene behind it (screen-aligned, desaturated),
 * the legibility veil, a thin gloss under the top edge, and the specular rim.
 *
 * With no scene yet (nothing has played or been focused) it falls back to the drawn material: a
 * smoked base for a panel, the translucent focus fill for focus.
 *
 * @param origin this surface's top-left in root coordinates, [root] the root's size: together
 * they place the scene so that it stays still behind the glass.
 * @param alpha the whole material's opacity, for fading a focus surface in and out.
 */
internal fun DrawScope.drawSceneGlass(
    outline: Outline,
    cache: TvGlassCache,
    scene: TvScene?,
    origin: Offset,
    root: Size,
    kind: TvGlassKind,
    alpha: Float = 1f,
    forSize: Size = size,
) {
    if (alpha <= 0.001f) return
    cache.prepare(this, outline, forSize)
    val focused = kind == TvGlassKind.Focus
    val live = scene?.backdrop
    if (live != null) {
        val frame = live.frame
        val path = cache.path
        if (frame != null && path != null) {
            clipPath(path) {
                drawBackdropFrame(frame, origin, forSize, alpha)
            }
            drawOutline(outline, Color.Black, alpha = alpha * 0.12f)
        } else {
            drawOutline(outline, Color.White, alpha = alpha * 0.035f)
        }
        cache.sheen?.let { drawOutline(outline, it, alpha = alpha) }
        cache.edge?.let { edge -> cache.edgeStroke?.let { drawOutline(outline, edge, alpha = alpha * 0.65f, style = it) } }
        return
    }
    val image = scene?.image?.takeIf { scene.canSampleArtwork }
    val path = cache.path
    if (image != null && path != null && root.width >= 1f && root.height >= 1f) {
        if (!focused) drawOutline(outline, TvPalette.Background, alpha = alpha)
        clipPath(path) {
            drawImage(
                image = image,
                dstOffset = IntOffset(-origin.x.roundToInt(), -origin.y.roundToInt()),
                dstSize = IntSize(root.width.roundToInt(), root.height.roundToInt()),
                alpha = alpha * if (focused) FOCUS_SCENE_SHARE else PANEL_SCENE_SHARE,
                colorFilter = if (focused) FocusFilter else RestFilter,
                filterQuality = FilterQuality.Low,
            )
        }
        drawOutline(outline, Color.Black, alpha = alpha * glassVeilAlpha(scene.luminance, focused))
    } else if (focused) {
        drawOutline(outline, TvPalette.FocusFill, alpha = alpha)
    } else {
        drawOutline(outline, TvPalette.GlassBase, alpha = alpha)
        drawOutline(outline, TvPalette.GlassFill, alpha = alpha)
    }
    cache.sheen?.let { drawOutline(outline, it, alpha = alpha) }
    val edge = cache.edge
    val stroke = cache.edgeStroke
    if (edge != null && stroke != null) {
        drawOutline(outline, edge, alpha = alpha * if (focused) 1f else PANEL_EDGE_ALPHA, style = stroke)
    }
}

/**
 * A standing glass surface in [shape]: the open rail, menus, dialogs. It goes before the surface's
 * own `clip(...)`, because an [elevated] surface draws its shadow outside its bounds.
 */
fun Modifier.tvSceneGlass(
    shape: Shape = TvShapes.Panel,
    elevated: Boolean = false,
): Modifier = composed {
    val overlay = LocalTvBackdropOverlay.current
    val content = if (overlay != null) rememberGraphicsLayer() else null
    this.then(TvSceneGlassElement(shape, elevated, overlay, content))
}

private data class TvSceneGlassElement(
    val shape: Shape,
    val elevated: Boolean,
    val overlay: TvBackdrop?,
    val content: GraphicsLayer?,
) : ModifierNodeElement<TvSceneGlassNode>() {
    override fun create() = TvSceneGlassNode(shape, elevated, overlay, content)

    override fun update(node: TvSceneGlassNode) {
        node.shape = shape
        node.elevated = elevated
        node.updateOverlay(overlay, content)
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "tvSceneGlass"
    }
}

private class TvSceneGlassNode(
    var shape: Shape,
    var elevated: Boolean,
    private var overlay: TvBackdrop?,
    private var content: GraphicsLayer?,
) : Modifier.Node(),
    DrawModifierNode,
    GlobalPositionAwareModifierNode,
    CompositionLocalConsumerModifierNode {
    private val cache = TvGlassCache()
    private val placement = TvGlassPlacement()
    private var outline: Outline? = null
    private var outlineSize = Size.Unspecified
    private var outlineShape: Shape? = null
    private var shadow: Stroke? = null

    fun updateOverlay(value: TvBackdrop?, layer: GraphicsLayer?) {
        if (overlay !== value) {
            overlay?.overlays?.remove(this)
            overlay?.reads?.clear(this)
        }
        overlay = value
        content = layer
    }

    override fun onDetach() {
        overlay?.overlays?.remove(this)
        overlay?.reads?.clear(this)
        overlay?.overlayChanged()
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (placement.update(coordinates)) {
            overlay?.takeIf { it.ownsRoot(coordinates) }?.request()
            invalidateDraw()
        }
    }

    override fun ContentDrawScope.draw() {
        val resolved =
            outline?.takeIf { outlineSize == size && outlineShape === shape }
                ?: shape.createOutline(size, layoutDirection, this).also {
                    outline = it
                    outlineSize = size
                    outlineShape = shape
                }
        val target = overlay?.takeIf { it.ownsRoot(requireLayoutCoordinates()) }
        val layer = content
        if (target != null && layer != null) {
            layer.record { target.observeContent(this@TvSceneGlassNode) { this@draw.drawContent() } }
            drawLayer(layer)
            target.overlays[this@TvSceneGlassNode] = {
                if (isAttached) {
                    val coordinates = requireLayoutCoordinates()
                    if (target.ownsRoot(coordinates)) {
                    placement.update(coordinates)
                    val origin = coordinates.localToRoot(Offset.Zero) - target.origin
                    val corner = coordinates.localToRoot(Offset(layer.size.width.toFloat(), layer.size.height.toFloat())) - target.origin
                    val visible = coordinates.boundsInRoot().translate(-target.origin)
                    clipRect(visible.left, visible.top, visible.right, visible.bottom) {
                    withTransform({
                        translate(origin.x, origin.y)
                        scale((corner.x - origin.x) / layer.size.width, (corner.y - origin.y) / layer.size.height, pivot = Offset.Zero)
                    }) {
                        drawSceneGlass(resolved, cache, currentValueOf(LocalTvScene), placement.origin, placement.root, TvGlassKind.Panel,
                            forSize = Size(layer.size.width.toFloat(), layer.size.height.toFloat()))
                        drawLayer(layer)
                    }
                    }
                    }
                }
            }
            target.request()
            target.overlayChanged()
            return
        }
        if (overlay?.overlays?.remove(this) != null) overlay?.overlayChanged()
        overlay?.reads?.clear(this)
        if (elevated) {
            val ring = shadow ?: Stroke(TvDimens.GlassShadow.toPx() / PANEL_SHADOW_RINGS).also { shadow = it }
            drawSoftHalo(
                bounds = Rect(Offset.Zero, size),
                cornerPx = cornerRadiusOf(resolved, shape),
                color = Color.Black,
                alpha = PANEL_SHADOW_ALPHA,
                ring = ring,
                dropPx = PANEL_SHADOW_DROP.toPx(),
            )
        }
        drawSceneGlass(resolved, cache, if (overlay == null) currentValueOf(LocalTvScene) else null,
            placement.origin, placement.root, TvGlassKind.Panel)
        drawContent()
    }
}

/** Where a glass surface sits in the root, kept by the node that draws it. */
internal class TvGlassPlacement {
    var origin = Offset.Zero
        private set
    var root = Size.Zero
        private set

    /** True when the placement changed and the surface must be redrawn. */
    fun update(coordinates: LayoutCoordinates): Boolean {
        if (!coordinates.isAttached) return false
        val nextOrigin = coordinates.positionInRoot()
        val nextRoot = coordinates.findRootCoordinates().size.toSize()
        if (nextOrigin == origin && nextRoot == root) return false
        origin = nextOrigin
        root = nextRoot
        return true
    }
}

/** The corner a soft halo follows for [outline]; a squircle reports the radius it was built with. */
internal fun DrawScope.cornerRadiusOf(
    outline: Outline,
    shape: Shape?,
): Float =
    when (outline) {
        is Outline.Rounded -> outline.roundRect.topLeftCornerRadius.x
        is Outline.Rectangle -> 0f
        is Outline.Generic -> (shape as? TvSquircleShape)?.radius?.toPx() ?: 0f
    }

private fun glassFilter(
    saturation: Float,
    brightness: Float,
): ColorFilter {
    val matrix = ColorMatrix().apply { setToSaturation(saturation) }
    matrix.timesAssign(ColorMatrix().apply { setToScale(brightness, brightness, brightness, 1f) })
    return ColorFilter.colorMatrix(matrix)
}

// A subdued contribution follows the eligible background without pigmenting the focus surface.
private const val REST_BRIGHTNESS = 1.10f
private const val FOCUS_BRIGHTNESS = 1.26f
private val RestFilter = glassFilter(saturation = 0.45f, brightness = REST_BRIGHTNESS)
private val FocusFilter = glassFilter(saturation = 0.45f, brightness = FOCUS_BRIGHTNESS)

private const val PANEL_SCENE_SHARE = 0.18f
private const val FOCUS_SCENE_SHARE = 0.16f
private const val PANEL_EDGE_ALPHA = 0.78f

// White text needs 4.5:1; over a surface at 0.18 relative luminance it has about 4.6:1.
private const val REST_TARGET_LUMINANCE = 0.11f
private const val FOCUS_TARGET_LUMINANCE = 0.18f
private const val MIN_VEIL = 0.08f
private const val MAX_VEIL = 0.82f

private val SHEEN_TOP = Color.White.copy(alpha = 0.06f)
private const val SHEEN_SHARE = 0.22f
private val SHEEN_MAX = 40.dp
private val EDGE_WIDTH = 1.dp

private const val PANEL_SHADOW_RINGS = 12
private const val PANEL_SHADOW_ALPHA = 0.30f
private val PANEL_SHADOW_DROP = 6.dp
