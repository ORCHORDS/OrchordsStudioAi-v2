package com.orchords.orchordsai.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SecondarySecretPersistencePolicyTest {
    private fun source(relative: String): String = File(relative).readText()

    @Test
    fun `settings secrets are transient and routed through the secondary store`() {
        val preferences = source("src/main/java/com/orchords/orchordsai/data/datastore/PreferencesStore.kt")
        val s3 = source("src/main/java/com/orchords/orchordsai/data/sync/s3/S3Config.kt")

        assertTrue("proxy username must be transient", Regex("@Transient\\s+val proxyUsername").containsMatchIn(preferences))
        assertTrue("proxy password must be transient", Regex("@Transient\\s+val proxyPassword").containsMatchIn(preferences))
        assertTrue("WebDAV password must be transient", Regex("@Transient\\s+val password").containsMatchIn(preferences))
        assertTrue("web server password must be transient", Regex("@Transient\\s+val webServerAccessPassword").containsMatchIn(preferences))
        assertTrue("S3 secret access key must be transient", Regex("@Transient\\s+val secretAccessKey").containsMatchIn(s3))

        val redact = preferences.indexOf("SecondarySecretCodec.redactSettingsForWrite")
        val edit = preferences.indexOf("dataStore.edit { preferences ->")
        assertTrue("secondary secrets must be persisted/redacted before DataStore edit", redact >= 0 && redact < edit)
        assertTrue("V6 migration must be installed", "PreferenceStoreV6Migration(context)" in preferences)
        assertTrue("legacy web server password key must be removed rather than rewritten", "preferences.remove(WEB_SERVER_ACCESS_PASSWORD)" in preferences)
    }

    @Test
    fun `new secondary store uses platform Android Keystore rather than deprecated encrypted preferences`() {
        val store = source("src/main/java/com/orchords/orchordsai/data/security/SecondarySecretStore.kt")
        assertTrue("secondary store must use AndroidKeyStore", "AndroidKeyStore" in store)
        assertTrue("secondary store must use AES GCM", "AES/GCM/NoPadding" in store)
        assertFalse(
            "new storage must not extend deprecated EncryptedSharedPreferences",
            "EncryptedSharedPreferences" in store,
        )
    }
}
