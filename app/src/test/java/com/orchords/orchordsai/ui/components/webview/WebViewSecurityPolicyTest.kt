package com.orchords.orchordsai.ui.components.webview

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewSecurityPolicyTest {
    @Test
    fun `external profile accepts only credential-free https urls`() {
        assertTrue(isAllowedWebViewMainFrameUrl(WebViewSecurityProfile.EXTERNAL_WEB, "https://example.com/path"))
        listOf(
            "http://example.com",
            "file:///tmp/x",
            "content://provider/item",
            "javascript:alert(1)",
            "data:text/html,hello",
            "intent://example",
            "https://user@example.com/path",
        ).forEach { url ->
            assertFalse(url, isAllowedWebViewMainFrameUrl(WebViewSecurityProfile.EXTERNAL_WEB, url))
        }
    }

    @Test
    fun `internal profile is pinned to exact app origin`() {
        assertTrue(isAllowedWebViewMainFrameUrl(WebViewSecurityProfile.INTERNAL_TRUSTED, "https://orchordsai.local/assets/app.js"))
        assertTrue(isAllowedWebViewMainFrameUrl(WebViewSecurityProfile.INTERNAL_TRUSTED, "https://orchordsai.local:443/page"))
        assertFalse(isAllowedWebViewMainFrameUrl(WebViewSecurityProfile.INTERNAL_TRUSTED, "https://example.com"))
        assertFalse(isAllowedWebViewMainFrameUrl(WebViewSecurityProfile.INTERNAL_TRUSTED, "https://orchordsai.local:444/page"))
    }
}
