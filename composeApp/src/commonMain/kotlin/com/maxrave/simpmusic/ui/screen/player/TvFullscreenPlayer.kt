package com.maxrave.simpmusic.ui.screen.player

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.maxrave.common.Config.MAIN_PLAYER
import com.maxrave.simpmusic.expect.ui.MediaPlayerViewWithSubtitle
import com.maxrave.simpmusic.expect.ui.PlatformBackHandler
import com.maxrave.simpmusic.extension.elapsedLabel
import com.maxrave.simpmusic.extension.lengthLabel
import com.maxrave.simpmusic.ui.component.AddLyricsSheet
import com.maxrave.simpmusic.ui.component.QueueBottomSheet
import com.maxrave.simpmusic.ui.component.NowPlayingBottomSheet
import com.maxrave.simpmusic.ui.icon.*
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.ui.tv.*
import com.maxrave.simpmusic.viewModel.SharedViewModel
import com.maxrave.simpmusic.viewModel.UIEvent
import com.maxrave.simpmusic.viewModel.TvRenditionSwitchPhase
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.tv_switch_music_video
import simpmusic.composeapp.generated.resources.tv_switch_song
import simpmusic.composeapp.generated.resources.tv_rendition_pending
import simpmusic.composeapp.generated.resources.tv_rendition_unavailable
import simpmusic.composeapp.generated.resources.tv_rendition_unsupported
import simpmusic.composeapp.generated.resources.tv_rendition_error

/** TV has a single hidden key surface and a footer of visible targets, never touch seek halves. */
@Composable
internal fun TvFullscreenPlayer(navController: NavController, sharedViewModel: SharedViewModel) {
    val nowPlaying by sharedViewModel.nowPlayingScreenData.collectAsStateWithLifecycle()
    val controller by sharedViewModel.controllerState.collectAsStateWithLifecycle()
    val timeline by sharedViewModel.timeline.collectAsStateWithLifecycle()
    val rendition by sharedViewModel.tvRenditionSwitch.collectAsStateWithLifecycle()
    val playingState by sharedViewModel.nowPlayingState.collectAsStateWithLifecycle()
    val currentId = playingState?.mediaItem?.mediaId
    val switching = rendition.phase == TvRenditionSwitchPhase.Pending && rendition.mediaId == currentId
    LaunchedEffect(currentId) { sharedViewModel.cancelTvRenditionIfTrackChanged(currentId) }
    var visible by rememberSaveable { mutableStateOf(true) }
    var subtitles by rememberSaveable { mutableStateOf(true) }
    var queue by rememberSaveable { mutableStateOf(false) }
    var more by rememberSaveable { mutableStateOf(false) }
    // Snapshot the song being edited so a later track change cannot redirect its save.
    var addLyricsFor by rememberSaveable { mutableStateOf<List<String>?>(null) }
    val modalOpen = queue || more || addLyricsFor != null
    var inputTick by remember { mutableIntStateOf(0) }
    var swallowedRelease by remember { mutableStateOf<Key?>(null) }
    var seekFeedback by remember { mutableStateOf(false) }
    val surface = remember { FocusRequester() }
    val play = remember { FocusRequester() }
    LaunchedEffect(visible, inputTick, modalOpen, switching) {
        if (visible && !modalOpen && !switching) { delay(5000); visible = false }
    }
    LaunchedEffect(visible, modalOpen) {
        if (!modalOpen) withFrameNanos { }
        if (!modalOpen) {
            if (visible) play.requestFocus() else surface.requestFocus()
        }
    }
    LaunchedEffect(seekFeedback, inputTick) {
        if (seekFeedback) { delay(1200); seekFeedback = false }
    }
    // One BACK hides the footer; a second pops the actual origin entry without touching playback.
    PlatformBackHandler(enabled = !modalOpen) {
        when (tvPlayerAction(visible, TvPlayerKey.Back)) {
            TvPlayerAction.Hide -> visible = false
            TvPlayerAction.Leave -> navController.popBackStack()
            else -> Unit
        }
    }
    Box(Modifier.fillMaxSize().onPreviewKeyEvent { event ->
        if (modalOpen || event.key == Key.Back) return@onPreviewKeyEvent false
        if (event.type == KeyEventType.KeyUp && swallowedRelease == event.key) {
            swallowedRelease = null
            return@onPreviewKeyEvent true
        }
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        val key = when (event.key) {
            Key.DirectionLeft -> TvPlayerKey.Left
            Key.DirectionRight -> TvPlayerKey.Right
            Key.DirectionUp -> TvPlayerKey.Up
            Key.DirectionDown -> TvPlayerKey.Down
            Key.Enter, Key.DirectionCenter, Key.NumPadEnter -> TvPlayerKey.Activate
            else -> TvPlayerKey.Other
        }
        if (key != TvPlayerKey.Other) inputTick++
        if (tvPlayerAction(visible, key, modalOpen) == TvPlayerAction.Reveal) {
            visible = true
            swallowedRelease = event.key
            true
        } else false
    }) {
        MediaPlayerViewWithSubtitle(
            playerName = MAIN_PLAYER,
            modifier = Modifier.fillMaxSize(),
            shouldPip = false,
            shouldShowSubtitle = subtitles,
            timelineState = timeline,
            lyricsData = nowPlaying.lyricsData?.lyrics,
            translatedLyricsData = nowPlaying.lyricsData?.translatedLyrics?.first,
            isInPipMode = false,
            mainTextStyle = typo().bodyLarge,
            translatedTextStyle = typo().bodyMedium,
        )
        if (!visible) {
            Box(Modifier.fillMaxSize().focusRequester(surface).focusable())
        } else {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))))
                    .padding(horizontal = 40.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(nowPlaying.nowPlayingTitle, style = typo().titleMedium, color = Color.White, maxLines = 1)
                val videoType = playingState?.track?.videoType
                val videoRendition = if (com.maxrave.domain.utils.MusicVideoType.isKnown(videoType)) {
                    com.maxrave.domain.utils.MusicVideoType.isVideoSong(videoType)
                } else nowPlaying.isVideo
                TvRenditionSwitchControl(
                    videoRendition,
                    if (rendition.mediaId == currentId) rendition.phase else TvRenditionSwitchPhase.Idle,
                    sharedViewModel::switchTvRendition,
                )
                val fraction = if (timeline.total > 0) timeline.current.toFloat() / timeline.total else 0f
                Slider(
                    value = fraction,
                    onValueChange = { sharedViewModel.onUIEvent(UIEvent.UpdateProgress(it * 100f)) },
                    modifier = Modifier.fillMaxWidth().tvSliderKeys(
                        value = fraction, range = 0f..1f,
                        step = if (timeline.total > 0) 5000f / timeline.total else 0.02f,
                        onChange = {
                            sharedViewModel.onUIEvent(UIEvent.UpdateProgress(it * 100f))
                            seekFeedback = true
                        },
                    ),
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TvVideoButton("Previous", SimpIcons.SkipPrevious, enabled = controller.isPreviousAvailable) { sharedViewModel.onUIEvent(UIEvent.Previous) }
                    TvVideoButton(if (controller.isPlaying) "Pause" else "Play", if (controller.isPlaying) SimpIcons.Pause else SimpIcons.PlayArrow, Modifier.focusRequester(play)) { sharedViewModel.onUIEvent(UIEvent.PlayPause) }
                    TvVideoButton("Next", SimpIcons.SkipNext, enabled = controller.isNextAvailable) { sharedViewModel.onUIEvent(UIEvent.Next) }
                    Text(timeline.elapsedLabel(fraction) + " / " + timeline.lengthLabel(), style = typo().bodySmall, color = Color.White)
                    Spacer(Modifier.weight(1f))
                    TvVideoButton("Queue", SimpIcons.QueueMusic) { queue = true }
                    TvVideoButton("Subtitles", if (subtitles) SimpIcons.Subtitles else SimpIcons.SubtitlesOff) { subtitles = !subtitles }
                    TvVideoButton("More", SimpIcons.MoreVert) { more = true }
                }
            }
        }
        if (seekFeedback) Text("5 s", Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.5f)).padding(12.dp), color = Color.White)
        if (queue) QueueBottomSheet(onDismiss = { queue = false }, navController = navController, onNavigateToOtherScreen = { navController.popBackStack() })
        if (more) NowPlayingBottomSheet(
            onDismiss = { more = false },
            navController = navController,
            setSleepTimerEnable = true,
            changeMainLyricsProviderEnable = true,
            song = null,
            onAddLyrics = if (nowPlaying.lyricsNotFound && nowPlaying.lyricsData == null) {
                {
                    sharedViewModel.nowPlayingState.value?.songEntity?.let { song ->
                        addLyricsFor = arrayListOf(song.videoId, song.title, song.artistName?.joinToString(", ").orEmpty())
                    }
                }
            } else null,
        )
        addLyricsFor?.let { (videoId, title, artist) ->
            AddLyricsSheet(
                songTitle = title,
                artist = artist,
                onSave = { lyrics -> sharedViewModel.saveUserLyrics(videoId, lyrics) },
                onDismiss = { addLyricsFor = null },
            )
        }
    }
}

