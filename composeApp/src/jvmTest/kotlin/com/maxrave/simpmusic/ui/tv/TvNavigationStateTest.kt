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
    @Test fun firstVisitEntersOnlyWhenContentIsReadyAndNothingElseOwnsFocus() {
        assertTrue(shouldEnterFresh(alreadyEntered = false, active = true, railHasFocus = false, hasSavedFocus = false, contentReady = true))
        assertFalse(shouldEnterFresh(alreadyEntered = false, active = true, railHasFocus = false, hasSavedFocus = false, contentReady = false))
    }
    @Test fun firstVisitEntryNeverCompetesWithRailRestorationOrARepeatVisit() {
        assertFalse(shouldEnterFresh(alreadyEntered = false, active = true, railHasFocus = true, hasSavedFocus = false, contentReady = true))
        assertFalse(shouldEnterFresh(alreadyEntered = false, active = true, railHasFocus = false, hasSavedFocus = true, contentReady = true))
        assertFalse(shouldEnterFresh(alreadyEntered = true, active = true, railHasFocus = false, hasSavedFocus = false, contentReady = true))
        assertFalse(shouldEnterFresh(alreadyEntered = false, active = false, railHasFocus = false, hasSavedFocus = false, contentReady = true))
    }
    @Test fun entryWithoutARegisteredTargetDoesNothing() {
        val navigation = TvNavigationState()
        navigation.resume("home-entry")
        navigation.contentReady("home-entry", true)
        assertFalse(navigation.enterIfFresh("home-entry"))
    }
}
