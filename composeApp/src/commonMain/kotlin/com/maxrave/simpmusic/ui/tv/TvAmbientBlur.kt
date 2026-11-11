package com.maxrave.simpmusic.ui.tv

import coil3.request.ImageRequest

/** Android supplies a CPU transformation; non-TV platforms keep their existing image pipeline. */
internal expect fun ImageRequest.Builder.ambientBlur(): ImageRequest.Builder

/** Three separable box passes approximate a Gaussian, with edge clamping to avoid dark seams. */
internal fun blurAmbientPixels(pixels: IntArray, width: Int, height: Int, radius: Int): IntArray {
    require(width > 0 && height > 0 && pixels.size == width * height)
    require(radius >= 0)
    if (radius == 0) return pixels.copyOf()
    val result = pixels.copyOf()
    val scratch = IntArray(result.size)
    fun pass(input: IntArray, output: IntArray, horizontal: Boolean) {
        val lines = if (horizontal) height else width
        val length = if (horizontal) width else height
        val divisor = radius * 2 + 1
        fun index(line: Int, position: Int) = if (horizontal) line * width + position else position * width + line
        for (line in 0 until lines) {
            var red = 0
            var green = 0
            var blue = 0
            for (p in -radius..radius) {
                val pixel = input[index(line, p.coerceIn(0, length - 1))]
                red += (pixel ushr 16) and 255
                green += (pixel ushr 8) and 255
                blue += pixel and 255
            }
            for (p in 0 until length) {
                output[index(line, p)] = (0xFF shl 24) or ((red / divisor) shl 16) or ((green / divisor) shl 8) or (blue / divisor)
                val add = input[index(line, (p + radius + 1).coerceAtMost(length - 1))]
                val remove = input[index(line, (p - radius).coerceAtLeast(0))]
                red += ((add ushr 16) and 255) - ((remove ushr 16) and 255)
                green += ((add ushr 8) and 255) - ((remove ushr 8) and 255)
                blue += (add and 255) - (remove and 255)
            }
        }
    }
    repeat(3) {
        pass(result, scratch, true)
        pass(scratch, result, false)
    }
    return result
}

/** Hands a decoded ambient picture to [scene]. Platforms without a bitmap view of it publish nothing. */
internal expect fun coil3.Image.publishTo(scene: TvScene)

/** Mean relative luminance of ARGB [pixels], 0..1, sampled every [step] pixels. */
internal fun meanLuminance(
    pixels: IntArray,
    step: Int = 1,
): Float {
    if (pixels.isEmpty()) return 0f
    var sum = 0.0
    var count = 0
    var i = 0
    while (i < pixels.size) {
        val p = pixels[i]
        sum += 0.2126 * ((p ushr 16) and 255) + 0.7152 * ((p ushr 8) and 255) + 0.0722 * (p and 255)
        count++
        i += step.coerceAtLeast(1)
    }
    return (sum / count / 255.0).toFloat()
}
