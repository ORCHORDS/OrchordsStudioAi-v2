package com.orchords.orchordsai.data.datastore.migration

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import com.orchords.orchordsai.data.datastore.SettingsStore
import com.orchords.orchordsai.data.security.McpSecretKey
import com.orchords.orchordsai.data.security.SecondarySecretBackend
import com.orchords.orchordsai.data.security.SecondarySecretStore
import com.orchords.orchordsai.utils.JsonInstant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.uuid.Uuid

/** Moves legacy MCP OAuth/header credentials out of Settings DataStore. */
class PreferenceStoreV7Migration(
    private val context: Context,
) : DataMigration<Preferences> {
    private val secretStore by lazy { SecondarySecretStore(context) }

    override suspend fun shouldMigrate(currentData: Preferences): Boolean {
        val version = currentData[SettingsStore.VERSION]
        return version == null || version < 7
    }

    override suspend fun migrate(currentData: Preferences): Preferences {
        val prefs = currentData.toMutablePreferences()
        prefs[SettingsStore.MCP_SERVERS]?.let { raw ->
            prefs[SettingsStore.MCP_SERVERS] = migrateMcpSecretsJson(raw, secretStore)
        }
        prefs[SettingsStore.VERSION] = 7
        return prefs.toPreferences()
    }

    override suspend fun cleanUp() {}

    companion object {
        fun migrateMcpSecretsJson(
            raw: String,
            backend: SecondarySecretBackend,
        ): String {
            val root = JsonInstant.parseToJsonElement(raw) as? JsonArray
                ?: error("MCP server payload is not an array")
            val values = linkedMapOf<String, String>()

            val migrated = JsonArray(root.map { element ->
                val server = element as? JsonObject ?: error("MCP server is not an object")
                val serverId = (server["id"] as? JsonPrimitive)?.content
                    ?.let { Uuid.parse(it) }
                    ?: error("MCP server id missing")
                val common = server["commonOptions"] as? JsonObject ?: return@map server
                val commonValues = common.toMutableMap()

                (common["oauth"] as? JsonObject)?.let { oauth ->
                    val oauthValues = oauth.toMutableMap()
                    moveJsonSecret(oauthValues, "clientSecret", McpSecretKey.oauthClientSecret(serverId), values)
                    moveJsonSecret(oauthValues, "accessToken", McpSecretKey.oauthAccessToken(serverId), values)
                    moveJsonSecret(oauthValues, "refreshToken", McpSecretKey.oauthRefreshToken(serverId), values)
                    commonValues["oauth"] = JsonObject(oauthValues)
                }

                (common["headers"] as? JsonArray)?.let { headers ->
                    commonValues["headers"] = JsonArray(headers.mapIndexed { index, header ->
                        redactHeaderValue(serverId, index, header, values)
                    })
                }

                JsonObject(server.toMutableMap().apply {
                    put("commonOptions", JsonObject(commonValues))
                })
            })

            if (values.isNotEmpty()) {
                check(backend.isAvailable()) { "Secondary secret store unavailable" }
                check(backend.replace(values, emptySet())) { "Secondary secret store rejected MCP migration" }
            }
            return JsonInstant.encodeToString(migrated)
        }

        private fun moveJsonSecret(
            objectValues: MutableMap<String, JsonElement>,
            jsonField: String,
            secretKey: String,
            destination: MutableMap<String, String>,
        ) {
            val value = (objectValues[jsonField] as? JsonPrimitive)?.content.orEmpty()
            if (value.isNotBlank()) destination[secretKey] = value
            objectValues.remove(jsonField)
        }

        private fun redactHeaderValue(
            serverId: Uuid,
            index: Int,
            header: JsonElement,
            destination: MutableMap<String, String>,
        ): JsonElement = when (header) {
            is JsonObject -> {
                val name = (header["first"] as? JsonPrimitive)?.content.orEmpty()
                val value = (header["second"] as? JsonPrimitive)?.content.orEmpty()
                if (value.isNotBlank()) {
                    destination[McpSecretKey.headerValue(serverId, index, name)] = value
                }
                JsonObject(header.toMutableMap().apply { put("second", JsonPrimitive("")) })
            }
            is JsonArray -> {
                val name = (header.getOrNull(0) as? JsonPrimitive)?.content.orEmpty()
                val value = (header.getOrNull(1) as? JsonPrimitive)?.content.orEmpty()
                if (value.isNotBlank()) {
                    destination[McpSecretKey.headerValue(serverId, index, name)] = value
                }
                JsonArray(header.mapIndexed { itemIndex, item ->
                    if (itemIndex == 1) JsonPrimitive("") else item
                })
            }
            else -> header
        }
    }
}
