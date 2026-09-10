package com.orchords.orchordsai.data.model

import com.orchords.ai.core.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.uuid.Uuid

class LorebookEntryOrderTest {
    private fun entry(name: String) = PromptInjection.RegexInjection(
        id = Uuid.random(),
        name = name,
        priority = 10,
        position = InjectionPosition.AFTER_SYSTEM_PROMPT,
        content = name,
        role = MessageRole.USER,
        keywords = listOf("trigger"),
    )

    @Test
    fun `equal priority lorebook entries preserve stored list order`() {
        val first = entry("first")
        val second = entry("second")

        assertEquals(
            listOf(first.id, second.id),
            getTriggeredInjections(listOf(first, second), "trigger").map { it.id },
        )
        assertEquals(
            listOf(second.id, first.id),
            getTriggeredInjections(listOf(second, first), "trigger").map { it.id },
        )
    }
}
