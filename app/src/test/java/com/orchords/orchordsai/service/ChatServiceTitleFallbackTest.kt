package com.orchords.orchordsai.service

import com.orchords.ai.provider.Model
import com.orchords.ai.provider.ProviderSetting
import com.orchords.orchordsai.data.datastore.DEFAULT_ASSISTANT_ID
import com.orchords.orchordsai.data.datastore.Settings
import com.orchords.orchordsai.data.datastore.findModelById
import com.orchords.orchordsai.data.datastore.getCurrentChatModel
import com.orchords.orchordsai.data.model.Assistant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.uuid.Uuid

/**
 * Locks the title-generation fallback chain used by ChatService.generateTitle:
 *
 *     findModelById(fastModelId) ?: getCurrentChatModel() ?: error(...)
 *
 * On a fresh install Settings.fastModelId defaults to Uuid.random() (which never
 * matches a configured provider), so without the fallback the title is silently
 * dropped and the chat keeps its blank title. With the fallback the current
 * chat model resolves and the title generation path stays alive.
 */
class ChatServiceTitleFallbackTest {
    private val randomUuid = Uuid.random()

    @Test
    fun `default fastModelId is unresolvable on a fresh install`() {
        val settings = Settings(
            fastModelId = randomUuid, // the exact default
            providers = emptyList(),
        )

        assertNull(
            "Default Settings.fastModelId must not resolve to a model on a fresh install",
            settings.findModelById(settings.fastModelId),
        )
        assertNull(
            "Without providers, getCurrentChatModel must also be null",
            settings.getCurrentChatModel(),
        )
    }

    @Test
    fun `fallback chain resolves to configured chat model when fastModelId is unresolvable`() {
        val chatModelId = Uuid.random()
        val chatModel = Model(modelId = "gpt-4o-mini", id = chatModelId)
        val settings = Settings(
            fastModelId = randomUuid, // unresolvable
            chatModelId = chatModelId, // explicit chat model
            providers = listOf(ProviderSetting.OpenAI(models = listOf(chatModel))),
            assistants = listOf(
                Assistant(
                    id = DEFAULT_ASSISTANT_ID,
                    chatModelId = chatModelId,
                ),
            ),
            assistantId = DEFAULT_ASSISTANT_ID,
        )

        val resolved = settings.findModelById(settings.fastModelId)
            ?: settings.getCurrentChatModel()

        assertNotNull(
            "Fallback must resolve the chat model even when fastModelId is unresolvable",
            resolved,
        )
        assertEquals(chatModelId, resolved!!.id)
    }

    @Test
    fun `fallback chain throws when neither fastModelId nor chat model is configured`() {
        val settings = Settings(
            fastModelId = randomUuid,
            providers = emptyList(),
            assistants = emptyList(),
        )

        val ex = kotlin.runCatching {
            settings.findModelById(settings.fastModelId)
                ?: settings.getCurrentChatModel()
                ?: error("No chat model available for title generation")
        }.exceptionOrNull()

        // error(...) returns Nothing, so the contract is "must throw IllegalStateException".
        // The production catch block in ChatService.generateTitle converts this into an
        // actionable CheckFastModelSettings error visible to the user.
        assertNotNull("Expected the fallback chain to throw on a fully-unconfigured install", ex)
    }
}
