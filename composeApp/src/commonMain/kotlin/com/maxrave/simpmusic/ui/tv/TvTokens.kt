package com.maxrave.simpmusic.ui.tv

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Layout tokens for the TV design, from `docs/superpowers/specs/2026-11-10-tv-p2-visual-design.md`.
 * The canvas is 960 x 540 dp; every value here applies only while [LocalTvDevice] is true.
 */
object TvDimens {
    val SafeHorizontal = 48.dp
    val SafeVertical = 27.dp
    val Gutter = 20.dp
    val RowGap = 32.dp
    val HeaderGap = 12.dp

    val RailCollapsed = 72.dp
    val RailExpanded = 248.dp
    val RailItemHeight = 48.dp

    val SquareCard = 148.dp
    val WideCardWidth = 268.dp
    val WideCardHeight = 151.dp
    val ArtistCard = 148.dp

    val QuickPickWidth = 412.dp
    val QuickPickHeight = 56.dp
    val SidePanelWidth = 400.dp

    /** Home's immersive header (spec, "P2-A: identity"). */
    val ImmersiveHeaderHeight = 220.dp

    /** Radius of the `Card` focus glow and of the elevated glass shadow. */
    val FocusGlow = 24.dp
    val GlassShadow = 16.dp

    /** The selected destination's dot in the rail. */
    val RailIndicator = 6.dp

    /**
     * Home's cards keep a transparent inset around their artwork (half a [Gutter] on each side), so
     * two neighbours' artworks stand one [Gutter] apart and the 1.10 focus scale grows into the
     * inset rather than onto the next card.
     */
    val CardInset = 10.dp
}

object TvShapes {
    val Card: Shape = RoundedCornerShape(12.dp)
    val Small: Shape = RoundedCornerShape(8.dp)
    val Panel: Shape = RoundedCornerShape(24.dp)
    val Control: Shape = CircleShape
}
