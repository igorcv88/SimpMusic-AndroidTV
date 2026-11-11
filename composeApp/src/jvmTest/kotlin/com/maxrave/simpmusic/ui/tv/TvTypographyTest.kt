package com.maxrave.simpmusic.ui.tv

import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TvTypographyTest {
    @Test
    fun noStyleBelow12sp() =
        tvTypography(Typography()).let { t ->
            listOf(t.labelSmall, t.bodySmall, t.labelMedium).forEach { assertTrue(it.fontSize.value >= 12f) }
        }

    @Test
    fun headlineSmallIs22sp() = assertEquals(22f, tvTypography(Typography()).headlineSmall.fontSize.value)

    @Test
    fun keepsBaseColor() =
        assertEquals(
            Color.Red,
            tvTypography(Typography(bodyMedium = TextStyle(color = Color.Red))).bodyMedium.color,
        )
}
