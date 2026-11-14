package com.maxrave.simpmusic.ui.tv

import kotlin.test.*

class TvNavigationStateTest {
    @Test fun returningToAnotherDestinationClosesRailWhenItsContentIsReady() {
        val requested = mutableListOf<androidx.compose.ui.focus.FocusRequester>()
        val navigation = TvNavigationState { requested += it; true }
        val homeCard = androidx.compose.ui.focus.FocusRequester()
        navigation.prepare("home", false)
        navigation.registerEntry("home", homeCard)
        navigation.resume("search")
        navigation.railHasFocus = true

        // Back in the rail returns to Home: the old rail must not cover the resumed page.
        navigation.resume("home")
        navigation.restoreReady("home")
        assertTrue(requested.isEmpty())
        navigation.contentReady("home", true)
        assertEquals(listOf(homeCard), requested)
        navigation.restoreReady("home")
        assertEquals(1, requested.size)
    }

    @Test fun railSelectionWaitsForDestinationAndThenMovesFocusExactlyOnce() {
        val requested = mutableListOf<androidx.compose.ui.focus.FocusRequester>()
        val navigation = TvNavigationState { requested += it; true }
        val settings = androidx.compose.ui.focus.FocusRequester()
        navigation.resume("home")
        navigation.railHasFocus = true
        navigation.prepare("settings", false)
        navigation.registerEntry("settings", settings)
        navigation.requestContentFocus("settings")
        assertTrue(requested.isEmpty())
        navigation.resume("settings")
        assertTrue(requested.isEmpty())
        navigation.contentReady("settings", true)
        assertEquals(listOf(settings), requested)
        navigation.restoreReady("settings")
        assertEquals(1, requested.size)
    }
    @Test fun returningFromPlayerRestoresOnlyItsOriginEntry() {
        val requested = mutableListOf<androidx.compose.ui.focus.FocusRequester>()
        val navigation = TvNavigationState { requested += it; true }
        val playlistTrack = androidx.compose.ui.focus.FocusRequester()
        val homeCard = androidx.compose.ui.focus.FocusRequester()
        navigation.prepare("playlist", true)
        navigation.prepare("home", true)
        navigation.resume("playlist")
        navigation.attach("playlist", "track", playlistTrack)
        navigation.focused("playlist", "track")
        navigation.focused("home", "card")
        navigation.resume("fullscreen")
        navigation.attach("home", "card", homeCard)
        assertTrue(requested.isEmpty())
        navigation.resume("playlist")
        navigation.restoreReady("playlist")
        assertEquals(listOf(playlistTrack), requested)
    }
    @Test fun restoringContentReportsSuccessAndDoesNotTriggerFallback() {
        val requested = mutableListOf<androidx.compose.ui.focus.FocusRequester>()
        val navigation = TvNavigationState { requested += it; true }
        val avatar = androidx.compose.ui.focus.FocusRequester()
        navigation.prepare("home", true)
        navigation.resume("home")
        navigation.attach("home", "avatar", avatar)
        navigation.focused("home", "avatar")
        assertTrue(navigation.restoreContent())
        assertEquals(listOf(avatar), requested)
    }
    @Test fun explicitPlaylistPlayEntryWinsOverGroupFallbackEvenIfGroupRegistersLast() {
        val requested = mutableListOf<androidx.compose.ui.focus.FocusRequester>()
        val navigation = TvNavigationState { requested += it; true }
        val play = androidx.compose.ui.focus.FocusRequester()
        val group = androidx.compose.ui.focus.FocusRequester()
        navigation.registerEntry("playlist", play)
        navigation.registerEntry("playlist", group, fallback = true)
        navigation.prepare("playlist", true)
        navigation.resume("playlist")
        assertTrue(navigation.enterIfFresh("playlist"))
        assertEquals(listOf(play), requested)
        navigation.unregisterEntry("playlist", group, fallback = true)
    }
    @Test fun incidentalLayoutNeverStealsRailFocusButRightExplicitlyRestoresAvatar() {
        val requested = mutableListOf<androidx.compose.ui.focus.FocusRequester>()
        val navigation = TvNavigationState { requested += it; true }
        val avatar = androidx.compose.ui.focus.FocusRequester()
        navigation.prepare("home", true)
        navigation.resume("home")
        navigation.focused("home", "avatar")
        navigation.railHasFocus = true
        navigation.attach("home", "avatar", avatar)
        assertTrue(requested.isEmpty())
        assertTrue(navigation.restoreContent())
        assertEquals(listOf(avatar), requested)
    }
    @Test fun failedFocusRequestRemainsPendingUntilTargetCanTakeFocus() {
        var ready = false
        val navigation = TvNavigationState { ready }
        val target = androidx.compose.ui.focus.FocusRequester()
        navigation.prepare("playlist", true)
        navigation.resume("playlist")
        navigation.focused("playlist", "track")
        navigation.attach("playlist", "track", target)
        assertFalse(navigation.restoreReady("playlist"))
        ready = true
        assertTrue(navigation.restoreReady("playlist"))
        assertFalse(navigation.restoreReady("playlist"))
    }
    @Test fun fallbackGroupNeverTakesInitialFocusFromDetailPagesPlayController() {
        var requests = 0
        val navigation = TvNavigationState { requests++; true }
        navigation.registerEntry("playlist", androidx.compose.ui.focus.FocusRequester(), fallback = true)
        navigation.prepare("playlist", true)
        navigation.resume("playlist")
        assertFalse(navigation.enterIfFresh("playlist"))
        assertEquals(0, requests)
    }
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
