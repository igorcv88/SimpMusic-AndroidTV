package com.maxrave.simpmusic.ui.tv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvMotionTest {
    @Test
    fun zeroAnimatorScaleSelectsReducedMotion() =
        resolveTvMotion(0f).let {
            assertTrue(it.reduced)
            assertEquals(0, it.fadeInMs)
            assertEquals(0, it.fadeOutMs)
        }

    @Test
    fun normalScaleUsesSprings() = assertFalse(resolveTvMotion(1f).reduced)
}
