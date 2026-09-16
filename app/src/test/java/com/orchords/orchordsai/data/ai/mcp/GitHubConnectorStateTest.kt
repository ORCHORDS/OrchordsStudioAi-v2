package com.orchords.orchordsai.data.ai.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubConnectorStateTest {
    @Test
    fun `fresh connector is ready for PAT with safe access profile`() {
        val config = githubMcpPreset()

        assertFalse(hasGitHubMcpAuthentication(config))
        assertTrue(isGitHubMcpReadOnly(config))
        assertTrue(isGitHubMcpLockdown(config))
        assertEquals("repos,issues,pull_requests", githubMcpToolsets(config))
    }

    @Test
    fun `adding PAT transitions connector to authenticated without widening access`() {
        val config = githubMcpPreset().withGitHubMcpPat("github_pat_example")

        assertTrue(hasGitHubMcpAuthentication(config))
        assertEquals("github_pat_example", githubMcpPat(config))
        assertTrue(isGitHubMcpReadOnly(config))
        assertTrue(isGitHubMcpLockdown(config))
    }

    @Test
    fun `write opt in is explicit and reversible`() {
        val writeEnabled = githubMcpPreset().withGitHubMcpReadOnly(false)
        assertFalse(isGitHubMcpReadOnly(writeEnabled))
        assertTrue(githubNewToolsNeedApproval(writeEnabled))

        val readOnlyAgain = writeEnabled.withGitHubMcpReadOnly(true)
        assertTrue(isGitHubMcpReadOnly(readOnlyAgain))
        assertFalse(githubNewToolsNeedApproval(readOnlyAgain))
    }

    @Test
    fun `disconnect removes auth and retains connector configuration`() {
        val configured = githubMcpPreset()
            .withGitHubMcpPat("github_pat_example")
            .withGitHubMcpToolsets("repos,issues,actions")
            .withGitHubMcpReadOnly(false)
        val disconnected = configured.withGitHubMcpDisconnected()

        assertFalse(hasGitHubMcpAuthentication(disconnected))
        assertEquals(GITHUB_MCP_REMOTE_ENDPOINT, disconnected.serverUrl)
        assertEquals("repos,issues,actions", githubMcpToolsets(disconnected))
        assertFalse(isGitHubMcpReadOnly(disconnected))
        assertTrue(isGitHubMcpLockdown(disconnected))
    }

    @Test
    fun `permission and auth failures remain distinct`() {
        assertTrue(McpAuthFailureClassifier.isUnauthorized(IllegalStateException("HTTP 401 Unauthorized")))
        assertFalse(McpAuthFailureClassifier.isPermissionDenied(IllegalStateException("HTTP 401 Unauthorized")))
        assertTrue(McpAuthFailureClassifier.isPermissionDenied(IllegalStateException("HTTP 403 Forbidden")))
        assertFalse(McpAuthFailureClassifier.isUnauthorized(IllegalStateException("HTTP 403 Forbidden")))
    }
}
