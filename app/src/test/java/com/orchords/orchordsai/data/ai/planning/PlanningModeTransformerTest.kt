package com.orchords.orchordsai.data.ai.planning

import com.orchords.ai.ui.UIMessage
import com.orchords.ai.ui.UIMessagePart
import com.orchords.orchordsai.data.ai.transformers.transformMessages
import com.orchords.orchordsai.data.model.Assistant
import com.orchords.orchordsai.data.model.PromptInjection
import kotlin.uuid.Uuid
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanningModeTransformerTest {
    private fun text(messages: List<UIMessage>): String = messages
        .flatMap { it.parts }
        .filterIsInstance<UIMessagePart.Text>()
        .joinToString("\n") { it.text }

    @Test
    fun `conversation Planning mode applies even when arbitrary conversation injections are disabled`() {
        val messages = listOf(UIMessage.system("System"), UIMessage.user("Plan this"))
        val assistant = Assistant(allowConversationPromptInjection = false)

        val result = transformMessages(
            messages = messages,
            assistant = assistant,
            modeInjections = listOf(planningModeInjection()),
            lorebooks = emptyList(),
            conversationModeInjectionIds = setOf(PLANNING_MODE_ID),
        )

        assertTrue(text(result).contains("Planning Mode is active"))
    }

    @Test
    fun `custom conversation mode remains blocked when arbitrary conversation injections are disabled`() {
        val customId = Uuid.random()
        val custom = PromptInjection.ModeInjection(
            id = customId,
            name = "Custom",
            content = "CUSTOM_SENTINEL",
        )
        val messages = listOf(UIMessage.system("System"), UIMessage.user("Hello"))
        val assistant = Assistant(allowConversationPromptInjection = false)

        val result = transformMessages(
            messages = messages,
            assistant = assistant,
            modeInjections = listOf(custom, planningModeInjection()),
            lorebooks = emptyList(),
            conversationModeInjectionIds = setOf(customId),
        )

        assertFalse(text(result).contains("CUSTOM_SENTINEL"))
    }
}
