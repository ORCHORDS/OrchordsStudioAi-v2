package com.orchords.orchordsai.ui.components.webview

import java.net.URI

enum class WebViewSecurityProfile {
    INTERNAL_TRUSTED,
    EXTERNAL_WEB,
}

internal fun isAllowedWebViewMainFrameUrl(
    profile: WebViewSecurityProfile,
    rawUrl: String,
): Boolean = runCatching {
    val uri = URI(rawUrl)
    if (!uri.scheme.equals("https", ignoreCase = true)) return@runCatching false
    if (uri.host.isNullOrBlank() || uri.userInfo != null) return@runCatching false
    val effectivePort = if (uri.port < 0) 443 else uri.port
    when (profile) {
        WebViewSecurityProfile.EXTERNAL_WEB -> true
        WebViewSecurityProfile.INTERNAL_TRUSTED ->
            uri.host.equals("orchordsai.local", ignoreCase = true) && effectivePort == 443
    }
}.getOrDefault(false)
