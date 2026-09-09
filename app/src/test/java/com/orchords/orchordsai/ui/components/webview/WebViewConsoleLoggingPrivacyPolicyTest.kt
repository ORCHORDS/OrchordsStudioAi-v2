package com.orchords.orchordsai.ui.components.webview

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WebViewConsoleLoggingPrivacyPolicyTest {
    private fun source(): String {
        val file = File("src/main/java/com/orchords/orchordsai/ui/components/webview/WebView.kt")
        require(file.isFile) { "WebView.kt not found from ${File(".").canonicalPath}" }
        return file.readText()
    }

    @Test
    fun `webview console logging never copies message or source url into android logs`() {
        val source = source()

        assertFalse(source.contains("${'$'}{consoleMessage.message()}"))
        assertFalse(source.contains("${'$'}{consoleMessage.sourceId()}"))
        assertTrue(
            source.contains(
                "onConsoleMessage: level=${'$'}{consoleMessage.messageLevel()} line=${'$'}{consoleMessage.lineNumber()}"
            )
        )
    }
}
