package com.orchords.orchordsai.data.ai.planning

import com.orchords.ai.core.MessageRole
import com.orchords.orchordsai.data.extensions.BuiltInLibrary
import com.orchords.orchordsai.data.model.InjectionPosition
import org.junit.Assert.assertEquals
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
}
