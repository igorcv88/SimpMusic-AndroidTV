package com.maxrave.simpmusic.ui.tv

import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Extracted glass is a sibling pass, so its content ancestors cannot fade independently. */
@Composable
internal fun <T> TvBackdropCrossfade(
    targetState: T,
    modifier: Modifier = Modifier,
    animationSpec: FiniteAnimationSpec<Float> = tween(),
    label: String = "Crossfade",
    content: @Composable (T) -> Unit,
) {
    if (LocalTvBackdropOverlay.current != null) {
        Box(modifier) { content(targetState) }
    } else {
        Crossfade(targetState, modifier, animationSpec, label, content)
    }
}

@Composable
internal fun tvBackdropEnter(transition: EnterTransition): EnterTransition =
    if (LocalTvBackdropOverlay.current != null) EnterTransition.None else transition

@Composable
internal fun tvBackdropExit(transition: ExitTransition): ExitTransition =
    if (LocalTvBackdropOverlay.current != null) ExitTransition.None else transition
