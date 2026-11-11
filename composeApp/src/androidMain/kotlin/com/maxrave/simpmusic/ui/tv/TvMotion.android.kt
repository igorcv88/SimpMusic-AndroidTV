package com.maxrave.simpmusic.ui.tv

import android.os.Build
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberSystemAnimatorScale(): Float {
    val context = LocalContext.current
    val resolver = context.contentResolver
    var scale by remember(resolver) {
        mutableFloatStateOf(Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f))
    }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                scale = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        scale = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return scale
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
