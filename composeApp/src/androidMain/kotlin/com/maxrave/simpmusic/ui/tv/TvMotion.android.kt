package com.maxrave.simpmusic.ui.tv

import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberSystemAnimatorScale(): Float {
    val context = LocalContext.current
    // Read once per composition root: the setting changes in developer options, away from the app,
    // and the Activity is recreated or resumed into a new composition often enough on a TV.
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }
}

/**
 * API 30 has no RenderEffect, so a backdrop can be neither blurred nor refracted. API 31-32 blur
 * through RenderEffect but have no RuntimeShader, which the lens needs. API 33+ run all of it.
 */
actual fun tvGlassTier(): TvGlassTier =
    when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> TvGlassTier.Liquid
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> TvGlassTier.Blur
        else -> TvGlassTier.Drawn
    }
