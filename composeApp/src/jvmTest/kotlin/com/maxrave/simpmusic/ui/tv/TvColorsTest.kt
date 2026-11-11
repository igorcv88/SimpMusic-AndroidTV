package com.maxrave.simpmusic.ui.tv

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TvColorsTest {
    @Test
    fun secondaryTextMeetsAa() = assertTrue(contrastRatio(TvPalette.TextSecondary, TvPalette.Background) >= 4.5)

    @Test
    fun primaryTextMeetsAaa() = assertTrue(contrastRatio(TvPalette.TextPrimary, TvPalette.Background) >= 7.0)

    @Test
    fun focusContentMeetsAaa() = assertTrue(contrastRatio(TvPalette.OnFocus, TvPalette.FocusFill.compositeOver(TvPalette.Background)) >= 7.0)

    @Test
    fun whiteOnBlackIs21() = assertEquals(21.0, contrastRatio(Color.White, Color.Black), 0.01)
}
