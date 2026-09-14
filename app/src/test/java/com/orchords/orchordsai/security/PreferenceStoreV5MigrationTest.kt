package com.orchords.orchordsai.security

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.orchords.orchordsai.data.datastore.SettingsStore
import com.orchords.orchordsai.data.datastore.migration.PreferenceStoreV5Migration
import com.orchords.orchordsai.data.security.ProviderSecretBackend
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

/**
 * Pure-JVM regression coverage for the V5 migration that moves provider
 * `apiKey` values out of Settings DataStore (#428). Because the migration
 * touches `android.content.Context` to look up the encrypted store, we
 * exercise the codec half directly via a `ProviderSecretBackend` fake and
 * assert against the in-memory JSON it writes back into the Preferences
 * object.
 *
 * The original bug: the migration ran through the `@Transient`-aware
 * `ProviderSetting` decoder, which silently dropped `apiKey`. The codec
 * fix here mirrors the production path: parse the raw JSON array,
 * extract the plaintext key, store it in the encrypted backend, and
 * re-emit each provider without the `apiKey` key.
 */
class PreferenceStoreV5MigrationTest {

    @Test
    fun `migration strips apiKey from the persisted providers JSON`() = runBlocking {
        val openAiId = Uuid.random()
        val googleId = Uuid.random()
        val legacy = JsonArray(
            listOf(
                providerJson(openAiId, "OpenAI", "sk-openai"),
                providerJson(googleId, "Google", "sk-google"),
            )
        ).toString()

        val store = RecordingBackend()
        val encoded = PreferenceStoreV5Migration.migrateProvidersJson(legacy, store)
        val parsed = Json.parseToJsonElement(encoded) as JsonArray
        assertEquals(2, parsed.size)
        parsed.forEach { node ->
            val obj = node as JsonObject
            assertFalse("apiKey must be removed from the persisted providers JSON", obj.containsKey("apiKey"))
        }
        assertEquals("Encrypted backend must receive every legacy apiKey", setOf(openAiId, googleId), store.stored)
        assertEquals(
            mapOf(openAiId to "sk-openai", googleId to "sk-google"),
            store.records,
        )
    }

    @Test
    fun `migration refuses to overwrite legacy apiKey when the encrypted store rejects the write`() {
        val id = Uuid.random()
        val legacy = JsonArray(listOf(providerJson(id, "OpenAI", "sk-do-not-lose"))).toString()
        val store = RejectingBackend()

        var error: Throwable? = null
        try {
            PreferenceStoreV5Migration.migrateProvidersJson(legacy, store)
        } catch (t: Throwable) {
            error = t
        }
        assertNotNull(
            "When the encrypted store refuses the write, the migration must throw " +
                "so DataStore does not overwrite the legacy apiKey with an empty value.",
            error,
        )
    }

    @Test
    fun `migration is a no-op when the persisted providers JSON has no apiKey`() {
        val id = Uuid.random()
        val noKeys = JsonArray(listOf(providerJson(id, "OpenAI", null))).toString()
        val store = RecordingBackend()
        val encoded = PreferenceStoreV5Migration.migrateProvidersJson(noKeys, store)
        assertEquals(0, store.stored.size)
        // The encoded JSON still has the provider, just without an apiKey.
        val parsed = Json.parseToJsonElement(encoded) as JsonArray
        assertEquals(1, parsed.size)
        assertFalse((parsed[0] as JsonObject).containsKey("apiKey"))
    }

    private fun providerJson(id: Uuid, subtype: String, apiKey: String?): JsonObject {
        val members = LinkedHashMap<String, kotlinx.serialization.json.JsonElement>()
        members["id"] = JsonPrimitive(id.toString())
        members["type"] = JsonPrimitive(subtype)
        members["name"] = JsonPrimitive(subtype)
        members["enabled"] = JsonPrimitive(true)
        members["models"] = JsonArray(emptyList())
        members["balanceOption"] = JsonObject(emptyMap())
        if (apiKey != null) members["apiKey"] = JsonPrimitive(apiKey)
        return JsonObject(members)
    }

    private class RecordingBackend : ProviderSecretBackend {
        val records = mutableMapOf<Uuid, String>()
        val stored: Set<Uuid> get() = records.keys.toSet()
        override fun isAvailable(): Boolean = true
        override fun get(providerId: Uuid): String? = records[providerId]
        override fun put(providerId: Uuid, apiKey: String): Boolean {
            records[providerId] = apiKey
            return true
        }

        override fun remove(providerId: Uuid): Boolean {
            records.remove(providerId)
            return true
        }

        override fun storedProviderIds(): Set<Uuid> = records.keys.toSet()
    }

    private class RejectingBackend : ProviderSecretBackend {
        override fun isAvailable(): Boolean = true
        override fun get(providerId: Uuid): String? = null
        override fun put(providerId: Uuid, apiKey: String): Boolean = false
        override fun remove(providerId: Uuid): Boolean = false
        override fun storedProviderIds(): Set<Uuid> = emptySet()
    }
}