@Composable
internal fun TvRenditionSwitchControl(
    videoRendition: Boolean,
    phase: TvRenditionSwitchPhase,
    onSwitch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = LocalFocusManager.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(
            onClick = { if (phase != TvRenditionSwitchPhase.Pending) onSwitch() },
            // Keep the focused action mounted while pending; repeat OK is coalesced.
            modifier = modifier.tvFocus(TvFocusStyle.Control).onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) false
                else when (event.key) {
                    Key.DirectionDown -> focus.moveFocus(FocusDirection.Down)
                    Key.DirectionUp -> focus.moveFocus(FocusDirection.Up)
                    else -> false
                }
            },
        ) {
            Text(stringResource(if (videoRendition) Res.string.tv_switch_song else Res.string.tv_switch_music_video), color = Color.White)
        }
        if (phase == TvRenditionSwitchPhase.Pending) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
        val feedback = when (phase) {
            TvRenditionSwitchPhase.Pending -> Res.string.tv_rendition_pending
            TvRenditionSwitchPhase.Unavailable -> Res.string.tv_rendition_unavailable
            TvRenditionSwitchPhase.Unsupported -> Res.string.tv_rendition_unsupported
            TvRenditionSwitchPhase.Error -> Res.string.tv_rendition_error
            else -> null
        }
        feedback?.let { Text(stringResource(it), style = typo().bodySmall, color = Color.White) }
    }
}

@Composable
private fun TvVideoButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = modifier.size(48.dp).tvFocus(TvFocusStyle.Control, CircleShape)) {
        Icon(icon, contentDescription = label, tint = if (enabled) Color.White else Color.Gray)
    }
}
