package com.maxrave.simpmusic.ui.tv

import kotlin.test.*

class TvNavigationStateTest {
    @Test fun initialEntryLeavesExplicitScreenEntryInControl() {
        assertNull(resolveTvFocusKey(null, linkedSetOf("profile", "play")))
    }
    @Test fun reorderedReadyItemsKeepTheirIdentity() {
        assertEquals("track-2", resolveTvFocusKey("track-2", linkedSetOf("track-3", "track-2")))
    }
    @Test fun removedItemHasDeterministicReadyFallback() {
        assertEquals("play", resolveTvFocusKey("removed", linkedSetOf("play", "track-1")))
        assertNull(resolveTvFocusKey("removed", emptySet()))
    }
    @Test fun focusHistoryIsScopedByBackStackEntry() {
        val navigation = TvNavigationState()
        navigation.focused("home-entry", "card-1")
        assertTrue(navigation.hasSavedFocus("home-entry"))
        assertFalse(navigation.hasSavedFocus("search-entry"))
        assertFalse(navigation.hasSavedFocus(null))
    }
}
