package com.orchords.orchordsai.data.datastore.migration

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import com.orchords.orchordsai.data.datastore.SettingsStore
import com.orchords.orchordsai.data.security.ProviderCredentialStore
import com.orchords.orchordsai.utils.JsonInstant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * One-shot migration that moves plaintext provider `apiKey` values out of
 * the Settings DataStore JSON and into [ProviderCredentialStore] (issue
 * #428). After this migration, the `providers` value in DataStore only
 * contains non-secret fields.
 *
 * Idempotent: a second launch against an already-redacted DataStore is a
 * no-op because the codec only writes the encrypted store when an apiKey
 * is non-empty.
 *
 * The migration only runs once per installation (gated by
 * [SettingsStore.VERSION]). A subsequent install that already starts
 * clean (because users wiped data) is also a no-op.
 */
class PreferenceStoreV5Migration(
    private val context: Context,
) : DataMigration<Preferences> {
    private val credentialStore by lazy { ProviderCredentialStore(context) }

    override suspend fun shouldMigrate(currentData: Preferences): Boolean {
        val version = currentData[SettingsStore.VERSION]
        return version == null || version < 5
    }

    override suspend fun migrate(currentData: Preferences): Preferences {
        val prefs = currentData.toMutablePreferences()

        val raw = prefs[SettingsStore.PROVIDERS]
        if (!raw.isNullOrEmpty()) {
            // #428: parse raw JSON to recover the plaintext apiKey because
            // ProviderSetting.apiKey is @Transient and would be dropped by
            // the polymorphic decoder. Any failure must throw — DataStore
            // only commits the returned Preferences, so a swallowed
            // failure would leave the legacy key stranded and overwrite the
            // JSON on the next successful write.
            prefs[SettingsStore.PROVIDERS] = migrateProvidersJson(raw, credentialStore)
        }

        prefs[SettingsStore.VERSION] = 5
        return prefs.toPreferences()
    }

    override suspend fun cleanUp() {}

    companion object {
        /**
         * Pure-JVM migration helper exposed for unit tests. Returns the
         * post-migration JSON string. Throws on any failure so callers
         * (production migration and tests) can refuse to overwrite the
         * DataStore payload with a partial result.
         */
        fun migrateProvidersJson(
            raw: String,
            backend: com.orchords.orchordsai.data.security.ProviderSecretBackend,
        ): String {
            val rawProviders = JsonInstant.parseToJsonElement(raw).let { it as? JsonArray
                ?: error("providers is not an array") }
            val migrated = JsonArray(rawProviders.map { element ->
                val provider = element as? JsonObject ?: error("provider is not an object")
                val id = (provider["id"] as? JsonPrimitive)?.content
                    ?: error("provider id missing")
                val apiKey = (provider["apiKey"] as? JsonPrimitive)?.content ?: ""
                if (apiKey.isNotBlank()) {
                    check(backend.put(kotlin.uuid.Uuid.parse(id), apiKey)) {
                        "Encrypted credential store rejected provider $id"
                    }
                }
                JsonObject(provider.toMutableMap().apply { remove("apiKey") })
            })
            return JsonInstant.encodeToString(migrated)
        }
    }
}
