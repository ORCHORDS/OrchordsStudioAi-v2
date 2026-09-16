package com.orchords.orchordsai.ui.components.ai

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanningModeUiPolicyTest {
    private val root = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .first { File(it, "app/src/main/java").isDirectory }

    private fun source(path: String): String = File(root, path).readText()

    @Test
    fun `chat composer exposes dedicated Plan control wired to conversation state`() {
        val input = source("app/src/main/java/com/orchords/orchordsai/ui/components/ai/ChatInput.kt")
        val page = source("app/src/main/java/com/orchords/orchordsai/ui/pages/chat/ChatPage.kt")
        val button = source("app/src/main/java/com/orchords/orchordsai/ui/components/ai/PlanningModeButton.kt")

        assertTrue(button.contains("fun PlanningModeButton"))
        assertTrue(button.contains("ToggleSurface"))
        assertTrue(button.contains("Plan"))
        assertTrue(input.contains("PlanningModeButton("))
        assertTrue(input.contains("planningModeEnabled"))
        assertTrue(input.contains("onUpdatePlanningMode"))
        assertTrue(page.contains("conversation.modeInjectionIds.isPlanningModeEnabled()"))
        assertTrue(page.contains("conversation.modeInjectionIds.withPlanningMode(enabled)"))
        assertTrue(page.contains("vm.updateConversation"))
    }
}
