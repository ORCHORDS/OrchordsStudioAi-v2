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
    fun `OrchordsAI provider exposes oai-1_0 and reasoning capable oai-1_2`() {
        val provider = DEFAULT_PROVIDERS.single() as ProviderSetting.OpenAI
        assertEquals(2, provider.models.size)
        val models = provider.models.associateBy { it.modelId }

        val legacy = models.getValue(ORCHORDS_MODEL_ID)
        assertEquals(ORCHORDS_MODEL_UUID, legacy.id)
        assertEquals("Orchords oai-1.0", legacy.displayName)
        assertEquals(listOf(ModelAbility.TOOL), legacy.abilities)
        assertFalse(BuiltInTools.Search in legacy.tools)

        val qpipe = models.getValue(ORCHORDS_OAI_1_2_MODEL_ID)
        assertEquals(ORCHORDS_OAI_1_2_MODEL_UUID, qpipe.id)
        assertEquals("Orchords oai-1.2", qpipe.displayName)
        assertEquals(listOf(ModelAbility.TOOL, ModelAbility.REASONING), qpipe.abilities)
        assertFalse(BuiltInTools.Search in qpipe.tools)
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
    fun `Model records keep both Orchords identifiers`() {
        val legacy = Model(modelId = ORCHORDS_MODEL_ID, displayName = "Orchords oai-1.0")
        val qpipe = Model(modelId = ORCHORDS_OAI_1_2_MODEL_ID, displayName = "Orchords oai-1.2")
        assertEquals(ORCHORDS_MODEL_ID, legacy.modelId)
        assertEquals(ORCHORDS_OAI_1_2_MODEL_ID, qpipe.modelId)
    }
}
