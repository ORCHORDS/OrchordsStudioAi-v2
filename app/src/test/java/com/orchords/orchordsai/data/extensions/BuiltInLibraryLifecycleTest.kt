package com.orchords.orchordsai.data.extensions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltInLibraryLifecycleTest {
    @Test
    fun `deleting an installed built in records a tombstone`() {
        val removed = recordRemovedBuiltIns(
            currentIds = setOf("built-in"),
            updatedIds = emptySet(),
            builtInIds = setOf("built-in"),
            existingRemovedIds = emptySet(),
        )
        assertEquals(setOf("built-in"), removed)
    }

    @Test
    fun `editing a built in while retaining its id does not tombstone it`() {
        val removed = recordRemovedBuiltIns(
            currentIds = setOf("built-in"),
            updatedIds = setOf("built-in"),
            builtInIds = setOf("built-in"),
            existingRemovedIds = emptySet(),
        )
        assertTrue(removed.isEmpty())
    }

    @Test
    fun `deleting custom content does not create a built in tombstone`() {
        val removed = recordRemovedBuiltIns(
            currentIds = setOf("custom"),
            updatedIds = emptySet(),
            builtInIds = setOf("built-in"),
            existingRemovedIds = emptySet(),
        )
        assertTrue(removed.isEmpty())
    }

    @Test
    fun `existing tombstones are preserved across later edits`() {
        val removed = recordRemovedBuiltIns(
            currentIds = setOf("other-built-in"),
            updatedIds = setOf("other-built-in"),
            builtInIds = setOf("built-in", "other-built-in"),
            existingRemovedIds = setOf("built-in"),
        )
        assertEquals(setOf("built-in"), removed)
    }

    @Test
    fun `install candidates exclude tombstoned identities without changing order`() {
        val candidates = listOf("one", "removed", "two")
        val filtered = filterRemovedBuiltIns(candidates, setOf("removed")) { it }
        assertEquals(listOf("one", "two"), filtered)
        assertFalse("removed" in filtered)
    }
}
