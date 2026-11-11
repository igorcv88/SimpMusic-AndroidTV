package com.maxrave.simpmusic.ui.tv

import kotlin.test.Test
import kotlin.test.assertEquals

class TvFocusTest {
    @Test
    fun cardScaleIs110() = assertEquals(1.10f, focusScaleFor(TvFocusStyle.Card))

    @Test
    fun otherScalesFollowTheSpec() {
        assertEquals(1.05f, focusScaleFor(TvFocusStyle.Control))
        assertEquals(1.02f, focusScaleFor(TvFocusStyle.Row))
        assertEquals(1.06f, focusScaleFor(TvFocusStyle.Backing))
    }
}
