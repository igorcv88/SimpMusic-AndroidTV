package com.maxrave.simpmusic.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.maxrave.simpmusic.expect.ui.PlatformBackdrop
import com.maxrave.simpmusic.ui.component.liquidGlass
import com.maxrave.simpmusic.ui.theme.LocalLiquidGlassEnabled

/**
 * What the device can do to the picture behind a glass surface. The three are not treated as
 * visually equivalent (spec, "Device facts"): each gets its own screenshot before it is called done.
 */
enum class TvGlassTier {
    /** API 30: no RenderEffect. The material is drawn and the backdrop is never sampled. */
    Drawn,

    /** API 31-32: RenderEffect blur, but no RuntimeShader, so no lens or refraction. */
    Blur,

    /** API 33+: the app's Liquid Glass. */
    Liquid,
}

expect fun tvGlassTier(): TvGlassTier

/**
 * TV glass drawn without a backdrop: a dense base tone, the 8% white fill, a 1 dp rim that runs from
 * white 22% at the top to nothing at the bottom and, when [elevated], a 16 dp shadow at 30%.
 *
 * It samples nothing, records nothing and holds no layer, so it costs four draw calls plus the
 * shadow rings and looks the same on every API level. It is the whole material on
 * [TvGlassTier.Drawn], and the fallback on the other tiers when Liquid Glass is switched off or the
 * caller owns no backdrop.
 *
 * It draws its shadow outside its own bounds, so it goes before the surface's `clip(...)`.
 */
fun Modifier.tvGlass(
    shape: Shape = TvShapes.Panel,
    elevated: Boolean = true,
): Modifier = tvSceneGlass(shape, elevated)

/**
 * TV glass over a recorded [backdrop], by what the device can do:
 * - [TvGlassTier.Drawn], Liquid Glass switched off, or no [backdrop]: [tvGlass] above.
 * - [TvGlassTier.Blur]: the backdrop blurred once through Kyant's plain path (no lens, no highlight
 *   shader), under the same fill and rim as the drawn tier.
 * - [TvGlassTier.Liquid]: the app's existing [liquidGlass], static (no press interaction — a remote
 *   has no pointer).
 *
 * The glass engine is not duplicated here: both blurred tiers go through the Kyant backdrop the rest
 * of the app uses. The caller must be a SIBLING of the backdrop's source, never inside it; nesting
 * is the render-feedback loop documented on [liquidGlass].
 */
@Composable
fun Modifier.tvGlass(
    backdrop: PlatformBackdrop?,
    shape: Shape = TvShapes.Panel,
    elevated: Boolean = true,
): Modifier {
    val tier = tvGlassTier()
    if (backdrop == null || tier == TvGlassTier.Drawn || !LocalLiquidGlassEnabled.current) {
        return tvGlass(shape, elevated)
    }
    return when (tier) {
        TvGlassTier.Liquid -> {
            liquidGlass(backdrop = backdrop, shape = shape, interactive = false)
        }

        else -> {
            drawPlainBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = { blur(GLASS_BLUR.toPx()) },
                onDrawSurface = {
                    val outline = shape.createOutline(size, layoutDirection, this)
                    drawOutline(outline, TvPalette.Scrim, alpha = GLASS_BLUR_TONE)
                    drawOutline(outline, TvPalette.GlassFill)
                },
            )
        }
    }
}

private fun DrawScope.drawGlassSurface(
    outline: Outline,
    rimBrush: Brush,
    rim: Stroke,
) {
    drawOutline(outline, TvPalette.GlassFill)
    drawOutline(outline, rimBrush, style = rim)
}

private const val GLASS_SHADOW_RINGS = 12
private const val GLASS_SHADOW_ALPHA = 0.30f
private const val GLASS_BLUR_TONE = 0.45f
private val GLASS_SHADOW_DROP = 6.dp
private val GLASS_BLUR = 24.dp
