@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.maxrave.simpmusic.ui.tv

import androidx.compose.foundation.Indication
import androidx.compose.foundation.clickable as foundationClickable
import androidx.compose.foundation.combinedClickable as foundationCombinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Provided once at the existing App root; false leaves touch/phone behavior unchanged. */
val LocalTvDevice = staticCompositionLocalOf { false }

@Composable
expect fun rememberTvDevice(): Boolean

/** One consistent focus mark, drawn inside the existing bounds without changing layout. */
fun Modifier.tvFocusIndicator(): Modifier = composed {
    if (!LocalTvDevice.current) return@composed this
    var focused by remember { mutableStateOf(false) }
    val color = MaterialTheme.colorScheme.primary
    onFocusChanged { focused = it.isFocused }.drawWithContent {
        drawContent()
        if (focused) {
            val stroke = 3.dp.toPx()
            drawRoundRect(color, Offset(stroke / 2, stroke / 2),
                Size((size.width - stroke).coerceAtLeast(0f), (size.height - stroke).coerceAtLeast(0f)),
                CornerRadius(8.dp.toPx()), style = Stroke(stroke))
        }
    }
}

// Same gesture/semantics implementations as Foundation, with a TV-only focus mark.
fun Modifier.tvClickable(enabled: Boolean = true, onClickLabel: String? = null,
    role: Role? = null, onClick: () -> Unit): Modifier =
    tvFocusIndicator().foundationClickable(enabled = enabled, onClickLabel = onClickLabel, role = role, onClick = onClick)

fun Modifier.tvClickable(interactionSource: MutableInteractionSource?, indication: Indication?,
    enabled: Boolean = true, onClickLabel: String? = null, role: Role? = null,
    onClick: () -> Unit): Modifier =
    tvFocusIndicator().foundationClickable(interactionSource = interactionSource, indication = indication, enabled = enabled, onClickLabel = onClickLabel, role = role, onClick = onClick)

fun Modifier.tvCombinedClickable(enabled: Boolean = true, onClickLabel: String? = null,
    role: Role? = null, onLongClickLabel: String? = null, onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null, onClick: () -> Unit): Modifier =
    tvFocusIndicator().foundationCombinedClickable(enabled = enabled, onClickLabel = onClickLabel,
        role = role, onLongClickLabel = onLongClickLabel, onLongClick = onLongClick,
        onDoubleClick = onDoubleClick, onClick = onClick)

fun Modifier.tvCombinedClickable(interactionSource: MutableInteractionSource?, indication: Indication?,
    enabled: Boolean = true, onClickLabel: String? = null, role: Role? = null,
    onLongClickLabel: String? = null, onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null, onClick: () -> Unit): Modifier =
    tvFocusIndicator().foundationCombinedClickable(interactionSource = interactionSource, indication = indication,
        enabled = enabled, onClickLabel = onClickLabel, role = role, onLongClickLabel = onLongClickLabel,
        onLongClick = onLongClick, onDoubleClick = onDoubleClick, onClick = onClick)

/** Adds remote actions to pointer-only surfaces; touch gestures remain attached unchanged. */
fun Modifier.tvRemoteActions(onClick: (() -> Unit)? = null, onLongClick: (() -> Unit)? = null,
    onLeft: (() -> Unit)? = null, onRight: (() -> Unit)? = null): Modifier = composed {
    if (!LocalTvDevice.current) return@composed this
    val scope = rememberCoroutineScope()
    var hold by remember { mutableStateOf<Job?>(null) }
    var pressed by remember { mutableStateOf(false) }
    var longPressed by remember { mutableStateOf(false) }
    val click by rememberUpdatedState(onClick)
    val longClick by rememberUpdatedState(onLongClick)
    DisposableEffect(Unit) { onDispose { hold?.cancel() } }
    tvFocusIndicator().onPreviewKeyEvent { event ->
        when (event.key) {
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                if (click == null && longClick == null) false
                else {
                    if (event.type == KeyEventType.KeyDown && !pressed) {
                        pressed = true
                        longPressed = false
                        if (longClick != null) hold = scope.launch {
                            delay(500)
                            longPressed = true
                            longClick?.invoke()
                        }
                    } else if (event.type == KeyEventType.KeyUp && pressed) {
                        hold?.cancel()
                        pressed = false
                        if (!longPressed) click?.invoke()
                    }
                    true
                }
            }
            Key.DirectionLeft -> if (onLeft != null) {
                if (event.type == KeyEventType.KeyDown) onLeft(); true
            } else false
            Key.DirectionRight -> if (onRight != null) {
                if (event.type == KeyEventType.KeyDown) onRight(); true
            } else false
            else -> false
        }
    }.focusable()
}

/** Left/right adjusts a defined step; up/down remain available for leaving the control. */
fun Modifier.tvSliderKeys(value: Float, range: ClosedFloatingPointRange<Float>, step: Float,
    onChange: (Float) -> Unit, onFinished: () -> Unit = {}, customSurface: Boolean = false): Modifier = composed {
    if (!LocalTvDevice.current) return@composed this
    var pending by remember(value) { mutableFloatStateOf(value) }
    val keys = tvFocusIndicator().onPreviewKeyEvent { event ->
        if (event.key != Key.DirectionLeft && event.key != Key.DirectionRight) false
        else {
            if (event.type == KeyEventType.KeyDown) {
                pending = (pending + if (event.key == Key.DirectionRight) step else -step).coerceIn(range)
                onChange(pending)
            } else if (event.type == KeyEventType.KeyUp) onFinished()
            true
        }
    }
    if (customSurface) keys.focusable() else keys
}

/** Explicit OK reopens the TV IME after Back; arrows can leave an input without a touch. */
fun Modifier.tvTextInput(): Modifier = composed {
    if (!LocalTvDevice.current) return@composed this
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    tvFocusIndicator().onPreviewKeyEvent { event ->
        when (event.key) {
            Key.DirectionCenter -> {
                if (event.type == KeyEventType.KeyUp) keyboard?.show()
                true
            }
            Key.DirectionDown, Key.DirectionUp -> {
                if (event.type == KeyEventType.KeyDown) {
                    keyboard?.hide()
                    focus.moveFocus(if (event.key == Key.DirectionDown) FocusDirection.Down else FocusDirection.Up)
                }
                true
            }
            else -> false
        }
    }
}

fun Modifier.tvFocusRestorer(): Modifier = composed {
    if (LocalTvDevice.current) focusRestorer() else this
}
