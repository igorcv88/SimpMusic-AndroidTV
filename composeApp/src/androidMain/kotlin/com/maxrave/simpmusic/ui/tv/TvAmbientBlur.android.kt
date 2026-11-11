package com.maxrave.simpmusic.ui.tv

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil3.BitmapImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.transformations
import coil3.size.Size
import coil3.transform.Transformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal actual fun ImageRequest.Builder.ambientBlur(): ImageRequest.Builder =
    allowHardware(false).transformations(AmbientBlurTransformation)

private object AmbientBlurTransformation : Transformation() {
    override val cacheKey = "tvAmbient:gaussian3:r18:v1"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap = withContext(Dispatchers.Default) {
        ensureActive()
        val pixels = IntArray(input.width * input.height)
        input.getPixels(pixels, 0, input.width, 0, 0, input.width, input.height)
        val blurred = blurAmbientPixels(pixels, input.width, input.height, 18)
        ensureActive()
        Bitmap.createBitmap(blurred, input.width, input.height, Bitmap.Config.ARGB_8888)
    }
}

internal actual fun coil3.Image.publishTo(scene: TvScene) {
    val bitmap = (this as? BitmapImage)?.bitmap ?: return
    // The transformation leaves a software ARGB_8888 bitmap; anything else is not readable here.
    if (bitmap.isRecycled || bitmap.config != Bitmap.Config.ARGB_8888) return
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    // 256 x 256 sampled every 16th pixel: about four thousand reads, once per artwork.
    scene.show(bitmap.asImageBitmap(), meanLuminance(pixels, step = 16))
}
