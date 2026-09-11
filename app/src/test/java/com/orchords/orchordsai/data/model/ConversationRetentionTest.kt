package com.orchords.orchordsai.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationRetentionTest {
    @Test
    fun `retained conversations allow durable persistence`() {
        assertTrue(ConversationRetention.RETAINED.allowsDurablePersistence())
    }

    @Test
    fun `temporary conversations deny durable persistence`() {
        assertFalse(ConversationRetention.TEMPORARY.allowsDurablePersistence())
    }
}
