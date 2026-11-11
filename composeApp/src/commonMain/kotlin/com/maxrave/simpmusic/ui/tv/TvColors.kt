package com.maxrave.simpmusic.ui.tv

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * The TV palette (spec, "Color"). The TV is always dark, and large flat areas never use pure white,
 * which blooms on TV panels.
 */
object TvPalette {
    val Background = Color(0xFF0E0E11)
    val TextPrimary = Color(0xFFF5F5F7)

    /** [TextPrimary] at 64%: about 7.5:1 on [Background], above the 4.5:1 the spec requires. */
    val TextSecondary = Color(0xA3F5F5F7)

    /** [TextPrimary] at 40%: large text and decoration only. */
    val TextTertiary = Color(0x66F5F5F7)

    val Surface1 = Color(0x0FFFFFFF) // white 6%
    val Surface2 = Color(0x1FFFFFFF) // white 12%
    val GlassFill = Color(0x14FFFFFF) // white 8%

    /** Top of the glass rim; it runs down to transparent. */
    val GlassRim = Color(0x38FFFFFF) // white 22%

    /**
     * The tone drawn under [GlassFill] where the backdrop cannot be blurred (API 30). Without it a
     * white 8% fill over artwork leaves labels unreadable; with it the panel has the density a
     * blurred pane has.
     */
    val GlassBase = Color(0xA60E0E11) // Smoked backing at 65%

    val FocusFill = Color(0x30FFFFFF)
    val OnFocus = TextPrimary

    /** The end of a scrim gradient; the other end is transparent. */
    val Scrim = Color(0xD9000000) // black 85%
}

/**
 * WCAG 2.x contrast ratio between [fg] and [bg], from 1.0 to 21.0. A translucent [fg] is composited
 * over [bg] first, since that is the colour the eye gets.
 */
fun contrastRatio(
    fg: Color,
    bg: Color,
): Double {
    val solid = if (fg.alpha < 1f) fg.compositeOver(bg) else fg
    val a = relativeLuminance(solid)
    val b = relativeLuminance(bg)
    return (max(a, b) + 0.05) / (min(a, b) + 0.05)
}

private fun relativeLuminance(color: Color): Double =
    0.2126 * linearize(color.red) + 0.7152 * linearize(color.green) + 0.0722 * linearize(color.blue)

private fun linearize(channel: Float): Double {
    val c = channel.toDouble()
    return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
}
