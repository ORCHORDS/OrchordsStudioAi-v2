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

    @Test
    fun `web composer exposes Plan with the same stable id and draft conversation state`() {
        val state = source("web-ui/app/components/input/planning-mode-state.ts")
        val button = source("web-ui/app/components/input/planning-mode-button.tsx")
        val input = source("web-ui/app/components/input/chat-input.tsx")
        val page = source("web-ui/app/routes/conversations.tsx")

        assertTrue(state.contains("164b9a03-828e-434e-8aa9-82c0e019a7fb"))
        assertTrue(button.contains("setPlanningModeEnabled"))
        assertTrue(button.contains("setDraftPromptInjectionIds"))
        assertTrue(button.contains("conversations/${'$'}{conversation.id}/injections"))
        assertTrue(button.contains("aria-pressed={enabled}"))
        assertTrue(input.contains("PlanningModeButton"))
        assertTrue(input.contains("conversation={conversation}"))
        assertTrue(input.contains("draftKey={draftKey}"))
        assertTrue(page.contains("useConversationPromptInjection: true"))
    }

    @Test
    fun `web backend permits only a Planning delta when generic conversation injections are disabled`() {
        val routes = source("app/src/main/java/com/orchords/orchordsai/web/routes/ConversationRoutes.kt")
        val planning = source("app/src/main/java/com/orchords/orchordsai/data/ai/planning/PlanningMode.kt")

        assertTrue(routes.contains("isPlanningOnlyConversationInjectionChange"))
        assertTrue(routes.contains("currentModeInjectionIds = conversation.modeInjectionIds"))
        assertTrue(routes.contains("currentLorebookIds = conversation.lorebookIds"))
        assertTrue(routes.contains("applyInitialConversationInjections"))
        assertTrue(planning.contains("requestedLorebookIds != currentLorebookIds"))
        assertTrue(planning.contains("requestedModeInjectionIds - PLANNING_MODE_ID"))
    }
}
