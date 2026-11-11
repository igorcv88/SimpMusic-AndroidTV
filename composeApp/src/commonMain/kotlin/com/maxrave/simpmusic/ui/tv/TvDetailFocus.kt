package com.maxrave.simpmusic.ui.tv

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Readiness is driven by layout, not a timer. One DOWN skips header metadata and reaches songs. */
@Composable
fun rememberTvDetailFocus(list: LazyListState, firstSongIndex: Int = 1): TvDetailFocus {
    val tv = LocalTvDevice.current
    val navigation = LocalTvNavigation.current
    val destination = LocalTvDestination.current
    val focus = remember { TvDetailFocus() }
    val scope = rememberCoroutineScope()
    var entered by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(tv, focus.playPlaced) {
        if (tv && focus.playPlaced && !entered && navigation?.hasSavedFocus(destination) != true) {
            entered = focus.play.requestFocus()
        }
    }
    focus.moveToSongs = {
        focus.moveJob?.cancel()
        focus.moveJob = scope.launch {
            list.scrollToItem(firstSongIndex)
            snapshotFlow { focus.firstSongPlaced }.first { it }
            focus.firstSong.requestFocus()
        }
    }
    return focus
}

@Stable
class TvDetailFocus internal constructor() {
    internal val play = FocusRequester()
    internal val firstSong = FocusRequester()
    internal var playPlaced by mutableStateOf(false)
    internal var firstSongPlaced by mutableStateOf(false)
    internal var moveToSongs: () -> Unit = {}
    internal var moveJob: Job? = null

    @Composable
    fun playModifier(hasSongs: Boolean): Modifier = if (LocalTvDevice.current) {
        Modifier.focusRequester(play)
            .onGloballyPositioned { playPlaced = true }
            .onPreviewKeyEvent {
                if (hasSongs && it.key == Key.DirectionDown) {
                    if (it.type == KeyEventType.KeyDown) moveToSongs()
                    true
                } else false
            }
            .tvFocus(TvFocusStyle.Control)
    } else Modifier

    @Composable
    fun songModifier(first: Boolean): Modifier = if (LocalTvDevice.current && first) {
        Modifier.composed {
            DisposableEffect(Unit) { onDispose { firstSongPlaced = false } }
            Modifier.focusRequester(firstSong).onGloballyPositioned { firstSongPlaced = true }
        }
    } else Modifier
}
