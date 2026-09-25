package com.orchords.ai.ui

import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
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

    @Test
    fun `explicit lifecycle distinguishes paused and terminal states`() {
        val awaitingApproval = UIMessagePart.Tool(
            toolCallId = "call-approval",
            toolName = "approval_tool",
            input = "{}",
            approvalState = ToolApprovalState.Pending,
            executionState = ToolExecutionState.AWAITING_APPROVAL,
        )
        val awaitingAuth = awaitingApproval.copy(
            approvalState = ToolApprovalState.Approved,
            executionState = ToolExecutionState.AWAITING_AUTH,
        )
        val succeeded = awaitingAuth.copy(executionState = ToolExecutionState.SUCCEEDED)
        val failed = awaitingAuth.copy(executionState = ToolExecutionState.FAILED)

        assertTrue(awaitingApproval.isPending)
        assertFalse(awaitingApproval.canResumeExecution)
        assertFalse(awaitingAuth.isExecuted)
        assertFalse(awaitingAuth.canResumeExecution)
        assertTrue(succeeded.isExecuted)
        assertTrue(failed.isExecuted)
    }

    @Test
    fun `lifecycle survives serialization without breaking legacy completion marker`() {
        val json = Json { encodeDefaults = true }
        val original = UIMessagePart.Tool(
            toolCallId = "call-state",
            toolName = "stateful_tool",
            input = "{}",
            executionCompleted = true,
            executionState = ToolExecutionState.SUCCEEDED,
        )
        val encoded = json.encodeToString(UIMessagePart.serializer(), original)
        val decoded = json.decodeFromString(UIMessagePart.serializer(), encoded) as UIMessagePart.Tool

        assertEquals(ToolExecutionState.SUCCEEDED, decoded.executionState)
        assertTrue(decoded.executionCompleted == true)
        assertTrue(decoded.isExecuted)
    }
    @Test
    fun `approved durable approval state becomes resumable after transition to prepared`() {
        val pending = UIMessagePart.Tool(
            toolCallId = "call-approval-resume",
            toolName = "approval_tool",
            input = "{}",
            approvalState = ToolApprovalState.Pending,
            executionState = ToolExecutionState.AWAITING_APPROVAL,
        )
        assertFalse(pending.canResumeExecution)

        val approved = pending.copy(
            approvalState = ToolApprovalState.Approved,
            executionState = ToolExecutionState.PREPARED,
        )
        assertTrue(approved.canResumeExecution)
    }

}
