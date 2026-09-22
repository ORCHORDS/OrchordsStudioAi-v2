package com.orchords.orchordsai.data.datastore

import com.orchords.ai.provider.Model
import com.orchords.ai.provider.ModelAbility
import com.orchords.ai.provider.ProviderSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class FirstPartyModelPolicyTest {
    @Test
    fun `legacy providers and unknown models are inert in effective settings`() {
        val legacyModel = Model(
            id = Uuid.parse("11111111-1111-4111-8111-111111111111"),
            modelId = "legacy-model",
        )
        val legacyProvider = ProviderSetting.Google(
            id = Uuid.parse("22222222-2222-4222-8222-222222222222"),
            name = "Legacy Google",
            apiKey = "legacy-secret",
            models = listOf(legacyModel),
        )
        val canonicalWithExtraModel = (DEFAULT_PROVIDERS.single() as ProviderSetting.OpenAI).copy(
            apiKey = "orchords-secret",
            models = listOf(
                Model(modelId = "wrong-extra-model"),
                Model(
                    id = ORCHORDS_MODEL_UUID,
                    modelId = ORCHORDS_MODEL_ID,
                    abilities = listOf(ModelAbility.TOOL),
                ),
            ),
        )
        val settings = Settings(
            providers = listOf(legacyProvider, canonicalWithExtraModel),
            chatModelId = legacyModel.id,
            fastModelId = legacyModel.id,
            translateModeId = legacyModel.id,
            compressModelId = legacyModel.id,
            favoriteModels = listOf(
                legacyModel.id,
                ORCHORDS_MODEL_UUID,
                ORCHORDS_OAI_1_2_MODEL_UUID,
            ),
            assistants = DEFAULT_ASSISTANTS.map { it.copy(chatModelId = legacyModel.id) },
        )

        val effective = settings.enforceFirstPartyModelPolicy()

        assertEquals(1, effective.providers.size)
        val provider = effective.providers.single() as ProviderSetting.OpenAI
        assertEquals("OrchordsAI", provider.name)
        assertEquals(ORCHORDS_GATEWAY_BASE_URL, provider.baseUrl)
        assertEquals("orchords-secret", provider.apiKey)
        assertTrue(provider.builtIn)
        assertTrue(provider.enabled)
        assertEquals(
            listOf(ORCHORDS_MODEL_ID, ORCHORDS_OAI_1_2_MODEL_ID),
            provider.models.map { it.modelId },
        )
        assertEquals(ORCHORDS_MODEL_UUID, effective.chatModelId)
        assertEquals(ORCHORDS_MODEL_UUID, effective.fastModelId)
        assertEquals(ORCHORDS_MODEL_UUID, effective.translateModeId)
        assertEquals(ORCHORDS_MODEL_UUID, effective.compressModelId)
        assertEquals(
            listOf(ORCHORDS_MODEL_UUID, ORCHORDS_OAI_1_2_MODEL_UUID),
            effective.favoriteModels,
        )
        assertTrue(effective.assistants.all { it.chatModelId == ORCHORDS_MODEL_UUID })
        assertFalse(effective.providers.any { it is ProviderSetting.Google || it is ProviderSetting.Claude })
    }

    @Test
    fun `valid oai-1_2 selections survive first party normalization`() {
        val settings = Settings(
            providers = DEFAULT_PROVIDERS,
            chatModelId = ORCHORDS_OAI_1_2_MODEL_UUID,
            fastModelId = ORCHORDS_OAI_1_2_MODEL_UUID,
            translateModeId = ORCHORDS_OAI_1_2_MODEL_UUID,
            compressModelId = ORCHORDS_OAI_1_2_MODEL_UUID,
            favoriteModels = listOf(ORCHORDS_OAI_1_2_MODEL_UUID),
            assistants = DEFAULT_ASSISTANTS.map {
                it.copy(chatModelId = ORCHORDS_OAI_1_2_MODEL_UUID)
            },
        )

        val effective = settings.enforceFirstPartyModelPolicy()

        assertEquals(ORCHORDS_OAI_1_2_MODEL_UUID, effective.chatModelId)
        assertEquals(ORCHORDS_OAI_1_2_MODEL_UUID, effective.fastModelId)
        assertEquals(ORCHORDS_OAI_1_2_MODEL_UUID, effective.translateModeId)
        assertEquals(ORCHORDS_OAI_1_2_MODEL_UUID, effective.compressModelId)
        assertEquals(listOf(ORCHORDS_OAI_1_2_MODEL_UUID), effective.favoriteModels)
        assertTrue(effective.assistants.all {
            it.chatModelId == ORCHORDS_OAI_1_2_MODEL_UUID
        })
    }

    @Test
    fun `empty migrated assistant state is repaired to compatibility default`() {
        val effective = Settings(assistants = emptyList()).enforceFirstPartyModelPolicy()

        assertTrue(effective.assistants.isNotEmpty())
        assertEquals(effective.assistants.first().id, effective.assistantId)
        assertEquals(ORCHORDS_MODEL_UUID, effective.assistants.first().chatModelId)
        assertEquals(effective.assistants.first(), effective.getCurrentAssistant())
    }

    @Test
    fun `canonical route cannot be retargeted but keeps its gateway credential`() {
        val stored = (DEFAULT_PROVIDERS.single() as ProviderSetting.OpenAI).copy(
            apiKey = "protected-key",
            baseUrl = "https://example.invalid/v1",
            enabled = false,
            name = "Renamed",
            models = listOf(Model(modelId = "other")),
        )

        val provider = canonicalOrchordsProvider(listOf(stored))

        assertEquals(ORCHORDS_GATEWAY_BASE_URL, provider.baseUrl)
        assertEquals("OrchordsAI", provider.name)
        assertEquals("protected-key", provider.apiKey)
        assertTrue(provider.enabled)
        assertEquals(
            listOf(ORCHORDS_MODEL_ID, ORCHORDS_OAI_1_2_MODEL_ID),
            provider.models.map { it.modelId },
        )
    }

    @Test
    fun `first party model internal ids are stable and default stays oai-1_0`() {
        assertEquals(
            Uuid.parse("0d3a4f7b-1a25-4f31-9d18-1c5f6b2a7e02"),
            ORCHORDS_MODEL_UUID,
        )
        assertEquals(
            Uuid.parse("0d3a4f7b-1a25-4f31-9d18-1c5f6b2a7e03"),
            ORCHORDS_OAI_1_2_MODEL_UUID,
        )
        assertEquals(ORCHORDS_MODEL_UUID, DEFAULT_AUTO_MODEL_ID)
    }
}
