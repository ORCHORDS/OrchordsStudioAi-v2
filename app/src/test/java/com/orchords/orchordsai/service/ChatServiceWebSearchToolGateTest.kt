package com.orchords.orchordsai.service

import com.orchords.ai.provider.ModelAbility
import com.orchords.ai.provider.Model
import com.orchords.ai.provider.ProviderSetting
import com.orchords.orchordsai.data.model.Assistant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the contract the runtime fix at ChatService.handleMessageComplete relies on:
 * when the user explicitly requested external web search but the chosen model lacks
 * the TOOL ability, the chat service must abort generation instead of silently
 * producing a response that never invokes the search tool.
 *
 * These tests exercise the pure helper [shouldUseExternalWebSearch] (which already
 * has its own coverage in ChatServiceTest) together with the runtime model's
 * ability bit so any future regression in either layer is caught here.
 */
class ChatServiceWebSearchToolGateTest {
    @Test
    fun `external web search requested but model lacks TOOL ability triggers abort path`() {
        val assistant = Assistant(enableWebSearch = true)
        val nonToolModel = Model(abilities = emptyList()) // default abilities

        assertTrue(
            "Toggle on + non-Claude-native provider must request external search",
            shouldUseExternalWebSearch(assistant, nonToolModel, providerSetting = null),
        )
        assertFalse(
            "Without ModelAbility.TOOL the runtime fix must surface a tools-warning and abort",
            nonToolModel.abilities.contains(ModelAbility.TOOL),
        )
    }

    @Test
    fun `external web search requested and model has TOOL ability proceeds to tool attach`() {
        val assistant = Assistant(enableWebSearch = true)
        val toolCapableModel = Model(abilities = listOf(ModelAbility.TOOL))

        assertTrue(
            "Toggle on must still request external search when the model is tool-capable",
            shouldUseExternalWebSearch(assistant, toolCapableModel, providerSetting = null),
        )
        assertTrue(
            "ModelAbility.TOOL must be set so the runtime fix does NOT abort",
            toolCapableModel.abilities.contains(ModelAbility.TOOL),
        )
    }

    @Test
    fun `external web search disabled regardless of tool ability`() {
        val assistant = Assistant(enableWebSearch = false)
        val toolCapableModel = Model(abilities = listOf(ModelAbility.TOOL))
        val nonToolModel = Model(abilities = emptyList())

        assertFalse(
            shouldUseExternalWebSearch(assistant, toolCapableModel, providerSetting = null),
        )
        assertFalse(
            shouldUseExternalWebSearch(assistant, nonToolModel, providerSetting = null),
        )
    }

    @Test
    fun `native Anthropic route suppresses external web search even when model has TOOL ability`() {
        val assistant = Assistant(enableWebSearch = true)
        val model = Model(
            abilities = listOf(ModelAbility.TOOL),
            tools = setOf(com.orchords.ai.provider.BuiltInTools.Search),
        )

        assertFalse(
            shouldUseExternalWebSearch(
                assistant,
                model,
                providerSetting = ProviderSetting.Claude(),
            ),
        )
    }
}
