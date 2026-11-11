@file:OptIn(ExperimentalFoundationApi::class)

package com.maxrave.simpmusic.ui.tv

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

/**
 * The scroll that puts an item's start on the keyline, Leanback style: the focused item stays at
 * one fixed place and the content moves under it.
 *
 * It is 0 when the item already sits on the keyline, so a second request for the same item — which
 * is what a held D-pad key produces while the scroll animation is still running — asks for nothing
 * more. Clamping at the ends of the list is left to the scrollable, which refuses to scroll past
 * them; the first and last items therefore rest where the list lets them.
 *
 * @param offset the item's start, relative to the container's start, in px.
 * @param size the item's size along the scroll axis, in px. Unused: an item wider than what is left
 * after the keyline still starts on it, which is the edge the eye tracks.
 * @param containerSize the viewport's size along the scroll axis, in px. Unused for a fixed keyline.
 */
@Suppress("UNUSED_PARAMETER")
fun tvKeylineScrollDelta(
    offset: Float,
    size: Float,
    containerSize: Float,
    keylinePx: Float,
): Float = offset - keylinePx

/**
 * Rows inside [content] keep the focused item's start on [keyline], measured from the row's start
 * edge (the right edge in right-to-left layouts). Off TV it only calls [content].
 *
 * A `BringIntoViewSpec` does not know which axis it serves: it answers every scrollable below it.
 * Provide this one directly around a horizontal row, inside the item of the column it belongs to.
 */
@Composable
fun ProvideTvRowScrolling(
    keyline: Dp = TvDimens.SafeHorizontal,
    content: @Composable () -> Unit,
) {
    if (!LocalTvDevice.current) {
        content()
        return
    }
    val keylinePx = with(LocalDensity.current) { keyline.toPx() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val spec = remember(keylinePx, rtl) { FixedKeylineSpec(keylinePx, rtl) }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
}

/**
 * The column inside [content] keeps the focused element's top at [fraction] of its own viewport
 * height (25% in the spec). Off TV it only calls [content].
 *
 * The spec reaches every scrollable below it, rows included, so each item of the column must go
 * through [TvColumnItem]: that hands rows back the platform's behaviour, and a row that wants the
 * keyline then opts in with [ProvideTvRowScrolling]. Without it a row would use this vertical
 * keyline on its horizontal axis.
 */
@Composable
fun ProvideTvColumnScrolling(
    fraction: Float = TV_COLUMN_KEYLINE,
    content: @Composable () -> Unit,
) {
    if (!LocalTvDevice.current) {
        content()
        return
    }
    val outer = LocalBringIntoViewSpec.current
    val spec = remember(fraction) { FractionKeylineSpec(fraction) }
    CompositionLocalProvider(
        LocalBringIntoViewSpec provides spec,
        LocalTvOuterBringIntoViewSpec provides outer,
        content = content,
    )
}

/**
 * Wraps one item of a column that sits in [ProvideTvColumnScrolling]: scrollables inside it get
 * back the behaviour they had outside the column. Anywhere else it only calls [content].
 */
@Composable
fun TvColumnItem(content: @Composable () -> Unit) {
    val outer = LocalTvOuterBringIntoViewSpec.current
    if (outer == null) {
        content()
    } else {
        CompositionLocalProvider(
            LocalBringIntoViewSpec provides outer,
            LocalTvOuterBringIntoViewSpec provides null,
            content = content,
        )
    }
}

internal const val TV_COLUMN_KEYLINE = 0.25f

private val LocalTvOuterBringIntoViewSpec = staticCompositionLocalOf<BringIntoViewSpec?> { null }

private class FixedKeylineSpec(
    private val keylinePx: Float,
    private val rtl: Boolean,
) : BringIntoViewSpec {
    override fun calculateScrollDistance(
        offset: Float,
        size: Float,
        containerSize: Float,
    ): Float =
        if (rtl) {
            // Mirror the row: measure the item's start from the right edge, then flip the answer
            // back into the left-to-right distance the scrollable works in.
            -tvKeylineScrollDelta(containerSize - offset - size, size, containerSize, keylinePx)
        } else {
            tvKeylineScrollDelta(offset, size, containerSize, keylinePx)
        }
}

private class FractionKeylineSpec(
    private val fraction: Float,
) : BringIntoViewSpec {
    override fun calculateScrollDistance(
        offset: Float,
        size: Float,
        containerSize: Float,
    ): Float = tvKeylineScrollDelta(offset, size, containerSize, containerSize * fraction)
}
