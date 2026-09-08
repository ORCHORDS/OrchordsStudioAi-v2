package com.orchords.orchordsai.data.datastore

import com.orchords.ai.provider.BuiltInTools
import com.orchords.ai.provider.Model
import com.orchords.ai.provider.ModelAbility
import com.orchords.ai.provider.ProviderSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultProvidersTest {
    @Test
    fun `default providers contain only the OrchordsAI provider`() {
        assertEquals(1, DEFAULT_PROVIDERS.size)
        val provider = DEFAULT_PROVIDERS.single()
        assertTrue(provider is ProviderSetting.OpenAI)
        provider as ProviderSetting.OpenAI
        assertEquals("OrchordsAI", provider.name)
        assertEquals(ORCHORDS_GATEWAY_BASE_URL, provider.baseUrl)
        assertEquals("", provider.apiKey)
        assertTrue(provider.builtIn)
        assertTrue(provider.enabled)
    }

    @Test
    fun `OrchordsAI provider exposes only tool-capable oai-1_0`() {
        val provider = DEFAULT_PROVIDERS.single() as ProviderSetting.OpenAI
        assertEquals(1, provider.models.size)
        val model = provider.models.single()
        assertEquals(ORCHORDS_MODEL_ID, model.modelId)
        assertEquals("Orchords oai-1.0", model.displayName)
        assertEquals(listOf(ModelAbility.TOOL), model.abilities)
        assertFalse(BuiltInTools.Search in model.tools)
    }

    @Test
    fun `recommended providers list is empty`() {
        assertTrue(RECOMMENDED_PROVIDERS.isEmpty())
    }

    @Test
    fun `default providers do not embed an api key`() {
        val provider = DEFAULT_PROVIDERS.single() as ProviderSetting.OpenAI
        assertFalse(provider.apiKey.isNotEmpty())
    }

    @Test
    fun `default providers serialize and round trip`() {
        val first = DEFAULT_PROVIDERS.single() as ProviderSetting.OpenAI
        val json = kotlinx.serialization.json.Json.encodeToString(
            ProviderSetting.serializer(), first
        )
        val decoded = kotlinx.serialization.json.Json.decodeFromString(
            ProviderSetting.serializer(), json
        )
        assertTrue(decoded is ProviderSetting.OpenAI)
        decoded as ProviderSetting.OpenAI
        assertEquals(first.id, decoded.id)
        assertEquals(first.name, decoded.name)
        assertEquals(first.models, decoded.models)
        assertEquals(first.apiKey, decoded.apiKey)
        assertEquals(first.baseUrl, decoded.baseUrl)
        assertEquals(first.enabled, decoded.enabled)
    }

    @Test
    fun `Model record keeps the Orchords identifier`() {
        val model = Model(modelId = ORCHORDS_MODEL_ID, displayName = "Orchords oai-1.0")
        assertEquals(ORCHORDS_MODEL_ID, model.modelId)
        assertEquals("Orchords oai-1.0", model.displayName)
    }
}
