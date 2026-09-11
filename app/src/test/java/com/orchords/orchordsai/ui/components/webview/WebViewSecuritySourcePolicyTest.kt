package com.orchords.orchordsai.ui.components.webview

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewSecuritySourcePolicyTest {
    @Test
    fun `external profile cannot inherit trusted WebView capabilities`() {
        val source = File("src/main/java/com/orchords/orchordsai/ui/components/webview/WebView.kt").readText()
        val page = File("src/main/java/com/orchords/orchordsai/ui/pages/webview/WebViewPage.kt").readText()

        assertTrue(source.contains("allowFileAccess = false"))
        assertTrue(source.contains("allowContentAccess = false"))
        assertTrue(source.contains("WebSettings.MIXED_CONTENT_NEVER_ALLOW"))
        assertTrue(source.contains("safeBrowsingEnabled = true"))
        assertTrue(source.contains("WebViewSecurityProfile.EXTERNAL_WEB ->"))
        assertTrue(source.contains("javaScriptEnabled = false"))
        assertTrue(source.contains("domStorageEnabled = false"))
        assertTrue(source.contains("shouldOverrideUrlLoading"))
        assertFalse(source.contains("settings.javaScriptEnabled = true // Enable JavaScript"))
        assertTrue(page.contains("securityProfile = WebViewSecurityProfile.EXTERNAL_WEB"))
        assertTrue(page.contains("securityProfile = WebViewSecurityProfile.INTERNAL_TRUSTED"))
    }
}
