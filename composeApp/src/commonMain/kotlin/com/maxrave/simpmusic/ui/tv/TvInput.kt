@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.maxrave.simpmusic.ui.tv

import androidx.compose.foundation.Indication
import androidx.compose.foundation.clickable as foundationClickable
import androidx.compose.foundation.combinedClickable as foundationCombinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Provided once at the existing App root; false leaves touch/phone behavior unchanged. */
val LocalTvDevice = staticCompositionLocalOf { false }

@Composable
expect fun rememberTvDevice(): Boolean

/**
 * The P1 focus mark, kept for the call sites that have not been migrated to a named style: it is
 * [tvFocus] in [TvFocusStyle.Backing] (scale 1.06 and a soft backing in the element's own shape, no
 * outline, layout unchanged). When a [tvFocus] of another style wraps the same focus target, that
 * one shows and this one stays at rest, so the two scales never multiply.
 */
fun Modifier.tvFocusIndicator(shape: Shape? = null): Modifier = tvFocus(TvFocusStyle.Backing, shape)

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
    onLeft: (() -> Unit)? = null, onRight: (() -> Unit)? = null, addFocusTarget: Boolean = true,
    // Off for surfaces that cover the screen, such as the video's seek halves: a ring around half
    // the screen marks nothing and hides the picture.
    showFocusIndicator: Boolean = true): Modifier = composed {
    if (!LocalTvDevice.current) return@composed this
    val longPressTimeout = androidx.compose.ui.platform.LocalViewConfiguration.current.longPressTimeoutMillis
    val scope = rememberCoroutineScope()
    var hold by remember { mutableStateOf<Job?>(null) }
    var pressed by remember { mutableStateOf(false) }
    var longPressed by remember { mutableStateOf(false) }
    var ownsFocus by remember { mutableStateOf(false) }
    val click by rememberUpdatedState(onClick)
    val longClick by rememberUpdatedState(onLongClick)
    DisposableEffect(Unit) { onDispose { hold?.cancel() } }
    val actions = (if (showFocusIndicator) tvFocusIndicator() else this).onFocusChanged {
        ownsFocus = it.isFocused
        if (!it.isFocused) { hold?.cancel(); pressed = false }
    }.onPreviewKeyEvent { event ->
        if (!ownsFocus) return@onPreviewKeyEvent false
        when (event.key) {
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                if (click == null && longClick == null) false
                else {
                    if (event.type == KeyEventType.KeyDown && !pressed) {
                        pressed = true
                        longPressed = false
                        if (longClick != null) hold = scope.launch {
                            delay(longPressTimeout)
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
    }
    if (addFocusTarget) actions.focusable() else actions
}

/** Adjusts a defined step along the slider axis; orthogonal arrows leave the control. */
fun Modifier.tvSliderKeys(value: Float, range: ClosedFloatingPointRange<Float>, step: Float,
    onChange: (Float) -> Unit, onFinished: (() -> Unit)? = null, customSurface: Boolean = false,
    vertical: Boolean = false, activationRequired: Boolean = false): Modifier = composed {
    if (!LocalTvDevice.current) return@composed this
    var pending by remember(value) { mutableFloatStateOf(value) }
    var ownsFocus by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var adjusting by remember { mutableStateOf(false) }
    com.maxrave.simpmusic.expect.ui.PlatformBackHandler(enabled = activationRequired && editing) { editing = false }
    val decrease = if (vertical) Key.DirectionDown else Key.DirectionLeft
    val increase = if (vertical) Key.DirectionUp else Key.DirectionRight
    val keys = tvFocusIndicator().onFocusChanged {
        ownsFocus = it.isFocused
        if (!it.isFocused) {
            editing = false
            if (adjusting) { adjusting = false; onFinished?.invoke() }
        }
    }.onPreviewKeyEvent { event ->
        if (!ownsFocus) false
        else if (activationRequired && event.key in listOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)) {
            if (event.type == KeyEventType.KeyUp) editing = !editing
            true
        } else if (event.key != decrease && event.key != increase || activationRequired && !editing) false
        else {
            if (event.type == KeyEventType.KeyDown) {
                pending = (pending + if (event.key == increase) step else -step).coerceIn(range)
                adjusting = true
                onChange(pending)
            } else if (event.type == KeyEventType.KeyUp && adjusting) {
                adjusting = false
                onFinished?.invoke()
            }
            true
        }
    }
    if (customSurface) keys.focusable() else keys
}

/** Explicit OK reopens the TV IME after Back; arrows can leave an input without a touch. */
fun Modifier.tvTextInput(onActivate: () -> Unit = {}): Modifier = composed {
    if (!LocalTvDevice.current) return@composed this
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    var ownsFocus by remember { mutableStateOf(false) }
    tvFocusIndicator().onFocusChanged { ownsFocus = it.isFocused }.onPreviewKeyEvent { event ->
        if (!ownsFocus) return@onPreviewKeyEvent false
        when (event.key) {
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                if (event.type == KeyEventType.KeyUp) {
                    onActivate()
                    keyboard?.show()
                }
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

/** Requests the existing control's focus when a TV page appears. */
fun Modifier.tvInitialFocus(): Modifier = composed {
    if (!LocalTvDevice.current) return@composed this
    val requester = remember { androidx.compose.ui.focus.FocusRequester() }
    val navigation = LocalTvNavigation.current
    val destination = LocalTvDestination.current
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(placed, navigation, destination) {
        if (placed && navigation?.hasSavedFocus(destination) != true) requester.requestFocus()
    }
    this.focusRequester(requester).onGloballyPositioned { placed = true }
}
