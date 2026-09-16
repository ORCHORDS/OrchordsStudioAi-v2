package com.orchords.orchordsai.data.ai.planning

import com.orchords.ai.core.Tool

private val PLANNING_AUTOMATIC_READ_ONLY_TOOLS = setOf(
    "search_web",
    "scrape_web",
    "workspace_read_file",
)

fun planningToolNeedsApproval(toolName: String): Boolean =
    toolName !in PLANNING_AUTOMATIC_READ_ONLY_TOOLS

fun Tool.withPlanningApprovalOverlay(enabled: Boolean): Tool {
    if (!enabled) return this
    val existingNeedsApproval = needsApproval
    val planningNeedsApproval = planningToolNeedsApproval(name)
    return copy(
        needsApproval = { input ->
            existingNeedsApproval(input) || planningNeedsApproval
        }
    )
}
