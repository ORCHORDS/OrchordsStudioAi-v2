package com.orchords.orchordsai.data.ai.mcp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubMcpSafetyTest {
    @Test
    fun `safe preset is recognized as server enforced read only`() {
        val config = githubMcpPreset()

        assertTrue(isOfficialGitHubMcpRemote(config))
        assertTrue(isGitHubMcpReadOnly(config))
        assertFalse(githubNewToolsNeedApproval(config))
    }

    @Test
    fun `GitHub write enabled config defaults every new tool to approval required`() {
        val base = githubMcpPreset()
        val writeEnabled = base.copy(
            commonOptions = base.commonOptions.copy(
                headers = base.commonOptions.headers.map { (name, value) ->
                    if (name.equals("X-MCP-Readonly", ignoreCase = true)) name to "false"
                    else name to value
                }
            )
        )

        assertFalse(isGitHubMcpReadOnly(writeEnabled))
        assertTrue(githubNewToolsNeedApproval(writeEnabled))
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
