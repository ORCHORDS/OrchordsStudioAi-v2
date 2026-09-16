package com.orchords.orchordsai.data.ai.planning

import com.orchords.ai.core.Tool
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanningToolPolicyTest {
    private val emptyArgs = JsonObject(emptyMap())

    @Test
    fun `only explicit read only planning tools stay automatic`() {
        assertFalse(planningToolNeedsApproval("search_web"))
        assertFalse(planningToolNeedsApproval("scrape_web"))
        assertFalse(planningToolNeedsApproval("workspace_read_file"))

        assertTrue(planningToolNeedsApproval("workspace_write_file"))
        assertTrue(planningToolNeedsApproval("workspace_edit_file"))
        assertTrue(planningToolNeedsApproval("workspace_shell"))
        assertTrue(planningToolNeedsApproval("mcp__github__create_issue"))
        assertTrue(planningToolNeedsApproval("unknown_future_tool"))
    }

    @Test
    fun `planning overlay never weakens an existing approval requirement`() {
        val alreadyProtected = Tool(
            name = "search_web",
            description = "",
            needsApproval = { true },
            execute = { emptyList() },
        )

        val wrapped = alreadyProtected.withPlanningApprovalOverlay(enabled = true)

        assertTrue(wrapped.needsApproval(emptyArgs))
    }

    @Test
    fun `planning overlay gates unknown tools`() {
        val unknown = Tool(
            name = "future_tool",
            description = "",
            needsApproval = { false },
            execute = { emptyList() },
        )

        val wrapped = unknown.withPlanningApprovalOverlay(enabled = true)

        assertTrue(wrapped.needsApproval(emptyArgs))
    }

    @Test
    fun `planning overlay leaves allowlisted read only tools automatic when originally automatic`() {
        val search = Tool(
            name = "search_web",
            description = "",
            needsApproval = { false },
            execute = { emptyList() },
        )

        val wrapped = search.withPlanningApprovalOverlay(enabled = true)

        assertFalse(wrapped.needsApproval(emptyArgs))
    }

    @Test
    fun `disabled planning overlay preserves original behavior`() {
        val unknown = Tool(
            name = "future_tool",
            description = "",
            needsApproval = { false },
            execute = { emptyList() },
        )

        val wrapped = unknown.withPlanningApprovalOverlay(enabled = false)

        assertFalse(wrapped.needsApproval(emptyArgs))
    }
}
