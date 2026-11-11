package com.maxrave.simpmusic.ui.tv

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import kotlin.math.PI
import kotlin.math.sin

/**
 * The TV's mini player, in Home's top bar beside the profile the way Spotify's TV app places it:
 * the artwork, with "Now Playing" and the track name to its right. OK opens the fullscreen player;
 * holding OK plays or pauses without leaving the page. Draws nothing while nothing is loaded.
 * It takes the `Control` focus style, and its artwork carries a playing indicator while a track plays.
 * Shown in the top bars of Home, Library and Search.
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
    // The `Control` style: a capsule that turns into the light pill with dark content when focused.
    // The key handling and the focus target are the P1 ones; only the mark around them changed.
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val titleColor = if (focused) TvPalette.OnFocus else TvPalette.TextPrimary
    val labelColor = if (focused) TvPalette.OnFocus.copy(alpha = 0.7f) else TvPalette.TextSecondary
    Row(
        modifier =
            modifier
                .tvFocus(TvFocusStyle.Control)
                .controlFill(focused = focused, restFill = TvPalette.Surface1)
                .tvRemoteActions(
                    onClick = onOpen,
                    onLongClick = { viewModel.onUIEvent(UIEvent.PlayPause) },
                    addFocusTarget = false,
                    showFocusIndicator = false,
                ).focusable(interactionSource = interactionSource)
                .padding(4.dp)
                .padding(end = 14.dp),
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
                        .clip(CircleShape),
            )
            if (controller.isPlaying) {
                PlayingIndicator(Modifier.size(ARTWORK_SIZE).clip(CircleShape))
            } else {
                Icon(
                    imageVector = SimpIcons.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier =
                        Modifier
                            .size(ARTWORK_SIZE)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f))
                            .padding(8.dp),
                )
            }
        }
        Column(Modifier.widthIn(max = TEXT_MAX_WIDTH)) {
            Text(
                stringResource(Res.string.now_playing),
                style = typo().bodySmall,
                color = labelColor,
                maxLines = 1,
            )
            Text(
                nowPlaying.nowPlayingTitle,
                style = typo().titleSmall,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Three bars over the artwork while a track plays. They move unless the system animator scale is
 * 0, where they stand still at different heights, which still reads as "playing". Only the draw
 * phase runs per frame.
 */
@Composable
private fun PlayingIndicator(modifier: Modifier = Modifier) {
    val reduced = LocalTvMotion.current.reduced
    val phase =
        if (reduced) {
            null
        } else {
            rememberInfiniteTransition(label = "tvPlaying").animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(INDICATOR_PERIOD_MS, easing = LinearEasing), RepeatMode.Restart),
                label = "tvPlayingPhase",
            )
        }
    Box(
        modifier.drawBehind {
            drawRect(Color.Black, alpha = 0.45f)
            val barWidth = size.width * 0.09f
            val gap = barWidth * 0.8f
            val maxHeight = size.height * 0.4f
            val left = (size.width - (barWidth * 3 + gap * 2)) / 2f
            val baseline = size.height / 2f + maxHeight / 2f
            val t = phase?.value ?: 0.15f
            for (i in 0 until 3) {
                // Three sines a third of a turn apart; never below a fifth of the full height.
                val level = 0.2f + 0.8f * (0.5f + 0.5f * sin((t + i / 3f) * 2f * PI.toFloat()))
                val height = maxHeight * level
                drawRoundRect(
                    color = TvPalette.TextPrimary,
                    topLeft = Offset(left + i * (barWidth + gap), baseline - height),
                    size = Size(barWidth, height),
                    cornerRadius = CornerRadius(barWidth / 2f),
                )
            }
        },
    )
}

private const val INDICATOR_PERIOD_MS = 900

private val ARTWORK_SIZE = 40.dp
private val TEXT_MAX_WIDTH = 240.dp
