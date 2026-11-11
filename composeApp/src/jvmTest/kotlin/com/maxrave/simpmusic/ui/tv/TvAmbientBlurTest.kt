package com.maxrave.simpmusic.ui.tv

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TvAmbientBlurTest {
    @Test
    fun uniformArtworkHasNoDarkEdges() {
        val pixels = IntArray(35) { 0xFF4589CD.toInt() }
        assertContentEquals(pixels, blurAmbientPixels(pixels, 7, 5, 18))
    }

    @Test
    fun highContrastArtworkIsActuallySmoothedWithoutMutatingSource() {
        val source = IntArray(81) { 0xFF000000.toInt() }.apply { this[40] = 0xFFFFFFFF.toInt() }
        val original = source.copyOf()
        val blurred = blurAmbientPixels(source, 9, 9, 1)
        assertContentEquals(original, source)
        assertTrue((blurred[40] and 255) in 1..254)
        assertTrue((blurred[39] and 255) > 0)
        assertEquals(blurred[39], blurred[41])
    }

    @Test
    fun onePixelAndZeroRadiusAreSafe() {
        assertContentEquals(intArrayOf(-1), blurAmbientPixels(intArrayOf(-1), 1, 1, 18))
        val pixels = intArrayOf(0xFF112233.toInt(), -1)
        assertContentEquals(pixels, blurAmbientPixels(pixels, 2, 1, 0))
        assertFailsWith<IllegalArgumentException> { blurAmbientPixels(pixels, 3, 1, 1) }
    }
}
