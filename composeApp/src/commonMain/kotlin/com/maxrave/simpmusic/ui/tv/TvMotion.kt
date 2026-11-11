package com.maxrave.simpmusic.ui.tv

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The TV motion tokens (spec, "Motion"). Springs start from the current value and velocity, so a
 * held D-pad key retargets the running animation instead of queueing a new one.
 *
 * [reduced] is the system animator scale at 0, which many TV owners set: springs snap and fades
 * take 0 ms, so every change lands in one frame while focus feedback stays.
 */
data class TvMotion(
    val reduced: Boolean,
    val focusSpring: AnimationSpec<Float>,
    val panelSpring: AnimationSpec<Float>,
    val fadeInMs: Int,
    val fadeOutMs: Int,
)

private val FocusSpring: AnimationSpec<Float> = spring(dampingRatio = 1f, stiffness = 600f)
private val PanelSpring: AnimationSpec<Float> = spring(dampingRatio = 0.9f, stiffness = 380f)
private val Snap: AnimationSpec<Float> = snap()

private val FullMotion =
    TvMotion(reduced = false, focusSpring = FocusSpring, panelSpring = PanelSpring, fadeInMs = 180, fadeOutMs = 140)
private val ReducedMotion =
    TvMotion(reduced = true, focusSpring = Snap, panelSpring = Snap, fadeInMs = 0, fadeOutMs = 0)

/** Both results are shared instances, so providing one never invalidates readers by itself. */
fun resolveTvMotion(animatorScale: Float): TvMotion = if (animatorScale == 0f) ReducedMotion else FullMotion

val LocalTvMotion = staticCompositionLocalOf { resolveTvMotion(1f) }

/** `Settings.Global.ANIMATOR_DURATION_SCALE` on Android (1 when unset); 1 everywhere else. */
@Composable
expect fun rememberSystemAnimatorScale(): Float

/** The fade-in as a spec for `Crossfade`/`AnimatedContent`; immediate under reduced motion. */
fun TvMotion.fadeInSpec(): FiniteAnimationSpec<Float> = if (reduced) snap() else tween(fadeInMs)

/** The fade-out counterpart of [fadeInSpec]. */
fun TvMotion.fadeOutSpec(): FiniteAnimationSpec<Float> = if (reduced) snap() else tween(fadeOutMs)
