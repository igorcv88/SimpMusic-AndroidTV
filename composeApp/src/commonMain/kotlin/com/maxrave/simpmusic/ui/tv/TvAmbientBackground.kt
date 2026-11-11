package com.maxrave.simpmusic.ui.tv

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision

/** Ambient artwork is decoded at a bounded resolution, blurred off-main and cached by Coil. */
sealed interface TvAmbientStyle {
    /** The artwork decoded at [decodePx] x [decodePx] and stretched to the screen, bilinear. */
    data class Artwork(
        val decodePx: Int,
    ) : TvAmbientStyle

    /** A vertical gradient between two tones of the artwork's tint. No image is decoded. */
    data object Gradient : TvAmbientStyle

    /**
     * Capture aid, not a shipping look: four equal vertical bands of the same full-screen picture
     * showing, left to right, the 16, 32 and 64 px decodes and the gradient, so one screenshot
     * compares every candidate on the same artwork.
     */
    data object Compare : TvAmbientStyle
}

/** Enough samples for smooth full-screen gradients after CPU blur, without full-size bitmap churn. */
internal const val AMBIENT_DECODE_PX = 256
internal val TV_AMBIENT_STYLE: TvAmbientStyle = TvAmbientStyle.Artwork(AMBIENT_DECODE_PX)

/** The image model for the ambient picture: null for a null or blank URL, otherwise the URL. */
fun ambientImageModel(url: String?): String? = url?.takeIf { it.isNotBlank() }

/**
 * Bounded artwork behind a dark scrim. On Android the Coil transformation performs a three-pass
 * CPU blur on Dispatchers.Default, including API 30. Coil caches the transformed image by URL,
 * resolution and blur version; AsyncImage cancels obsolete requests when a model leaves composition.
 * No image processing runs from a draw callback or animation frame.
 */
@Composable
fun TvAmbientBackground(
    artworkUrl: String?,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    style: TvAmbientStyle = TV_AMBIENT_STYLE,
) {
    val motion = LocalTvMotion.current
    val model = ambientImageModel(artworkUrl)
    Box(
        modifier
            .fillMaxSize()
            .background(TvPalette.Background)
            .drawWithContent {
                drawContent()
                drawRect(
                    Brush.verticalGradient(
                        AMBIENT_SCRIM_START to Color.Transparent,
                        1f to TvPalette.Scrim,
                    ),
                )
            },
    ) {
        when (style) {
            is TvAmbientStyle.Artwork -> {
                // Keyed on the model: the outgoing picture fades out while the incoming one fades in,
                // and a null model fades to the bare background.
                Crossfade(targetState = model, animationSpec = motion.fadeInSpec(), label = "tvAmbient") { current ->
                    if (current != null) AmbientPicture(current, style.decodePx, motion.fadeInMs, Modifier.fillMaxSize())
                }
            }

            TvAmbientStyle.Gradient -> {
                AmbientGradient(tint, Modifier.fillMaxSize())
            }

            TvAmbientStyle.Compare -> {
                if (model != null) {
                    AMBIENT_CANDIDATES.forEachIndexed { index, px ->
                        AmbientPicture(model, px, 0, Modifier.fillMaxSize().band(index, AMBIENT_CANDIDATES.size + 1))
                    }
                }
                AmbientGradient(
                    tint,
                    Modifier.fillMaxSize().band(AMBIENT_CANDIDATES.size, AMBIENT_CANDIDATES.size + 1),
                )
            }
        }
    }
}

@Composable
private fun AmbientPicture(
    model: String,
    decodePx: Int,
    fadeMs: Int,
    modifier: Modifier,
) {
    val context = LocalPlatformContext.current
    val request =
        remember(context, model, decodePx, fadeMs) {
            ImageRequest
                .Builder(context)
                .data(model)
                // EXACT: without it the decoder may stop at the nearest power-of-two sample (a 544 px
                // cover gives 34 or 68 px), and the candidates would not be the sizes they are named.
                .size(decodePx, decodePx)
                .precision(Precision.EXACT)
                .ambientBlur()
                .memoryCacheKey("tvAmbient:gaussian3:r18:v1:$decodePx:$model")
                .crossfade(fadeMs)
                .build()
        }
    val scene = LocalTvScene.current
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        // The same blurred bitmap becomes the body of every glass surface (TvSceneGlass).
        onSuccess = { state -> scene?.let { state.result.image.publishTo(it) } },
        filterQuality = FilterQuality.High,
        alpha = AMBIENT_ALPHA,
        modifier = modifier,
    )
}

@Composable
private fun AmbientGradient(
    tint: Color?,
    modifier: Modifier,
) {
    if (tint == null) return
    Box(
        modifier.drawBehind {
            drawRect(
                Brush.verticalGradient(
                    listOf(
                        lerp(TvPalette.Background, tint, GRADIENT_TOP),
                        lerp(TvPalette.Background, tint, GRADIENT_BOTTOM),
                    ),
                ),
            )
        },
    )
}

/** Shows only band [index] of [count] equal vertical bands of a full-size picture. */
private fun Modifier.band(
    index: Int,
    count: Int,
): Modifier =
    drawWithContent {
        val width = size.width / count
        clipRect(left = width * index, top = 0f, right = width * (index + 1), bottom = size.height) {
            this@drawWithContent.drawContent()
        }
        drawRect(Color.Black, topLeft = Offset(width * index, 0f), size = Size(1f, size.height), alpha = 0.6f)
    }

private val AMBIENT_CANDIDATES = listOf(16, 32, 64)
private const val AMBIENT_ALPHA = 0.55f
private const val AMBIENT_SCRIM_START = 0.4f
private const val GRADIENT_TOP = 0.45f
private const val GRADIENT_BOTTOM = 0.12f
