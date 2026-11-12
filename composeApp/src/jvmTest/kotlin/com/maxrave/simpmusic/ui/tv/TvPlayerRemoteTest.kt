package com.maxrave.simpmusic.ui.tv

import kotlin.test.*

class TvPlayerRemoteTest {
    @Test fun hiddenControlsSwallowEveryNavigationOrActivationKey() {
        for (key in listOf(TvPlayerKey.Left, TvPlayerKey.Right, TvPlayerKey.Up, TvPlayerKey.Down, TvPlayerKey.Activate)) {
            assertEquals(TvPlayerAction.Reveal, tvPlayerAction(false, key))
            assertEquals(TvPlayerAction.Navigate, tvPlayerAction(true, key))
        }
    }
    @Test fun lyricsDialogOwnsBackAndRemoteKeysWithoutWakingOrHidingTheFooter() {
        for (visible in listOf(false, true)) {
            for (key in TvPlayerKey.entries) {
                assertEquals(TvPlayerAction.DeferToDialog, tvPlayerAction(visible, key, modalOpen = true))
            }
        }
        assertEquals(TvPlayerAction.Hide, tvPlayerAction(true, TvPlayerKey.Back, modalOpen = false))
    }
    @Test fun backHidesBeforeLeavingAndNeverChangesPlayback() {
        assertEquals(TvPlayerAction.Hide, tvPlayerAction(true, TvPlayerKey.Back))
        assertEquals(TvPlayerAction.Leave, tvPlayerAction(false, TvPlayerKey.Back))
        assertEquals(TvPlayerAction.Navigate, tvPlayerAction(false, TvPlayerKey.Other))
    }
}
