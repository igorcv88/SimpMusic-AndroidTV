package com.maxrave.simpmusic.ui.tv

import kotlin.test.Test
import kotlin.test.assertEquals

class TvScrollTest {
    @Test
    fun itemRightOfKeylineScrollsToKeyline() =
        assertEquals(300f, tvKeylineScrollDelta(offset = 348f, size = 148f, containerSize = 840f, keylinePx = 48f))

    @Test
    fun tvKeylineScrollDeltaIsIdempotentAtKeyline() = assertEquals(0f, tvKeylineScrollDelta(48f, 148f, 840f, 48f))

    @Test
    fun itemLeftOfKeylineScrollsBack() = assertEquals(-48f, tvKeylineScrollDelta(0f, 148f, 840f, 48f))
}
