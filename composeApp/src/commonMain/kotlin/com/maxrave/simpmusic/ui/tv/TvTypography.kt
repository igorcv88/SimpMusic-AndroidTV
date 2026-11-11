package com.maxrave.simpmusic.ui.tv

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

/**
 * The TV type scale (spec, "Typography"). Only size, line height and tracking change: the font,
 * weight, colour and text direction of [base] are kept, so `typo()`'s theme and RTL handling
 * carries over unchanged.
 */
fun tvTypography(base: Typography): Typography =
    base.copy(
        displayLarge = base.displayLarge.tv(48f, 1.12f, -0.5f),
        displayMedium = base.displayMedium.tv(40f, 1.12f, -0.5f),
        displaySmall = base.displaySmall.tv(34f, 1.15f, -0.25f),
        headlineLarge = base.headlineLarge.tv(30f, 1.2f, -0.25f),
        headlineMedium = base.headlineMedium.tv(26f, 1.2f, -0.25f),
        headlineSmall = base.headlineSmall.tv(22f, 1.25f, 0f),
        titleLarge = base.titleLarge.tv(20f, 1.3f, 0f),
        titleMedium = base.titleMedium.tv(16f, 1.35f, 0f),
        titleSmall = base.titleSmall.tv(14f, 1.35f, 0.1f),
        bodyLarge = base.bodyLarge.tv(16f, 1.45f, 0f),
        bodyMedium = base.bodyMedium.tv(14f, 1.45f, 0f),
        bodySmall = base.bodySmall.tv(13f, 1.4f, 0.1f),
        labelLarge = base.labelLarge.tv(14f, 1.3f, 0.1f),
        labelMedium = base.labelMedium.tv(13f, 1.3f, 0.2f),
        labelSmall = base.labelSmall.tv(12f, 1.3f, 0.2f),
    )

private fun TextStyle.tv(
    sizeSp: Float,
    lineHeightMultiple: Float,
    trackingSp: Float,
): TextStyle =
    copy(
        fontSize = sizeSp.sp,
        lineHeight = (sizeSp * lineHeightMultiple).sp,
        letterSpacing = trackingSp.sp,
    )
