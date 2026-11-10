@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.maxrave.simpmusic.ui.tv

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
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

/**
 * The TV focus mark: the focused element grows a little and gets a soft light backing in its own
 * shape, the way TV launchers mark focus, instead of an outline. Nothing changes layout.
 *
 * The backing follows the element's likely outline (a circle on square elements, a capsule on
 * pills, a 16dp corner otherwise) because most call sites sit behind a `clip(...)` that would cut
 * the corners off a rectangle. A caller that knows the shape passes it.
 */
fun Modifier.tvFocusIndicator(shape: Shape? = null): Modifier = composed {
    if (!LocalTvDevice.current) return@composed this
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) TV_FOCUS_SCALE else 1f, label = "tvFocusScale")
    val backing by animateFloatAsState(if (focused) TV_FOCUS_BACKING_ALPHA else 0f, label = "tvFocusBacking")
    onFocusChanged { focused = it.isFocused }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }.drawWithContent {
            if (backing > 0f) {
                val outline = (shape ?: defaultFocusShape(size)).createOutline(size, layoutDirection, this)
                drawOutline(outline, Color.White.copy(alpha = backing))
            }
            drawContent()
        }
}

private const val TV_FOCUS_SCALE = 1.06f
private const val TV_FOCUS_BACKING_ALPHA = 0.16f
private val TV_FOCUS_MAX_CORNER = 16.dp

private fun androidx.compose.ui.unit.Density.defaultFocusShape(size: Size): Shape {
    val shortSide = minOf(size.width, size.height)
    val isSquare = shortSide > 0f && kotlin.math.abs(size.width - size.height) / shortSide < 0.1f
    if (isSquare) return CircleShape
    return RoundedCornerShape(minOf(shortSide / 2f, TV_FOCUS_MAX_CORNER.toPx()))
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
fun Modifier.tvTextInput(): Modifier = composed {
    if (!LocalTvDevice.current) return@composed this
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    var ownsFocus by remember { mutableStateOf(false) }
    tvFocusIndicator().onFocusChanged { ownsFocus = it.isFocused }.onPreviewKeyEvent { event ->
        if (!ownsFocus) return@onPreviewKeyEvent false
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

/** Requests the existing control's focus when a TV page appears. */
fun Modifier.tvInitialFocus(): Modifier = composed {
    if (!LocalTvDevice.current) return@composed this
    val requester = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        requester.requestFocus()
    }
    this.then(Modifier.focusRequester(requester))
}
