package com.maxrave.simpmusic.tv

import android.content.pm.PackageManager
import android.graphics.Color
import android.widget.TextView
import com.maxrave.logger.Logger
import com.maxrave.simpmusic.MainActivity
import java.security.MessageDigest

/** Reuses MainActivity's initialization, binding, intents, permissions and complete App UI. */
class TvActivity : MainActivity() {
    override fun checkBuildIdentity(): Boolean {
        val hashes = runCatching {
            packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners.orEmpty().map { signature ->
                    MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
                        .joinToString("") { "%02x".format(it) }
                }
        }.getOrDefault(emptyList())
        val reason = when {
            packageName != TV_PACKAGE -> "Package not recognized"
            hashes != listOf(TV_CERT_SHA256) -> "Signing certificate not recognized"
            else -> return true
        }
        // Package names and signing certificate digests are public, never authentication secrets.
        Logger.w("TvIdentity", "$reason: expectedPackage=$TV_PACKAGE foundPackage=$packageName foundSha256=${hashes.joinToString().ifEmpty { "unavailable" }}")
        setContentView(TextView(this).apply {
            text = "$reason.\nExpected package: $TV_PACKAGE\nFound package: $packageName\nCertificate SHA-256: ${hashes.joinToString().ifEmpty { "unavailable" }}"
            textSize = 22f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.BLACK)
            setPadding(48, 48, 48, 48)
        })
        return false
    }

    companion object {
        private const val TV_PACKAGE = "com.maxrave.simpmusic.tv"
        private const val TV_CERT_SHA256 = "e5d68192109e8c65f4d433cb4beda0a47c40e7b73c9e48832673b24a6b0b9257"
    }
}
