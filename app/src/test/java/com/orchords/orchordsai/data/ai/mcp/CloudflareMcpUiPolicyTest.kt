package com.orchords.orchordsai.data.ai.mcp

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudflareMcpUiPolicyTest {
    private val settingsSource = File(
        "src/main/java/com/orchords/orchordsai/ui/pages/setting/SettingMcpPage.kt"
    ).readText()

    @Test
    fun `Cloudflare is a first-class OAuth-first MCP option`() {
        assertTrue(settingsSource.contains("creationState.open(cloudflareMcpPreset())"))
        assertTrue(settingsSource.contains("Text(\"Cloudflare\")"))
        assertTrue(settingsSource.contains("Connect with Cloudflare OAuth"))
        assertTrue(settingsSource.contains("mcpManager.startAuthorization(item, context)"))
    }

    @Test
    fun `Cloudflare optional token uses dedicated managed field`() {
        assertTrue(settingsSource.contains("cloudflareMcpApiToken(config)"))
        assertTrue(settingsSource.contains("config.withCloudflareMcpApiToken(it)"))
        assertTrue(settingsSource.contains("isCloudflareManagedHeader(header.first)"))
        assertTrue(settingsSource.contains("withCloudflareMcpDisconnected()"))
        assertFalse(cloudflareMcpPreset().commonOptions.headers.any {
            it.first.equals("Authorization", ignoreCase = true)
        })
    }

    @Test
    fun `Cloudflare tools default approval required`() {
        assertTrue(cloudflareNewToolsNeedApproval(cloudflareMcpPreset()))
        assertTrue(githubNewToolsNeedApproval(cloudflareMcpPreset()))
    }
}
