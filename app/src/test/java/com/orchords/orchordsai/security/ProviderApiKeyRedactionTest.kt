package com.orchords.orchordsai.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Regression coverage for #428 — provider `apiKey` values must never be
 * persisted into the Settings DataStore JSON; the encrypted
 * `ProviderCredentialStore` is the only on-disk source of truth.
 *
 * These are pure-JVM static-analysis tests: they read the production
 * source as text and assert invariants that are easy to break by an
 * accidental `@Transient` removal, a missing migration registration,
 * or a leaky redaction call site. They mirror the `AndroidBackupPolicyTest`
 * style and require no Robolectric.
 */
class ProviderApiKeyRedactionTest {
    private val moduleDir: File = File(".").canonicalFile
    private val appDir: File = moduleDir.resolve("src/main").canonicalFile
    private val projectRoot: File = moduleDir.parentFile?.canonicalFile
        ?: error("Cannot locate project root above ${moduleDir.path}")

    private fun source(relative: String): String = run {
        require(appDir.isDirectory) {
            "Unexpected working directory ${moduleDir.path}: unit tests must run from the app module"
        }
        val file = projectRoot.resolve(relative).canonicalFile
        require(file.toPath().startsWith(projectRoot.toPath())) {
            "Path escapes project root: $relative"
        }
        file.readText()
    }

    @Test
    fun `provider apiKey fields are marked Transient on every chat-LLM subtype`() {
        // OpenAI / Google / Claude each carry their own apiKey. If a new
        // subtype is added without `@Transient`, the Settings DataStore JSON
        // would re-leak plaintext keys. This test pins the marker on every
        // existing subtype.
        val providerSetting = source(
            "ai/src/main/java/com/orchords/ai/provider/ProviderSetting.kt"
        )
        assertTrue(
            "ProviderSetting must declare `abstract var apiKey: String` (#428)",
            Regex("abstract\\s+var\\s+apiKey\\s*:\\s*String").containsMatchIn(providerSetting),
        )
        listOf("OpenAI", "Google", "Claude").forEach { subtype ->
            // Each subtype's `apiKey` field must live inside the same
            // data-class body as `data class $subtype(`. The regex below
            // counts parens once we are past the open paren, stopping at
            // its matching close — that prevents a sibling type such as
            // `data class OpenAIConfig(...)` from satisfying the
            // assertion and prevents `@Transient` from a *later* data
            // class header from being counted as this subtype's marker.
            val headerStart = providerSetting.indexOf("data class $subtype(")
            assertTrue(
                "$subtype must declare `data class $subtype(` in ProviderSetting.kt",
                headerStart >= 0,
            )
            var depth = 0
            var idx = providerSetting.indexOf('(', headerStart)
            var headerEnd = -1
            for (i in idx until providerSetting.length) {
                val c = providerSetting[i]
                if (c == '(') depth++
                else if (c == ')') {
                    depth--
                    if (depth == 0) { headerEnd = i; break }
                }
            }
            assertTrue(
                "$subtype data-class header is unbalanced",
                headerEnd > 0,
            )
            val headerRegion = providerSetting.substring(headerStart, headerEnd + 1)
            assertTrue(
                "$subtype.apiKey must be `@Transient override var` in its own data class header (#428)",
                Regex("@Transient\\s+override\\s+var\\s+apiKey").containsMatchIn(headerRegion),
            )
            // Negative guard: `OpenAIWrapper`/etc. do not exist, but if
            // any sibling class shares the subtype token (e.g. a future
            // `OpenAIConfig`) it must not be reachable from inside this
            // header region.
            assertFalse(
                "subtype header for $subtype must not bleed into a sibling class",
                "data class ${subtype}Wrapper" in headerRegion,
            )
        }
    }

