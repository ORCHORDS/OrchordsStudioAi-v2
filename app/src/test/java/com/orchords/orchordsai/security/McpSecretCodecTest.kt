package com.orchords.orchordsai.security

import com.orchords.orchordsai.data.ai.mcp.McpCommonOptions
import com.orchords.orchordsai.data.ai.mcp.McpOAuthState
import com.orchords.orchordsai.data.ai.mcp.McpServerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class McpSecretCodecTest {
    @Test
    fun `redaction stores oauth and header secrets and returns credential free config`() {
        val store = FakeSecondarySecretStore()
        val server = McpServerConfig.StreamableHTTPServer(
            commonOptions = McpCommonOptions(
                name = "GitHub",
                headers = listOf(
                    "Authorization" to "Bearer github_pat_secret",
                    "X-Custom" to "custom-secret-value",
                ),
                oauth = McpOAuthState(
                    enabled = true,
                    clientId = "public-client-id",
                    clientSecret = "client-secret",
                    accessToken = "access-token",
                    refreshToken = "refresh-token",
                ),
            ),
            url = "https://api.githubcopilot.com/mcp/",
        )

        val redacted = requireNotNull(
            McpSecretCodec.redactServersForWrite(
                previousServers = emptyList(),
                servers = listOf(server),
                store = store,
            )
        ).single()

        assertEquals("", redacted.commonOptions.headers[0].second)
        assertEquals("", redacted.commonOptions.headers[1].second)
        assertNull(redacted.commonOptions.oauth?.clientSecret)
        assertNull(redacted.commonOptions.oauth?.accessToken)
        assertNull(redacted.commonOptions.oauth?.refreshToken)

        assertEquals("client-secret", store.values[McpSecretKey.oauthClientSecret(server.id)])
        assertEquals("access-token", store.values[McpSecretKey.oauthAccessToken(server.id)])
        assertEquals("refresh-token", store.values[McpSecretKey.oauthRefreshToken(server.id)])
        assertEquals(
            "Bearer github_pat_secret",
            store.values[McpSecretKey.headerValue(server.id, 0, "Authorization")],
        )
        assertEquals(
            "custom-secret-value",
            store.values[McpSecretKey.headerValue(server.id, 1, "X-Custom")],
        )
    }

    @Test
    fun `hydrate restores secrets only at runtime`() {
        val store = FakeSecondarySecretStore()
        val server = McpServerConfig.StreamableHTTPServer(
            commonOptions = McpCommonOptions(
                name = "GitHub",
                headers = listOf("Authorization" to ""),
                oauth = McpOAuthState(enabled = true, clientId = "public-client-id"),
            ),
            url = "https://api.githubcopilot.com/mcp/",
        )
        store.values[McpSecretKey.oauthAccessToken(server.id)] = "access-token"
        store.values[McpSecretKey.oauthRefreshToken(server.id)] = "refresh-token"
        store.values[McpSecretKey.headerValue(server.id, 0, "Authorization")] = "Bearer github_pat_secret"

        val hydrated = McpSecretCodec.hydrateServersFromStore(listOf(server), store).single()

        assertEquals("access-token", hydrated.commonOptions.oauth?.accessToken)
        assertEquals("refresh-token", hydrated.commonOptions.oauth?.refreshToken)
        assertEquals("Bearer github_pat_secret", hydrated.commonOptions.headers.single().second)
    }

    @Test
    fun `dropping a server removes its stored MCP secrets`() {
        val store = FakeSecondarySecretStore()
        val server = McpServerConfig.StreamableHTTPServer(
            commonOptions = McpCommonOptions(
                name = "GitHub",
                headers = listOf("Authorization" to "Bearer secret"),
                oauth = McpOAuthState(enabled = true, accessToken = "access-token"),
            ),
        )
        requireNotNull(McpSecretCodec.redactServersForWrite(emptyList(), listOf(server), store))
        assertTrue(store.values.isNotEmpty())

        val ok = McpSecretCodec.redactServersForWrite(listOf(server), emptyList(), store)

        assertTrue(ok != null)
        assertFalse(store.values.keys.any { it.startsWith("mcp.${server.id}.") })
    }

    @Test
    fun `secret bearing MCP update fails closed when secure store is unavailable`() {
        val store = FakeSecondarySecretStore(available = false)
        val server = McpServerConfig.StreamableHTTPServer(
            commonOptions = McpCommonOptions(headers = listOf("Authorization" to "Bearer secret")),
        )

        val redacted = McpSecretCodec.redactServersForWrite(emptyList(), listOf(server), store)

        assertNull(redacted)
    }

    private class FakeSecondarySecretStore(
        private val available: Boolean = true,
    ) : SecondarySecretBackend {
        val values = linkedMapOf<String, String>()

        override fun isAvailable(): Boolean = available
        override fun get(name: String): String? = values[name]
        override fun put(name: String, value: String): Boolean {
            if (!available) return false
            values[name] = value
            return true
        }
        override fun remove(name: String): Boolean {
            if (!available) return false
            values.remove(name)
            return true
        }
        override fun replace(values: Map<String, String>, removals: Set<String>): Boolean {
            if (!available) return false
            removals.forEach(this.values::remove)
            this.values.putAll(values)
            return true
        }
    }
}
