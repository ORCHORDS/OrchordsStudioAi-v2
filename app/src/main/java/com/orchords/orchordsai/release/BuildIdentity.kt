package com.orchords.orchordsai.release

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import com.orchords.orchordsai.BuildConfig

data class BuildIdentity(
    val versionName: String,
    val versionCode: Long,
    val buildSha: String,
    val channel: String,
) {
    val shortSha: String
        get() = buildSha
            .takeIf { it.matches(Regex("[0-9a-fA-F]{7,40}")) }
            ?.take(8)
            ?: "local"

    fun displayText(): String = "$versionName ($versionCode) • $channel • $shortSha"

    /** Stable, copy-safe identity for QA/diagnostics. Includes the full source SHA when available. */
    fun diagnosticText(packageName: String): String = buildString {
        append("Package: ").append(packageName).append('\n')
        append("Version: ").append(versionName).append(" (").append(versionCode).append(")\n")
        append("Channel: ").append(channel).append('\n')
        append("Source SHA: ").append(buildSha.takeIf { it.matches(Regex("[0-9a-fA-F]{40}")) } ?: "local")
    }
}

/** Read the identity of the APK that is actually installed, then add immutable build metadata. */
fun resolveBuildIdentity(context: Context): BuildIdentity {
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    return BuildIdentity(
        versionName = packageInfo.versionName?.takeIf { it.isNotBlank() } ?: BuildConfig.VERSION_NAME,
        versionCode = PackageInfoCompat.getLongVersionCode(packageInfo),
        buildSha = BuildConfig.BUILD_SHA,
        channel = BuildConfig.BUILD_CHANNEL,
    )
}
