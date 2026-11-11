package com.maxrave.simpmusic.ui.tv

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.maxrave.simpmusic.ui.theme.typo

/**
 * The `Control` focus style as components: a capsule that is `Surface2` with primary text at rest
 * and gains a translucent `FocusFill` surface with light content when focused. The fine rim
 * comes from [tvFocus]; the content colours reach
 * the content through [LocalContentColor], so a `Text` inside must not name a colour of its own.
 */
@Composable
fun TvButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val contentColor = if (focused) TvPalette.OnFocus else TvPalette.TextPrimary
    Row(
        modifier =
            modifier
                .tvFocus(TvFocusStyle.Control)
                .controlFill(focused = focused, pressed = pressed, restFill = TvPalette.Surface2)
                .clickable(interactionSource = interactionSource, indication = null, role = Role.Button, onClick = onClick)
                .height(TV_BUTTON_HEIGHT)
                .defaultMinSize(minWidth = TV_BUTTON_HEIGHT)
                .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            content()
        }
    }
}

/** An icon-only control. [contentDescription] is not nullable on purpose: it has no other label. */
@Composable
fun TvIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier =
            modifier
                .tvFocus(TvFocusStyle.Control)
                .controlFill(focused = focused, pressed = pressed, restFill = Color.Transparent)
                .clickable(interactionSource = interactionSource, indication = null, role = Role.Button, onClick = onClick)
                .size(size),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (focused) TvPalette.OnFocus else TvPalette.TextPrimary,
            modifier = Modifier.size(size / 2),
        )
    }
}

/**
 * A filter pill. Selection and focus stay distinct: a selected chip is filled with `Surface2` and
 * has primary text, an unselected one is `Surface1` with secondary text, and focus turns either
 * into the translucent `FocusFill` surface with a brighter rim.
 */
@Composable
fun TvChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val contentColor =
        when {
            focused -> TvPalette.OnFocus
            selected -> TvPalette.TextPrimary
            else -> TvPalette.TextSecondary
        }
    Box(
        modifier =
            modifier
                .tvFocus(TvFocusStyle.Control)
                .controlFill(focused = focused, pressed = pressed, restFill = if (selected) TvPalette.Surface2 else TvPalette.Surface1)
                .clickable(interactionSource = interactionSource, indication = null, role = Role.Tab, onClick = onClick)
                .height(TV_CHIP_HEIGHT)
                .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = typo().labelLarge,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The capsule fill of a `Control`: [restFill] at rest, `FocusFill` when focused. Draw phase only. */
internal fun Modifier.controlFill(
    focused: Boolean,
    restFill: Color,
    pressed: Boolean = false,
): Modifier =
    drawBehind {
        val fill = when {
            pressed -> Color.White.copy(alpha = 0.1f)
            focused -> TvPalette.FocusFill
            else -> restFill
        }
        if (fill.alpha > 0f) {
            drawOutline(TvShapes.Control.createOutline(size, layoutDirection, this), fill)
        }
    }

private val TV_BUTTON_HEIGHT = 40.dp
private val TV_CHIP_HEIGHT = 36.dp
