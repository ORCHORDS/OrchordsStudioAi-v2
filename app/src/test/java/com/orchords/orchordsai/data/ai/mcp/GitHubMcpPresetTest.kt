package com.orchords.orchordsai.data.ai.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubMcpPresetTest {
    @Test
    fun `GitHub preset uses official remote endpoint and conservative safety defaults`() {
        val preset = githubMcpPreset()

        assertTrue(preset is McpServerConfig.StreamableHTTPServer)
        assertEquals("GitHub", preset.commonOptions.name)
        assertEquals(GITHUB_MCP_REMOTE_ENDPOINT, preset.serverUrl)

        val headers = preset.commonOptions.headers.toMap()
        assertEquals("", headers["Authorization"])
        assertEquals("repos,issues,pull_requests", headers["X-MCP-Toolsets"])
        assertEquals("true", headers["X-MCP-Readonly"])
        assertEquals("true", headers["X-MCP-Lockdown"])
        assertEquals(null, preset.commonOptions.oauth)
    }
}
