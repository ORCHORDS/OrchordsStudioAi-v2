package com.orchords.orchordsai.data.datastore.migration

import androidx.datastore.preferences.core.preferencesOf
import com.orchords.orchordsai.data.datastore.SettingsStore
import com.orchords.orchordsai.utils.JsonInstant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferenceStoreV3MigrationWeatherTest {
    private val legacyDefaultAssistant = """
        [{
          "id":"0950e2dc-9bd5-4801-afa3-aa887aa36b4e",
          "name":"",
          "localTools":[{"type":"time_info"}]
        }]
    """.trimIndent()

    @Test
    fun `version 3 preferences migrate default weather once and finish at version 4`() = runTest {
        val migration = PreferenceStoreV3Migration()
        val input = preferencesOf(
            SettingsStore.VERSION to 3,
            SettingsStore.ASSISTANTS to legacyDefaultAssistant,
            SettingsStore.QUICK_MESSAGES to "[]",
        )

        assertTrue(migration.shouldMigrate(input))
        val migrated = migration.migrate(input)
        assertEquals(4, migrated[SettingsStore.VERSION])
        assertFalse(migration.shouldMigrate(migrated))

        val types = JsonInstant.parseToJsonElement(migrated[SettingsStore.ASSISTANTS]!!)
            .jsonArray.single().jsonObject["localTools"]!!.jsonArray
            .map { it.jsonObject["type"]!!.jsonPrimitive.content }
        assertEquals(listOf("time_info", "weather"), types)
    }

    @Test
    fun `legacy built in assistant weather transform is idempotent`() {
        val once = migrateDefaultAssistantWeather(legacyDefaultAssistant)
        val twice = migrateDefaultAssistantWeather(once)
        val types = JsonInstant.parseToJsonElement(twice)
            .jsonArray.single().jsonObject["localTools"]!!.jsonArray
            .map { it.jsonObject["type"]!!.jsonPrimitive.content }

        assertEquals(listOf("time_info", "weather"), types)
        assertEquals(
            JsonInstant.parseToJsonElement(once),
            JsonInstant.parseToJsonElement(twice),
        )
    }

    @Test
    fun `customized built in assistant tool selection is not broadened`() {
        val input = """
            [{
              "id":"0950e2dc-9bd5-4801-afa3-aa887aa36b4e",
              "localTools":[{"type":"time_info"},{"type":"calendar"}]
            }]
        """.trimIndent()

        val migrated = migrateDefaultAssistantWeather(input)
        val types = JsonInstant.parseToJsonElement(migrated)
            .jsonArray.single().jsonObject["localTools"]!!.jsonArray
            .map { it.jsonObject["type"]!!.jsonPrimitive.content }

        assertEquals(listOf("time_info", "calendar"), types)
        assertFalse(types.contains("weather"))
    }

    @Test
    fun `non default assistant is not modified`() {
        val input = """
            [{
              "id":"11111111-1111-1111-1111-111111111111",
              "localTools":[{"type":"time_info"}]
            }]
        """.trimIndent()

        val migrated = migrateDefaultAssistantWeather(input)
        val types = JsonInstant.parseToJsonElement(migrated)
            .jsonArray.single().jsonObject["localTools"]!!.jsonArray
            .map { it.jsonObject["type"]!!.jsonPrimitive.content }

        assertEquals(listOf("time_info"), types)
    }

    @Test
    fun `settings JSON import receives the same default weather migration`() {
        val input = """{"assistants":$legacyDefaultAssistant}"""
        val migrated = SettingsJsonMigrator.migrate(input)
        val types = JsonInstant.parseToJsonElement(migrated)
            .jsonObject["assistants"]!!.jsonArray.single().jsonObject["localTools"]!!.jsonArray
            .map { it.jsonObject["type"]!!.jsonPrimitive.content }

        assertEquals(listOf("time_info", "weather"), types)
    }

    @Test
    fun `malformed assistant JSON is preserved instead of being destroyed`() {
        val input = "not-json"
        assertEquals(input, migrateDefaultAssistantWeather(input))
    }
}
