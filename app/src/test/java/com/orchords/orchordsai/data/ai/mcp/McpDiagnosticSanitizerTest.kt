package com.orchords.orchordsai.data.ai.mcp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class McpDiagnosticSanitizerTest {
    @Test
    fun `sanitizer removes credential shaped values but keeps useful context`() {
        val raw = """
            HTTP 401 for https://api.githubcopilot.com/mcp/?access_token=url-access&code=oauth-code
            Authorization: Bearer github_pat_11AA_secretvalue
            Proxy-Authorization: Basic proxy-secret
            Cookie: session=super-secret-cookie
            {"error":"invalid_grant","refresh_token":"refresh-secret","client_secret":"client-secret","access_token":"access-secret"}
        """.trimIndent()

        val sanitized = McpDiagnosticSanitizer.sanitize(raw)

        listOf(
            "url-access",
            "oauth-code",
            "github_pat_11AA_secretvalue",
            "proxy-secret",
            "super-secret-cookie",
            "refresh-secret",
            "client-secret",
            "access-secret",
        ).forEach { secret ->
            assertFalse("diagnostics must not contain $secret", sanitized.contains(secret))
        }
        assertTrue(sanitized.contains("HTTP 401"))
        assertTrue(sanitized.contains("api.githubcopilot.com/mcp/"))
        assertTrue(sanitized.contains("invalid_grant"))
        assertTrue(sanitized.contains("[REDACTED]"))
    }

    @Test
    fun `status error factory sanitizes message and stack detail`() {
        val error = IllegalStateException("Authorization: Bearer ghp_secret123 at https://example.test/?token=query-secret")

        val status = McpStatus.Error.from(error)

        assertFalse(status.message.contains("ghp_secret123"))
        assertFalse(status.message.contains("query-secret"))
        assertFalse(status.detail.orEmpty().contains("ghp_secret123"))
        assertFalse(status.detail.orEmpty().contains("query-secret"))
    }
}
