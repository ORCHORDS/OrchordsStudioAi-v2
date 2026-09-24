package com.orchords.ai.ui

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolExecutionCompletionTest {
    @Test
    fun `new empty successful execution is terminal`() {
        val tool = UIMessagePart.Tool(
            toolCallId = "call-empty",
            toolName = "empty_tool",
            input = "{}",
            output = emptyList(),
            executionCompleted = true,
        )
        assertTrue(tool.isExecuted)
        assertFalse(tool.canResumeExecution)
    }

    @Test
    fun `legacy non-empty output remains terminal`() {
        val tool = UIMessagePart.Tool(
            toolCallId = "call-old",
            toolName = "old_tool",
            input = "{}",
            output = listOf(UIMessagePart.Text("done")),
        )
        assertTrue(tool.isExecuted)
    }

    @Test
    fun `legacy empty tool remains pending`() {
        val tool = UIMessagePart.Tool(
            toolCallId = "call-pending",
            toolName = "pending_tool",
            input = "{}",
        )
        assertFalse(tool.isExecuted)
    }

    @Test
    fun `submitted execution is durable and cannot automatically replay`() {
        val tool = UIMessagePart.Tool(
            toolCallId = "call-submitted",
            toolName = "external_write",
            input = "{}",
            executionCompleted = false,
            executionState = ToolExecutionState.SUBMITTED,
        )
        assertFalse(tool.isExecuted)
        assertFalse(tool.canResumeExecution)
    }

    @Test
    fun `unknown outcome is terminal for conversation and survives serialization`() {
        val json = Json { encodeDefaults = true }
        val original = UIMessagePart.Tool(
            toolCallId = "call-unknown",
            toolName = "external_write",
            input = "{}",
            executionCompleted = true,
            executionState = ToolExecutionState.OUTCOME_UNKNOWN,
        )
        val encoded = json.encodeToString(UIMessagePart.serializer(), original)
        val decoded = json.decodeFromString(UIMessagePart.serializer(), encoded) as UIMessagePart.Tool
        assertTrue(decoded.isExecuted)
        assertFalse(decoded.canResumeExecution)
        assertEquals(ToolExecutionState.OUTCOME_UNKNOWN, decoded.executionState)
    }

    @Test
    fun `approval state can resume after durable awaiting approval`() {
        val tool = UIMessagePart.Tool(
            toolCallId = "call-approval",
            toolName = "write_file",
            input = "{}",
            approvalState = ToolApprovalState.Approved,
            executionState = ToolExecutionState.AWAITING_APPROVAL,
        )
        assertTrue(tool.canResumeExecution)
    }

    @Test
    fun `completion marker survives serialization`() {
        val json = Json { encodeDefaults = true }
        val original = UIMessagePart.Tool(
            toolCallId = "call-empty",
            toolName = "empty_tool",
            input = "{}",
            executionCompleted = true,
        )
        val encoded = json.encodeToString(UIMessagePart.serializer(), original)
        val decoded = json.decodeFromString(UIMessagePart.serializer(), encoded) as UIMessagePart.Tool
        assertTrue(decoded.isExecuted)
        assertTrue(decoded.executionCompleted == true)
    }
}
