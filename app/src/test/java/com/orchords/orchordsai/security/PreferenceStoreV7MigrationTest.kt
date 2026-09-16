package com.orchords.orchordsai.security

import com.orchords.orchordsai.data.datastore.migration.PreferenceStoreV7Migration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferenceStoreV7MigrationTest {
    @Test
    fun `migration extracts oauth and MCP header secrets from legacy JSON`() {
        val store = FakeSecondarySecretStore()
        val raw = """
            [
              {
                "type":"streamable_http",
                "id":"00000000-0000-0000-0000-000000000007",
                "commonOptions":{
                  "enable":true,
                  "name":"GitHub",
                  "headers":[
                    {"first":"Authorization","second":"Bearer github_pat_secret"},
                    {"first":"X-Custom","second":"custom-secret-value"}
                  ],
                  "tools":[],
                  "oauth":{
                    "enabled":true,
                    "clientId":"public-client-id",
                    "clientSecret":"client-secret",
                    "accessToken":"access-token",
                    "refreshToken":"refresh-token",
                    "expiresAt":123
                  }
                },
                "url":"https://api.githubcopilot.com/mcp/"
              }
            ]
        """.trimIndent()

        val migrated = PreferenceStoreV7Migration.migrateMcpSecretsJson(raw, store)

        assertFalse(migrated.contains("github_pat_secret"))
        assertFalse(migrated.contains("custom-secret-value"))
        assertFalse(migrated.contains("client-secret"))
        assertFalse(migrated.contains("access-token"))
        assertFalse(migrated.contains("refresh-token"))
        assertTrue(migrated.contains("public-client-id"))
        assertTrue(migrated.contains("Authorization"))
        assertTrue(store.values.values.contains("Bearer github_pat_secret"))
        assertTrue(store.values.values.contains("custom-secret-value"))
        assertTrue(store.values.values.contains("client-secret"))
        assertTrue(store.values.values.contains("access-token"))
        assertTrue(store.values.values.contains("refresh-token"))
    }

    @Test(expected = IllegalStateException::class)
    fun `migration refuses to erase legacy MCP secrets when secure store is unavailable`() {
        PreferenceStoreV7Migration.migrateMcpSecretsJson(
            raw = """[{"type":"streamable_http","id":"00000000-0000-0000-0000-000000000007","commonOptions":{"headers":[{"first":"Authorization","second":"Bearer keep-me"}],"tools":[]},"url":"https://api.githubcopilot.com/mcp/"}]""",
            backend = FakeSecondarySecretStore(available = false),
        )
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
