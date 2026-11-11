package com.maxrave.simpmusic.ui.tv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TvSceneGlassTest {
    /** White text on a surface of relative luminance [surface]. */
    private fun whiteContrast(surface: Float) = 1.05f / (surface + 0.05f)

    private fun shownLuminance(
        scene: Float,
        focused: Boolean,
    ): Float {
        val brightness = if (focused) 1.26f else 1.10f
        return (scene * brightness).coerceAtMost(1f) * (1f - glassVeilAlpha(scene, focused))
    }

    @Test
    fun `white text keeps AA contrast on focused glass over any scene`() {
        for (step in 0..100) {
            val scene = step / 100f
            assertTrue(whiteContrast(shownLuminance(scene, focused = true)) >= 4.5f, "scene luminance $scene")
        }
    }

    @Test
    fun `white text keeps AA contrast on a resting panel over any scene`() {
        for (step in 0..100) {
            val scene = step / 100f
            assertTrue(whiteContrast(shownLuminance(scene, focused = false)) >= 4.5f, "scene luminance $scene")
        }
    }

    @Test
    fun `a dark scene is left almost clear`() {
        assertTrue(glassVeilAlpha(0.05f, focused = true) <= 0.1f)
        assertTrue(glassVeilAlpha(0.05f, focused = false) <= 0.1f)
    }

    @Test
    fun `focused glass is never darker than a resting panel over the same scene`() {
        for (step in 0..100) {
            val scene = step / 100f
            assertTrue(shownLuminance(scene, focused = true) >= shownLuminance(scene, focused = false) - 1e-4f)
        }
    }

    @Test
    fun `mean luminance of flat colours`() {
        assertEquals(0f, meanLuminance(IntArray(16) { 0xFF000000.toInt() }), 1e-4f)
        assertEquals(1f, meanLuminance(IntArray(16) { 0xFFFFFFFF.toInt() }), 1e-4f)
        assertEquals(0.7152f, meanLuminance(IntArray(16) { 0xFF00FF00.toInt() }, step = 4), 1e-3f)
        assertEquals(0f, meanLuminance(IntArray(0)), 1e-4f)
    }
}
