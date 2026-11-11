package com.maxrave.simpmusic.ui.tv

import androidx.compose.runtime.Composable

@Composable
actual fun rememberTvDevice(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    return androidx.compose.runtime.remember(context) {
        context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK) ||
            (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK) ==
            android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
    }
}
