package com.orchords.orchordsai.data.model

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemporaryConversationMarkerStoreTest {
    @Test
    fun `marker survives a fresh store instance and clears on promotion`() {
        val root = Files.createTempDirectory("temporary-chat-marker").toFile()
        val id = "3f6a8bd8-64f1-4bb9-b2c1-1e90c65a35ff"
        try {
            TemporaryConversationMarkerStore(root).markTemporary(id)

            val reloaded = TemporaryConversationMarkerStore(root)
            assertTrue(reloaded.isTemporary(id))
            assertEquals(setOf(id), reloaded.listTemporaryIds())

            reloaded.markRetained(id)
            assertFalse(reloaded.isTemporary(id))
            assertTrue(reloaded.listTemporaryIds().isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `path shaped ids are rejected`() {
        val root = Files.createTempDirectory("temporary-chat-marker-invalid").toFile()
        try {
            TemporaryConversationMarkerStore(root).markTemporary("../escape")
        } finally {
            root.deleteRecursively()
        }
    }
}
