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
    fun playlistClusterContainsFirstMiddleAndLastControlsWithCompleteRims() {
        val gutter = TvDimens.PlaylistActionGutter.value
        val height = TvDimens.PlaylistActionClusterHeight.value
        val width = 3 * 48f + 2 * gutter
        val outset = 48f * (focusScaleFor(TvFocusStyle.Backing) - 1f) / 2f + 0.5f // 1 dp rim
        for (index in 0..2) {
            val left = gutter + index * 48f
            assertTrue(left - outset >= 0f, "first control rim")
            assertTrue(left + 48f + outset <= width, "last control rim")
            assertTrue(gutter - outset >= 0f, "top rim")
            assertTrue(gutter + 48f + outset <= height, "bottom rim")
        }
    }

    @Test
    fun wideRowsDoNotExpandIntoTheRail() {
        assertTrue(focusScaleFor(TvFocusStyle.Row) == 1f, "page-wide rows must keep their rim inside their slot")
        assertTrue(focusScaleFor(TvFocusStyle.Control) <= 1.02f)
        assertTrue(focusScaleFor(TvFocusStyle.Backing) <= 1.02f)
    }
}
