package com.orchords.orchordsai.data.ai.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudflareMcpPresetTest {
    @Test
    fun `Cloudflare preset uses official endpoint and OAuth-first defaults`() {
        val preset = cloudflareMcpPreset()

        assertTrue(preset is McpServerConfig.StreamableHTTPServer)
        assertEquals("Cloudflare", preset.commonOptions.name)
        assertEquals(CLOUDFLARE_MCP_REMOTE_ENDPOINT, preset.serverUrl)
        assertFalse(preset.commonOptions.headers.any { it.first.equals("Authorization", ignoreCase = true) })
        assertEquals(null, preset.commonOptions.oauth)
        assertTrue(cloudflareNewToolsNeedApproval(preset))
    }

    @Test
    fun `Cloudflare token helper owns Bearer formatting and disconnect clears auth only`() {
        val preset = cloudflareMcpPreset()
        val withToken = preset.withCloudflareMcpApiToken(" test-token ")

        assertEquals("test-token", cloudflareMcpApiToken(withToken))
        assertTrue(
            withToken.commonOptions.headers.any {
                it.first == "Authorization" && it.second == "Bearer test-token"
            }
        )
        assertTrue(isCloudflareManagedHeader("authorization"))

        val disconnected = withToken.withCloudflareMcpDisconnected()
        assertEquals(CLOUDFLARE_MCP_REMOTE_ENDPOINT, disconnected.serverUrl)
        assertEquals("Cloudflare", disconnected.commonOptions.name)
        assertEquals("", cloudflareMcpApiToken(disconnected))
        assertFalse(disconnected.commonOptions.headers.any { it.first.equals("Authorization", ignoreCase = true) })
        assertEquals(null, disconnected.commonOptions.oauth)
    }

    @Test
    fun `Cloudflare endpoint recognition rejects lookalikes`() {
        assertTrue(isOfficialCloudflareMcpRemote(cloudflareMcpPreset()))
        assertFalse(
            isOfficialCloudflareMcpRemote(
                McpServerConfig.StreamableHTTPServer(url = "https://mcp.cloudflare.com.evil.example/mcp")
            )
        )
        assertFalse(
            isOfficialCloudflareMcpRemote(
                McpServerConfig.StreamableHTTPServer(url = "http://mcp.cloudflare.com/mcp")
            )
        )
    }
}