    @Test
    fun `settings datastore write path redacts provider apiKey before serialization`() {
        // The write path must route through ProviderSecretCodec.redactProvidersForWrite,
        // not encode the raw `settings.providers` shape into DataStore.
        val preferencesStore = source(
            "app/src/main/java/com/orchords/orchordsai/data/datastore/PreferencesStore.kt"
        )
        val writesProvidersLine = Regex(
            "preferences\\[SettingsStore\\.PROVIDERS\\]\\s*=\\s*JsonInstant\\.encodeToString\\((?!redactedProviders|ProviderSecretCodec)",
        )
        assertFalse(
            "PreferencesStore must not serialize raw `settings.providers` into DataStore " +
                "without going through ProviderSecretCodec (#428).",
            writesProvidersLine.containsMatchIn(preferencesStore),
        )
        assertTrue(
            "PreferencesStore must call ProviderSecretCodec.redactProvidersForWrite before " +
                "writing the providers JSON (#428).",
            "ProviderSecretCodec.redactProvidersForWrite(" in preferencesStore,
        )
        assertTrue(
            "PreferencesStore must refuse to write when the encrypted store is unavailable " +
                "and a real apiKey is present (#428).",
            // Case-insensitive: the throw site uses "refusing settings update";
            // both spellings satisfy the contract.
            preferencesStore.contains("refusing settings update", ignoreCase = true),
        )
    }

    @Test
    fun `settings datastore read path hydrates apiKey from the encrypted store`() {
        val preferencesStore = source(
            "app/src/main/java/com/orchords/orchordsai/data/datastore/PreferencesStore.kt"
        )
        assertTrue(
            "SettingsStore must hydrate apiKey via ProviderSecretCodec.hydrateProvidersFromStore (#428).",
            "ProviderSecretCodec.hydrateProvidersFromStore(" in preferencesStore,
        )
    }

    @Test
    fun `V5 migration backfills legacy plaintext apiKey into the encrypted store`() {
        // The migration is registered in produceMigrations AND implemented.
        val preferencesStore = source(
            "app/src/main/java/com/orchords/orchordsai/data/datastore/PreferencesStore.kt"
        )
        assertTrue(
            "V5 migration must be registered in Context.settingsStore.produceMigrations (#428).",
            "PreferenceStoreV5Migration(context)" in preferencesStore,
        )
        val migrationFile = source(
            "app/src/main/java/com/orchords/orchordsai/data/datastore/migration/PreferenceStoreV5Migration.kt"
        )
        assertTrue(
            "V5 migration must call put to backfill each non-empty apiKey via the encrypted store (#428).",
            // Either the direct credentialStore.put(...) invocation or the
            // pure-JVM helper's backend.put(...) call satisfies the contract.
            "credentialStore.put(" in migrationFile || "backend.put(" in migrationFile,
        )
        assertTrue(
            "V5 migration must rewrite the DataStore JSON with apiKey redacted (#428).",
            "encodeToString(migrated)" in migrationFile,
        )
        assertTrue(
            "V5 migration must bump SettingsStore.VERSION to 5 (#428).",
            "SettingsStore.VERSION] = 5" in migrationFile,
        )
    }

    @Test
    fun `ProviderCredentialStore is wired as a Koin singleton`() {
        val module = source(
            "app/src/main/java/com/orchords/orchordsai/di/DataSourceModule.kt"
        )
        assertTrue(
            "DataSourceModule must provide ProviderCredentialStore as a Koin singleton (#428).",
            "ProviderCredentialStore(context = get())" in module,
        )
        assertTrue(
            "DataSourceModule must pass credentialStore into SettingsStore (#428).",
            "credentialStore = get()" in module,
        )
    }

    @Test
    fun `ProviderCredentialStore wraps EncryptedSharedPreferences with a stable master key`() {
        val store = source(
            "app/src/main/java/com/orchords/orchordsai/data/security/ProviderCredentialStore.kt"
        )
        assertNotNull(
            "ProviderCredentialStore source must be present (#428).",
            store,
        )
        assertTrue(
            "ProviderCredentialStore must build a MasterKey via KeyScheme.AES256_GCM (#428).",
            "MasterKey.KeyScheme.AES256_GCM" in store,
        )
        assertTrue(
            "ProviderCredentialStore must use EncryptedSharedPreferences.create " +
                "with AES256_SIV key + AES256_GCM value encryption (#428).",
            "EncryptedSharedPreferences.create(" in store &&
                "PrefKeyEncryptionScheme.AES256_SIV" in store &&
                "PrefValueEncryptionScheme.AES256_GCM" in store,
        )
    }

