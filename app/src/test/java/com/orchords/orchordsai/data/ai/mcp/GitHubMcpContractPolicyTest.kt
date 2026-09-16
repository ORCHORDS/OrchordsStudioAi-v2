package com.orchords.orchordsai.data.ai.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubMcpContractPolicyTest {
    @Test
    fun `preset matches official remote transport contract`() {
        val config = githubMcpPreset()
        val headers = config.commonOptions.headers.toMap()

        assertEquals("https://api.githubcopilot.com/mcp/", config.serverUrl)
        assertEquals("repos,issues,pull_requests", headers["X-MCP-Toolsets"])
        assertEquals("true", headers["X-MCP-Readonly"])
        assertEquals("true", headers["X-MCP-Lockdown"])
        assertEquals("", headers["Authorization"])
    }

    @Test
    fun `authorization and permission failures are distinct recoverable classes`() {
        assertTrue(McpAuthFailureClassifier.isUnauthorized(IllegalStateException("HTTP 401 Unauthorized")))
        assertTrue(McpAuthFailureClassifier.isPermissionDenied(IllegalStateException("HTTP 403 Forbidden")))
        assertTrue(McpAuthFailureClassifier.isPermissionDenied(IllegalStateException("insufficient_scope")))
        assertFalse(McpAuthFailureClassifier.isUnauthorized(IllegalStateException("HTTP 403 Forbidden")))
    }

    @Test
    fun `generic remote failures are sanitized and bounded`() {
        val cases = listOf(
            "HTTP 404 for https://api.githubcopilot.com/mcp/?token=secret-token",
            "HTTP 429 rate limited Authorization: Bearer github_pat_secret",
            "HTTP 500 body={\"access_token\":\"secret-access\"}",
            "Malformed MCP response client_secret=secret-client",
        )

        cases.forEach { raw ->
            val sanitized = McpDiagnosticSanitizer.sanitize(raw)
            assertFalse(sanitized.contains("secret-token"))
            assertFalse(sanitized.contains("github_pat_secret"))
            assertFalse(sanitized.contains("secret-access"))
            assertFalse(sanitized.contains("secret-client"))
            assertTrue(sanitized.length <= raw.length + 32)
        }
    }

    @Test
    fun `disconnect retains contract configuration but removes authentication`() {
        val connected = githubMcpPreset().withGitHubMcpPat("github_pat_secret")
        val disconnected = connected.withGitHubMcpDisconnected()

        assertFalse(hasGitHubMcpAuthentication(disconnected))
        assertEquals(GITHUB_MCP_REMOTE_ENDPOINT, disconnected.serverUrl)
        assertEquals("repos,issues,pull_requests", githubMcpToolsets(disconnected))
        assertTrue(isGitHubMcpReadOnly(disconnected))
        assertTrue(isGitHubMcpLockdown(disconnected))
    }
}
