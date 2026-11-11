package com.maxrave.simpmusic.ui.tv

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

/** Continuous corner surface shared by artwork, clipping and the focus rim. */
data class TvSquircleShape(val radius: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { radius.toPx() }.coerceAtMost(minOf(size.width, size.height) / 2f)
        // The control points hold the tangent longer than a circular corner, softly squaring it.
        val c = r * 0.22f
        return Outline.Generic(Path().apply {
            moveTo(r, 0f)
            lineTo(size.width - r, 0f)
            cubicTo(size.width - c, 0f, size.width, c, size.width, r)
            lineTo(size.width, size.height - r)
            cubicTo(size.width, size.height - c, size.width - c, size.height, size.width - r, size.height)
            lineTo(r, size.height)
            cubicTo(c, size.height, 0f, size.height - c, 0f, size.height - r)
            lineTo(0f, r)
            cubicTo(0f, c, c, 0f, r, 0f)
            close()
        })
    }
}