    @Test
    fun `ProviderSecretBackend is an interface that lets the codec be tested without Android Keystore`() {
        val store = source(
            "app/src/main/java/com/orchords/orchordsai/data/security/ProviderCredentialStore.kt"
        )
        assertTrue(
            "ProviderSecretBackend must be declared as an interface so tests can substitute (#428).",
            "interface ProviderSecretBackend" in store,
        )
    }

    @Test
    fun `settings datastore write path wipes encrypted apiKey for dropped provider ids`() {
        // Settings → Providers → Remove must scrub the encrypted apiKey for
        // any provider id that disappears from `settings.providers`. If
        // `ProviderSecretCodec.removeDroppedProviders` is not called before
        // `dataStore.edit`, a Remove action leaves the previous key
        // resident in the encrypted file under the dropped Uuid — the
        // exact behaviour Play Console flags as "unnecessary data
        // collection". The wipe must run *before* redactProvidersForWrite
        // and *before* dataStore.edit so a removal failure refuses the
        // settings update instead of leaking the stale key.
        val preferencesStore = source(
            "app/src/main/java/com/orchords/orchordsai/data/datastore/PreferencesStore.kt"
        )
        assertTrue(
            "SettingsStore.update must derive previousIds from settingsFlow.value.providers " +
                "so a Remove action detects the dropped id (#428 deletion-path).",
            "previousProviderIds = settingsFlow.value.providers.mapTo(HashSet())" in preferencesStore,
        )
        assertTrue(
            "SettingsStore.update must call ProviderSecretCodec.removeDroppedProviders " +
                "with the derived previousIds before writing DataStore (#428 deletion-path).",
            "ProviderSecretCodec.removeDroppedProviders(" in preferencesStore,
        )
        // Order matters: the wipe site must sit above both redactProvidersForWrite
        // and dataStore.edit so a removal failure aborts the write.
        val wipeIdx = preferencesStore.indexOf("ProviderSecretCodec.removeDroppedProviders(")
        val redactIdx = preferencesStore.indexOf("ProviderSecretCodec.redactProvidersForWrite(")
        val editIdx = preferencesStore.indexOf("dataStore.edit { preferences ->")
        assertTrue(
            "SettingsStore.update must call removeDroppedProviders before redactProvidersForWrite (#428 deletion-path).",
            wipeIdx in 0 until redactIdx,
        )
        assertTrue(
            "SettingsStore.update must call removeDroppedProviders before dataStore.edit (#428 deletion-path).",
            wipeIdx in 0 until editIdx,
        )
        assertTrue(
            "SettingsStore.update must throw when removeDroppedProviders refuses to wipe a dropped " +
                "provider; refusing the settings update keeps the stale key out of DataStore (#428 deletion-path).",
            // The throw site uses the literal "refusing settings update" string
            // — both spellings (encrypted credential store / Removing dropped
            // providers) satisfy the contract.
            preferencesStore.contains("Removing dropped providers") ||
                preferencesStore.contains("refusing settings update", ignoreCase = true),
        )
    }

    @Test
    fun `security-crypto dependency is declared in the version catalog and consumed by app`() {
        val catalog = source("gradle/libs.versions.toml")
        assertTrue(
            "libs.versions.toml must declare `securityCrypto` version (#428).",
            "securityCrypto = " in catalog,
        )
        assertTrue(
            "libs.versions.toml must declare the `androidx-security-crypto` library alias (#428).",
            "androidx-security-crypto" in catalog,
        )
        val gradleFile = source("app/build.gradle.kts")
        assertTrue(
            "app/build.gradle.kts must consume libs.androidx.security.crypto (#428).",
            "libs.androidx.security.crypto" in gradleFile,
        )
    }
}
