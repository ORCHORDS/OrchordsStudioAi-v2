package com.orchords.orchordsai.data.model

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemporaryConversationRegistryTest {
    private val id = "temporary-test-conversation"

    @After
    fun tearDown() {
        TemporaryConversationRegistry.markRetained(id)
    }

    @Test
    fun `marked conversation is temporary until explicitly promoted`() {
        TemporaryConversationRegistry.markTemporary(id)
        assertTrue(TemporaryConversationRegistry.isTemporary(id))

        TemporaryConversationRegistry.markRetained(id)
        assertFalse(TemporaryConversationRegistry.isTemporary(id))
    }

    @Test
    fun `unregistered conversation is retained by default`() {
        assertFalse(TemporaryConversationRegistry.isTemporary("ordinary-conversation"))
    }
}
