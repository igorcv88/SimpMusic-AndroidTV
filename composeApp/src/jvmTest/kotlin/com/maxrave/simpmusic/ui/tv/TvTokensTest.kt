package com.maxrave.simpmusic.ui.tv

import kotlin.test.Test
import kotlin.test.assertTrue

class TvTokensTest {
    @Test
    fun cardFocusScaleFitsGutter() {
        // growth on each side of a 148dp card at 1.10 must not exceed half the 20dp gap
        assertTrue(TvDimens.SquareCard.value * (1.10f - 1f) / 2f <= TvDimens.Gutter.value / 2f)
    }

    @Test
    fun cardInsetIsHalfTheGutter() {
        // Two neighbouring cards' insets add up to one gutter between their artworks.
        assertTrue(TvDimens.CardInset.value * 2f == TvDimens.Gutter.value)
    }
}
