package com.orchords.orchordsai.data.ai.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubMcpSafetyTest {
    @Test
    fun `safe preset is recognized as server enforced read only`() {
        val config = githubMcpPreset()

        assertTrue(isOfficialGitHubMcpRemote(config))
        assertTrue(isGitHubMcpReadOnly(config))
        assertTrue(isGitHubMcpLockdown(config))
        assertFalse(githubNewToolsNeedApproval(config))
    }

    @Test
    fun `GitHub write enabled config defaults every new tool to approval required`() {
        val writeEnabled = githubMcpPreset().withGitHubMcpReadOnly(false)

        assertFalse(isGitHubMcpReadOnly(writeEnabled))
        assertTrue(githubNewToolsNeedApproval(writeEnabled))
    }

    @Test
    fun `GitHub safety toggles update only their matching headers`() {
        val base = githubMcpPreset()
        val changed = base
            .withGitHubMcpReadOnly(false)
            .withGitHubMcpLockdown(false)

        val headers = changed.commonOptions.headers.toMap()
        assertTrue(headers["Authorization"] == "")
        assertTrue(headers["X-MCP-Toolsets"] == "repos,issues,pull_requests")
        assertTrue(headers["X-MCP-Readonly"] == "false")
        assertTrue(headers["X-MCP-Lockdown"] == "false")
    }

    @Test
    fun `GitHub PAT helper owns bearer syntax without exposing it to the user`() {
        val configured = githubMcpPreset().withGitHubMcpPat(" github_pat_example ")

        assertEquals("github_pat_example", githubMcpPat(configured))
        assertEquals(
            "Bearer github_pat_example",
            configured.commonOptions.headers.toMap()["Authorization"],
        )

        val cleared = configured.withGitHubMcpPat("")
        assertEquals("", githubMcpPat(cleared))
        assertEquals("", cleared.commonOptions.headers.toMap()["Authorization"])
    }

    @Test
    fun `custom MCP servers are not silently classified as GitHub`() {
        val custom = McpServerConfig.StreamableHTTPServer(
            commonOptions = McpCommonOptions(name = "Custom"),
            url = "https://example.com/mcp",
        )

        assertFalse(isOfficialGitHubMcpRemote(custom))
        assertFalse(githubNewToolsNeedApproval(custom))
    }
}
