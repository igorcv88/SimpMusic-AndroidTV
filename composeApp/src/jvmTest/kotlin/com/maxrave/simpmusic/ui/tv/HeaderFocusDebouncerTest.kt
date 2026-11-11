package com.maxrave.simpmusic.ui.tv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HeaderFocusDebouncerTest {
    @Test
    fun headerDebounceIgnoresTransientFocus() {
        val d = HeaderFocusDebouncer(300)
        d.onFocus("a", 0)
        d.onFocus("b", 100)
        d.onFocus("c", 200)
        assertNull(d.settled(250))
        assertEquals("c", d.settled(500))
    }

    @Test
    fun firstFocusSettlesAfterDelay() {
        val d = HeaderFocusDebouncer(300)
        d.onFocus("a", 0)
        assertEquals("a", d.settled(300))
    }

    @Test
    fun refocusingThePendingItemDoesNotRestartItsWait() {
        val d = HeaderFocusDebouncer(300)
        d.onFocus("a", 0)
        d.onFocus("a", 250)
        assertEquals("a", d.settled(300))
    }
}
