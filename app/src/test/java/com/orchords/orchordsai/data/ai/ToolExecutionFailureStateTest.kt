package com.orchords.orchordsai.data.ai

import com.orchords.ai.ui.ToolExecutionState
import com.orchords.orchordsai.data.ai.mcp.McpAuthorizationRequiredException
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.uuid.Uuid

class ToolExecutionFailureStateTest {
    @Test
    fun `authorization-required failure pauses instead of becoming terminal failure`() {
        val exception = McpAuthorizationRequiredException(
            serverId = Uuid.random(),
            toolName = "write_issue",
            cause = IllegalStateException("fixture unauthorized"),
        )
        assertEquals(ToolExecutionState.AWAITING_AUTH, toolExecutionStateForFailure(exception))
    }

    @Test
    fun `ordinary execution failure remains terminal failure`() {
        assertEquals(
            ToolExecutionState.FAILED,
            toolExecutionStateForFailure(IllegalStateException("fixture failure")),
        )
    }
}
