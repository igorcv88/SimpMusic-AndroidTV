package com.maxrave.simpmusic.ui.tv

import androidx.compose.runtime.Composable

@Composable
actual fun rememberSystemAnimatorScale(): Float = 1f

// Never read off TV: every caller is gated on LocalTvDevice, which is false on this platform.
actual fun tvGlassTier(): TvGlassTier = TvGlassTier.Drawn
