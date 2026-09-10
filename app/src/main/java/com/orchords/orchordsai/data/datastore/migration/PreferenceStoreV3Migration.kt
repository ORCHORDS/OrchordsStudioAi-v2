package com.orchords.orchordsai.data.datastore.migration

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import com.orchords.orchordsai.data.datastore.SettingsStore
import com.orchords.orchordsai.utils.JsonInstant
import kotlin.uuid.Uuid

private const val CURRENT_PREFERENCE_VERSION = 4
private const val DEFAULT_ASSISTANT_ID_VALUE = "0950e2dc-9bd5-4801-afa3-aa887aa36b4e"
private const val TIME_INFO_TOOL_TYPE = "time_info"
private const val WEATHER_TOOL_TYPE = "weather"

class PreferenceStoreV3Migration : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean {
        val version = currentData[SettingsStore.VERSION]
        return version == null || version < CURRENT_PREFERENCE_VERSION
    }

    override suspend fun migrate(currentData: Preferences): Preferences {
        val prefs = currentData.toMutablePreferences()

        val (quickMessageMigratedAssistants, extractedQuickMessages) =
            migrateAssistantsQuickMessages(prefs[SettingsStore.ASSISTANTS] ?: "[]")
        val migratedAssistants = migrateDefaultAssistantWeather(quickMessageMigratedAssistants)

        prefs[SettingsStore.ASSISTANTS] = migratedAssistants

        val existingQuickMessages = prefs[SettingsStore.QUICK_MESSAGES]?.let { json ->
            runCatching<JsonArray> {
                JsonInstant.parseToJsonElement(json).jsonArray
            }.getOrElse { JsonArray(emptyList()) }
        } ?: JsonArray(emptyList())

        val existingIds = existingQuickMessages.mapNotNull {
            (it as? JsonObject)?.get("id")?.toString()?.trim('"')
        }.toSet()

        val merged = JsonArray(
            existingQuickMessages + extractedQuickMessages.filter { element ->
                val id = (element as? JsonObject)?.get("id")?.toString()?.trim('"')
                id != null && id !in existingIds
            }
        )

        prefs[SettingsStore.QUICK_MESSAGES] = JsonInstant.encodeToString(merged)
        prefs[SettingsStore.VERSION] = CURRENT_PREFERENCE_VERSION

        return prefs.toPreferences()
    }

    override suspend fun cleanUp() {}
}

/**
 * Upgrade only the untouched built-in assistant tool default from Time Info to
 * Time Info + Weather. Any custom/non-default tool selection is preserved.
 * The transform is idempotent because DataStore may execute migrations more
 * than once when a later migration or disk write fails.
 */
internal fun migrateDefaultAssistantWeather(assistantsJson: String): String {
    return runCatching {
        val root = JsonInstant.parseToJsonElement(assistantsJson) as? JsonArray
            ?: return@runCatching assistantsJson
        val migrated = JsonArray(root.map { element ->
            val assistant = element as? JsonObject ?: return@map element
            val id = assistant["id"]?.jsonPrimitive?.contentOrNull
            if (id != DEFAULT_ASSISTANT_ID_VALUE) return@map element

            val tools = assistant["localTools"] as? JsonArray ?: return@map element
            if (tools.size != 1) return@map element
            val onlyTool = tools.single() as? JsonObject ?: return@map element
            val onlyType = onlyTool["type"]?.jsonPrimitive?.contentOrNull
            if (onlyType != TIME_INFO_TOOL_TYPE) return@map element

            val weather = JsonObject(mapOf("type" to JsonPrimitive(WEATHER_TOOL_TYPE)))
            JsonObject(assistant.toMutableMap().apply {
                put("localTools", JsonArray(tools + weather))
            })
        })
        JsonInstant.encodeToString(migrated)
    }.getOrElse { assistantsJson }
}

/**
 */
internal fun migrateAssistantsQuickMessages(
    assistantsJson: String
): Pair<String, JsonArray> {
    return runCatching {
        val root = JsonInstant.parseToJsonElement(assistantsJson) as? JsonArray
            ?: return@runCatching assistantsJson to JsonArray(emptyList())

        val allQuickMessages = mutableListOf<JsonElement>()

        val migratedAssistants = JsonArray(
            root.map { assistant ->
                val assistantObj = assistant as? JsonObject
                    ?: return@map assistant

                val oldQuickMessages = assistantObj["quickMessages"] as? JsonArray
                    ?: return@map assistant

                val messagesWithIds = oldQuickMessages.map { element ->
                    val obj = element as? JsonObject ?: return@map element
                    val newId = Uuid.random().toString()
                    JsonObject(obj.toMutableMap().apply {
                        put("id", JsonPrimitive(newId))
                    })
                }

                allQuickMessages.addAll(messagesWithIds)

                val ids = JsonArray(
                    messagesWithIds.mapNotNull { element ->
                        (element as? JsonObject)?.get("id")
                    }
                )

                JsonObject(
                    assistantObj.toMutableMap().apply {
                        remove("quickMessages")
                        put("quickMessageIds", ids)
                    }
                )
            }
        )

        JsonInstant.encodeToString(migratedAssistants) to JsonArray(allQuickMessages)
    }.getOrElse { assistantsJson to JsonArray(emptyList()) }
}
