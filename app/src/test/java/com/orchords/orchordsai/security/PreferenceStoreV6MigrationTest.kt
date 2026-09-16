package com.orchords.orchordsai.security

import com.orchords.orchordsai.data.datastore.migration.PreferenceStoreV6Migration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferenceStoreV6MigrationTest {
    @Test
    fun `migration moves mapped JSON secrets into backend and strips plaintext`() {
        val store = FakeSecondarySecretStore()
        val migrated = PreferenceStoreV6Migration.migrateJsonSecrets(
            raw = """{"userAgent":"Orchords","proxyUrl":"http://localhost:8080","proxyUsername":"alice","proxyPassword":"secret"}""",
            fields = mapOf(
                "proxyUsername" to SecondarySecretKey.PROXY_USERNAME,
                "proxyPassword" to SecondarySecretKey.PROXY_PASSWORD,
            ),
            backend = store,
        )

        assertTrue(store.values[SecondarySecretKey.PROXY_USERNAME] == "alice")
        assertTrue(store.values[SecondarySecretKey.PROXY_PASSWORD] == "secret")
        assertFalse("plaintext username must be removed", migrated.contains("alice"))
        assertFalse("plaintext password must be removed", migrated.contains("secret"))
        assertTrue("non-secret proxy URL must remain", migrated.contains("http://localhost:8080"))
    }

    @Test(expected = IllegalStateException::class)
    fun `migration refuses to strip a nonblank secret when backend is unavailable`() {
        PreferenceStoreV6Migration.persistIfPresent(
            secretKey = SecondarySecretKey.WEBDAV_PASSWORD,
            value = "must-not-be-lost",
            backend = FakeSecondarySecretStore(available = false),
        )
    }

    @Test
    fun `blank values require no secret store`() {
        PreferenceStoreV6Migration.persistIfPresent(
            secretKey = SecondarySecretKey.WEBDAV_PASSWORD,
            value = "",
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
            values.remove(name)
            return available
        }
    }
}
