package com.maxrave.simpmusic.ui.tv

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

object TvPlaybackRequests {
    private val requests = Channel<Unit>(Channel.CONFLATED)
    val events = requests.receiveAsFlow()
    fun userStartedPlayback() { requests.trySend(Unit) }
}
