package com.maxrave.simpmusic.ui.tv

import kotlin.test.Test
import kotlin.test.assertTrue

class TvFocusTest {
    @Test
    fun focusedArtworkStaysInsideItsGutter() {
        for (width in listOf(TvDimens.SquareCard, TvDimens.WideCardWidth)) {
            val growthPerSide = width.value * (focusScaleFor(TvFocusStyle.Card) - 1f) / 2f
            assertTrue(growthPerSide <= TvDimens.CardInset.value)
        }
    }

    @Test
    fun wideRowsDoNotExpandIntoTheRail() {
        assertTrue(800f * (focusScaleFor(TvFocusStyle.Row) - 1f) / 2f < 4f)
        assertTrue(focusScaleFor(TvFocusStyle.Control) <= 1.02f)
        assertTrue(focusScaleFor(TvFocusStyle.Backing) <= 1.02f)
    }
}
