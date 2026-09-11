package com.orchords.orchordsai.ui.pages.imggen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageGenerationSlotsTest {
    @Test
    fun interleavedIndexedEventsStayInTheirOwnSlots() {
        val slots = ImageGenerationSlots<String>(4)

        assertEquals(2, slots.resolvePartialIndex(2))
        assertTrue(slots.putPartial(2, "preview-2a").accepted)
        assertEquals(1, slots.resolvePartialIndex(1))
        assertTrue(slots.putPartial(1, "preview-1").accepted)

        val replaced = slots.putPartial(2, "preview-2b")
        assertTrue(replaced.accepted)
        assertEquals("preview-2a", replaced.replacedPreview)

        val finalized = slots.putFinal(1, "final-1")
        assertTrue(finalized.accepted)
        assertEquals("preview-1", finalized.replacedPreview)
        assertEquals(listOf("final-1", "preview-2b"), slots.snapshot())
    }

    @Test
    fun latePartialAndDuplicateFinalDoNotReplaceFinalSlot() {
        val slots = ImageGenerationSlots<String>(2)
        slots.putPartial(0, "preview")
        slots.putFinal(0, "final")

        assertFalse(slots.putPartial(0, "late-preview").accepted)
        assertFalse(slots.putFinal(0, "duplicate-final").accepted)
        assertEquals(listOf("final"), slots.snapshot())
    }

    @Test
    fun outOfOrderFinalsPreserveProviderSlotOrder() {
        val slots = ImageGenerationSlots<String>(4)

        slots.putFinal(3, "final-3")
        slots.putFinal(1, "final-1")
        slots.putFinal(0, "final-0")
        slots.putFinal(2, "final-2")

        assertEquals(
            listOf("final-0", "final-1", "final-2", "final-3"),
            slots.snapshot(),
        )
    }

    @Test
    fun missingIndexesUseDistinctDeterministicFallbackSlots() {
        val slots = ImageGenerationSlots<String>(4)

        val first = slots.resolvePartialIndex(null)
        assertEquals(0, first)
        slots.putPartial(first!!, "anonymous-0")

        val second = slots.resolvePartialIndex(null)
        assertEquals(1, second)
        slots.putPartial(second!!, "anonymous-1")

        assertEquals(0, slots.resolveFinalIndex(null))
        slots.putFinal(slots.resolveFinalIndex(null)!!, "final-0")
        assertEquals(1, slots.resolveFinalIndex(null))
    }

    @Test
    fun drainingPartialsRetainsFinals() {
        val slots = ImageGenerationSlots<String>(4)
        slots.putPartial(0, "preview-0")
        slots.putPartial(1, "preview-1")
        slots.putFinal(1, "final-1")
        slots.putFinal(3, "final-3")

        assertEquals(listOf("preview-0"), slots.drainPartials())
        assertEquals(listOf("final-1", "final-3"), slots.snapshot())
        assertTrue(slots.isFinal(1))
        assertFalse(slots.isFinal(0))
        assertNull(slots.resolvePartialIndex(4))
    }
}
