package com.orchords.orchordsai.data.datastore.migration

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import com.orchords.orchordsai.data.datastore.SettingsStore
import com.orchords.orchordsai.data.security.SecondarySecretBackend
import com.orchords.orchordsai.data.security.SecondarySecretKey
import com.orchords.orchordsai.data.security.SecondarySecretStore
import com.orchords.orchordsai.utils.JsonInstant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Moves secondary credentials out of Settings DataStore into Android Keystore-backed storage. */
class PreferenceStoreV6Migration(
    private val context: Context,
) : DataMigration<Preferences> {
    private val secretStore by lazy { SecondarySecretStore(context) }

    override suspend fun shouldMigrate(currentData: Preferences): Boolean {
        val version = currentData[SettingsStore.VERSION]
        return version == null || version < 6
    }

    override suspend fun migrate(currentData: Preferences): Preferences {
        val prefs = currentData.toMutablePreferences()

        prefs[SettingsStore.NETWORK_SETTING]?.let { raw ->
            prefs[SettingsStore.NETWORK_SETTING] = migrateJsonSecrets(
                raw = raw,
                fields = mapOf(
                    "proxyUsername" to SecondarySecretKey.PROXY_USERNAME,
                    "proxyPassword" to SecondarySecretKey.PROXY_PASSWORD,
                ),
                backend = secretStore,
            )
        }
        prefs[SettingsStore.WEBDAV_CONFIG]?.let { raw ->
            prefs[SettingsStore.WEBDAV_CONFIG] = migrateJsonSecrets(
                raw = raw,
                fields = mapOf("password" to SecondarySecretKey.WEBDAV_PASSWORD),
                backend = secretStore,
            )
        }
        prefs[SettingsStore.S3_CONFIG]?.let { raw ->
            prefs[SettingsStore.S3_CONFIG] = migrateJsonSecrets(
                raw = raw,
                fields = mapOf("secretAccessKey" to SecondarySecretKey.S3_SECRET_ACCESS_KEY),
                backend = secretStore,
            )
        }
        prefs[SettingsStore.WEB_SERVER_ACCESS_PASSWORD]?.let { password ->
            persistIfPresent(SecondarySecretKey.WEB_SERVER_ACCESS_PASSWORD, password, secretStore)
            prefs.remove(SettingsStore.WEB_SERVER_ACCESS_PASSWORD)
        }

        prefs[SettingsStore.VERSION] = 6
        return prefs.toPreferences()
    }

    override suspend fun cleanUp() {}

    companion object {
        fun migrateJsonSecrets(
            raw: String,
            fields: Map<String, String>,
            backend: SecondarySecretBackend,
        ): String {
            val objectValue = JsonInstant.parseToJsonElement(raw) as? JsonObject
                ?: error("settings secret payload is not an object")
            val values = objectValue.toMutableMap()
            for ((jsonField, secretKey) in fields) {
                val value = (values[jsonField] as? JsonPrimitive)?.content.orEmpty()
                persistIfPresent(secretKey, value, backend)
                values.remove(jsonField)
            }
            return JsonInstant.encodeToString(JsonObject(values))
        }

        fun persistIfPresent(
            secretKey: String,
            value: String,
            backend: SecondarySecretBackend,
        ) {
            if (value.isBlank()) return
            check(backend.isAvailable()) { "Secondary secret store unavailable" }
            check(backend.put(secretKey, value)) { "Secondary secret store rejected $secretKey" }
        }
    }
}
