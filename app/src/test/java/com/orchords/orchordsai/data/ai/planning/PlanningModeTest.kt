package com.orchords.orchordsai.data.ai.planning

import com.orchords.ai.core.MessageRole
import com.orchords.orchordsai.data.extensions.BuiltInLibrary
import com.orchords.orchordsai.data.extensions.toModeInjection
import com.orchords.orchordsai.data.model.InjectionPosition
import kotlin.uuid.Uuid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanningModeTest {
    @Test
    fun `planning mode id is stable`() {
        assertEquals("164b9a03-828e-434e-8aa9-82c0e019a7fb", PLANNING_MODE_ID.toString())
    }

    @Test
    fun `planning mode prompt enforces research first no silent writes and explicit handoff`() {
        val prompt = PLANNING_MODE_PROMPT.lowercase()

        assertTrue(prompt.contains("inspect"))
        assertTrue(prompt.contains("authoritative"))
        assertTrue(prompt.contains("assumption"))
        assertTrue(prompt.contains("do not") && prompt.contains("write"))
        assertTrue(prompt.contains("do not claim"))
        assertTrue(prompt.contains("ready to execute"))
        assertTrue(prompt.contains("chain-of-thought"))
    }

    @Test
    fun `planning mode injection uses the approved prompt boundary`() {
        val injection = planningModeInjection()

        assertEquals(PLANNING_MODE_ID, injection.id)
        assertEquals("Planning", injection.name)
        assertEquals(InjectionPosition.AFTER_SYSTEM_PROMPT, injection.position)
        assertEquals(MessageRole.USER, injection.role)
        assertEquals(PLANNING_MODE_PROMPT, injection.content)
        assertTrue(injection.enabled)
    }

    @Test
    fun `built in library exposes the planning mode exactly once`() {
        val matches = BuiltInLibrary.catalog.modes.filter { it.id == PLANNING_MODE_ID.toString() }

        assertEquals(1, matches.size)
        assertEquals("Planning", matches.single().name)
    }

    @Test
    fun `built in planning library entry converts to after system prompt`() {
        val planning = BuiltInLibrary.catalog.modes
            .single { it.id == PLANNING_MODE_ID.toString() }
            .toModeInjection()

        assertEquals(InjectionPosition.AFTER_SYSTEM_PROMPT, planning.position)
        assertEquals(PLANNING_MODE_ID, planning.id)
        assertEquals(PLANNING_MODE_PROMPT, planning.content)
    }

    @Test
    fun `planning toggle preserves unrelated conversation modes`() {
        val other = Uuid.random()
        val base = setOf(other)

        val enabled = base.withPlanningMode(true)
        val disabled = enabled.withPlanningMode(false)

        assertTrue(enabled.contains(PLANNING_MODE_ID))
        assertTrue(enabled.contains(other))
        assertFalse(disabled.contains(PLANNING_MODE_ID))
        assertTrue(disabled.contains(other))
    }

    @Test
    fun `planning-only conversation change may toggle planning while preserving all other ids`() {
        val otherMode = Uuid.random()
        val lorebook = Uuid.random()
        val currentModes = setOf(otherMode)
        val requestedModes = currentModes + PLANNING_MODE_ID

        assertTrue(
            isPlanningOnlyConversationInjectionChange(
                currentModeInjectionIds = currentModes,
                currentLorebookIds = setOf(lorebook),
                requestedModeInjectionIds = requestedModes,
                requestedLorebookIds = setOf(lorebook),
            )
        )
    }

    @Test
    fun `planning-only conversation change rejects unrelated mode or lorebook changes`() {
        val existingMode = Uuid.random()
        val otherMode = Uuid.random()
        val existingLorebook = Uuid.random()
        val otherLorebook = Uuid.random()

        assertFalse(
            isPlanningOnlyConversationInjectionChange(
                currentModeInjectionIds = setOf(existingMode),
                currentLorebookIds = setOf(existingLorebook),
                requestedModeInjectionIds = setOf(existingMode, otherMode, PLANNING_MODE_ID),
                requestedLorebookIds = setOf(existingLorebook),
            )
        )
        assertFalse(
            isPlanningOnlyConversationInjectionChange(
                currentModeInjectionIds = setOf(existingMode),
                currentLorebookIds = setOf(existingLorebook),
                requestedModeInjectionIds = setOf(existingMode, PLANNING_MODE_ID),
                requestedLorebookIds = setOf(existingLorebook, otherLorebook),
            )
        )
    }
}
