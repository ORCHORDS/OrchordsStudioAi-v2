package com.orchords.orchordsai.data.ai.transformers

import com.orchords.ai.core.MessageRole
import com.orchords.ai.ui.UIMessage
import com.orchords.ai.ui.UIMessagePart
import com.orchords.orchordsai.data.model.Assistant
import com.orchords.orchordsai.data.model.InjectionPosition
import com.orchords.orchordsai.data.model.PromptInjection
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class PromptInjectionRuntimeVariablesTest {
    private val zone = ZoneId.of("America/Los_Angeles")
    private val firstVariables = PromptInjectionRuntimeVariables.capture(
        Clock.fixed(Instant.parse("2026-01-01T00:30:00Z"), zone)
    )

    @Test
    fun `runtime variables use one captured request instant and preserve unknown names`() {
        assertEquals(
            "2025-12-31 16:30:00-08:00 2025-12-31T16:30:00-08:00 {{cur_unknown}}",
            firstVariables.render("{{cur_date}} {{cur_time}} {{cur_datetime}} {{cur_unknown}}")
        )
    }

    @Test
    fun `rendering copies injection without mutating configured source`() {
        val configured = PromptInjection.ModeInjection(content = "At {{cur_time}}")

        val rendered = configured.renderRuntimeVariables(firstVariables)

        assertNotSame(configured, rendered)
        assertEquals("At {{cur_time}}", configured.content)
        assertEquals("At 16:30:00-08:00", rendered.content)
    }

    @Test
    fun `all injection positions render from same request snapshot without mutating history`() {
        val originalMessages = listOf(
            UIMessage.system("System"),
            UIMessage.user("First"),
            UIMessage.assistant("Reply"),
            UIMessage.user("Last"),
        )
        val snapshot = originalMessages.map { it.copy(parts = it.parts.toList()) }

        InjectionPosition.entries.forEach { position ->
            if (position !in setOf(
                    InjectionPosition.BEFORE_SYSTEM_PROMPT,
                    InjectionPosition.AFTER_SYSTEM_PROMPT,
                    InjectionPosition.TOP_OF_CHAT,
                    InjectionPosition.BOTTOM_OF_CHAT,
                    InjectionPosition.AT_DEPTH,
                )
            ) return@forEach

            val id = Uuid.random()
            val injection = PromptInjection.ModeInjection(
                id = id,
                enabled = true,
                position = position,
                content = "stamp={{cur_datetime}}",
                injectDepth = 2,
                role = MessageRole.USER,
            )
            val result = transformMessages(
                messages = originalMessages,
                assistant = Assistant(modeInjectionIds = setOf(id)),
                modeInjections = listOf(injection),
                lorebooks = emptyList(),
                runtimeVariables = firstVariables,
            )
            val renderedText = result.flatMap { it.parts }
                .filterIsInstance<UIMessagePart.Text>()
                .joinToString("\n") { it.text }

            assertTrue("position=$position", renderedText.contains("stamp=2025-12-31T16:30:00-08:00"))
            assertFalse("position=$position", renderedText.contains("{{cur_datetime}}"))
            assertEquals("stamp={{cur_datetime}}", injection.content)
            assertEquals(snapshot, originalMessages)
        }
    }

    @Test
    fun `later transform captures a fresh value instead of caching first expansion`() {
        val later = PromptInjectionRuntimeVariables.capture(
            Clock.fixed(Instant.parse("2026-01-01T01:30:00Z"), zone)
        )

        assertEquals("16:30:00-08:00", firstVariables.render("{{cur_time}}"))
        assertEquals("17:30:00-08:00", later.render("{{cur_time}}"))
    }
}
