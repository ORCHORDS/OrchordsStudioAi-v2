package com.orchords.ai.ui

import com.orchords.ai.core.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class ToolResultRetentionTest {

    private fun toolPart(callId: String, output: List<UIMessagePart>, executed: Boolean = true): UIMessagePart.Tool =
        UIMessagePart.Tool(
            toolCallId = callId,
            toolName = "lookup",
            input = "{}",
            output = output,
        )

    private fun toolText(text: String) = UIMessagePart.Text(text)

    private fun assistantWithCompletedTool(callId: String, outputText: String): UIMessage {
        val tool = UIMessagePart.Tool(
            toolCallId = callId,
            toolName = "lookup",
            input = "{}",
            output = listOf(toolText(outputText)),
        )
        return UIMessage(role = MessageRole.ASSISTANT, parts = listOf(tool))
    }

    private fun assistantWithPendingTool(callId: String): UIMessage {
        val tool = UIMessagePart.Tool(
            toolCallId = callId,
            toolName = "lookup",
            input = "{}",
            output = emptyList(),
        )
        return UIMessage(role = MessageRole.ASSISTANT, parts = listOf(tool))
    }

    private fun user(text: String): UIMessage =
        UIMessage(role = MessageRole.USER, parts = listOf(toolText(text)))

    private fun assistantText(text: String): UIMessage =
        UIMessage(role = MessageRole.ASSISTANT, parts = listOf(toolText(text)))

    @Test
    fun `ALL mode keeps every completed tool round`() {
        val msgs = listOf(
            user("hi"),
            assistantWithCompletedTool("1", "first"),
            assistantWithCompletedTool("2", "second"),
            assistantText("done"),
        )
        val out = msgs.applyToolResultRetention(ToolResultRetentionMode.ALL)
        assertEquals(msgs, out)
    }

    @Test
    fun `LATEST_N keeps only the N most recent completed rounds`() {
        val msgs = listOf(
            user("hi"),
            assistantWithCompletedTool("1", "first"),
            assistantWithCompletedTool("2", "second"),
            assistantWithCompletedTool("3", "third"),
            assistantText("done"),
        )
        val out = msgs.applyToolResultRetention(ToolResultRetentionMode.LATEST_N, latestNRounds = 1)
        val callIds = out.filter { it.role == MessageRole.ASSISTANT }
            .flatMap { it.parts.filterIsInstance<UIMessagePart.Tool>() }
            .map { it.toolCallId }
        assertEquals(listOf("3"), callIds)
        assertTrue(out.any { it.role == MessageRole.USER })
        // user (prefix) + round3 (kept) + assistantText (suffix) = 3 messages
        assertEquals(3, out.size)
    }

    @Test
    fun `LATEST_N with N zero clamps to one round`() {
        val msgs = listOf(
            user("u"),
            assistantWithCompletedTool("1", "a"),
            assistantWithCompletedTool("2", "b"),
        )
        val out = msgs.applyToolResultRetention(ToolResultRetentionMode.LATEST_N, latestNRounds = 0)
        val callIds = out.filter { it.role == MessageRole.ASSISTANT }
            .flatMap { it.parts.filterIsInstance<UIMessagePart.Tool>() }
            .map { it.toolCallId }
        assertEquals(listOf("2"), callIds)
    }

    @Test
    fun `OMIT_OLDER_COMPLETED preserves only the latest complete round`() {
        val msgs = listOf(
            user("u"),
            assistantWithCompletedTool("1", "a"),
            assistantWithCompletedTool("2", "b"),
            assistantText("answer"),
        )
        val out = msgs.applyToolResultRetention(ToolResultRetentionMode.OMIT_OLDER_COMPLETED)
        val callIds = out.filter { it.role == MessageRole.ASSISTANT }
            .flatMap { it.parts.filterIsInstance<UIMessagePart.Tool>() }
            .map { it.toolCallId }
        assertEquals(listOf("2"), callIds)
        assertEquals("answer", (out.last().parts.first() as UIMessagePart.Text).text)
    }

    @Test
    fun `incomplete rounds are never dropped`() {
        val msgs = listOf(
            user("u"),
            assistantWithCompletedTool("1", "a"),
            assistantWithPendingTool("2"),
            assistantText("partial"),
        )
        val out = msgs.applyToolResultRetention(ToolResultRetentionMode.OMIT_OLDER_COMPLETED)
        val callIds = out.filter { it.role == MessageRole.ASSISTANT }
            .flatMap { it.parts.filterIsInstance<UIMessagePart.Tool>() }
            .map { it.toolCallId }
        assertTrue(callIds.contains("1"))
        assertTrue(callIds.contains("2"))
    }

    @Test
    fun `empty input is returned unchanged`() {
        val empty = emptyList<UIMessage>()
        assertEquals(empty, empty.applyToolResultRetention(ToolResultRetentionMode.LATEST_N, latestNRounds = 3))
        assertEquals(empty, empty.applyToolResultRetention(ToolResultRetentionMode.OMIT_OLDER_COMPLETED))
    }
}
