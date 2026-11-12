package com.maxrave.simpmusic.ui.tv

import kotlin.test.Test
import kotlin.test.assertTrue

class TvFocusTest {
    @Test
    fun focusedArtworkStaysInsideItsGutter() {
        for (width in listOf(TvDimens.SquareCard, TvDimens.WideCardWidth)) {
            val growthPerSide = width.value * (focusScaleFor(TvFocusStyle.Card) - 1f) / 2f
            assertTrue(growthPerSide + TvDimens.FocusGlow.value <= TvDimens.CardInset.value, "scale plus the complete halo must fit the reserved gutter")
        }
    }

    @Test
    fun wideRowsDoNotExpandIntoTheRail() {
        assertTrue(focusScaleFor(TvFocusStyle.Row) == 1f, "page-wide rows must keep their rim inside their slot")
        assertTrue(focusScaleFor(TvFocusStyle.Control) <= 1.02f)
        assertTrue(focusScaleFor(TvFocusStyle.Backing) <= 1.02f)
    }
}
