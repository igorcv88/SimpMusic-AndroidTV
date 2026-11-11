package com.maxrave.simpmusic.ui.tv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TvAmbientBackgroundTest {
    @Test
    fun ambientModelIsNullForBlankUrl() {
        assertNull(ambientImageModel(""))
        assertNull(ambientImageModel("  "))
        assertNull(ambientImageModel(null))
    }

    @Test
    fun ambientModelPassesUrl() = assertEquals("https://example.com/a.jpg", ambientImageModel("https://example.com/a.jpg"))
}
