package com.maxrave.simpmusic.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.maxrave.simpmusic.ui.icon.PlayArrow
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.viewModel.SharedViewModel
import com.maxrave.simpmusic.viewModel.UIEvent
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.now_playing

/**
 * The TV's mini player, in Home's top bar beside the profile the way Spotify's TV app places it:
 * the artwork, with "Now Playing" and the track name to its right. OK opens the fullscreen player;
 * holding OK plays or pauses without leaving the page. Draws nothing while nothing is loaded.
 */
@Composable
fun TvNowPlayingChip(
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SharedViewModel = koinInject(),
) {
    val nowPlaying by viewModel.nowPlayingScreenData.collectAsStateWithLifecycle()
    val controller by viewModel.controllerState.collectAsStateWithLifecycle()
    if (nowPlaying.nowPlayingTitle.isBlank()) return
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(CHIP_CORNER))
                .tvRemoteActions(
                    onClick = onOpen,
                    onLongClick = { viewModel.onUIEvent(UIEvent.PlayPause) },
                    addFocusTarget = false,
                ).focusable()
                .padding(4.dp)
                .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            AsyncImage(
                model = nowPlaying.thumbnailURL,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .size(ARTWORK_SIZE)
                        .clip(RoundedCornerShape(6.dp)),
            )
            if (!controller.isPlaying) {
                Icon(
                    imageVector = SimpIcons.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier =
                        Modifier
                            .size(ARTWORK_SIZE)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.45f))
                            .padding(8.dp),
                )
            }
        }
        Column(Modifier.widthIn(max = TEXT_MAX_WIDTH)) {
            Text(
                stringResource(Res.string.now_playing),
                style = typo().bodySmall,
                maxLines = 1,
            )
            Text(
                nowPlaying.nowPlayingTitle,
                style = typo().titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val ARTWORK_SIZE = 40.dp
private val CHIP_CORNER = 12.dp
private val TEXT_MAX_WIDTH = 240.dp
