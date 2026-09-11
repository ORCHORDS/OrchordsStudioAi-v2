package com.orchords.common.companion

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionProtocolTest {
    @Test
    fun `replay guard rejects redelivery and remains bounded`() {
        val guard = CompanionReplayGuard(maxEntries = 2)
        assertTrue(guard.accept("a"))
        assertFalse(guard.accept("a"))
        assertTrue(guard.accept("b"))
        assertTrue(guard.accept("c"))
        // Oldest entry was evicted; transport-level durable receipts can remain stricter.
        assertTrue(guard.accept("a"))
    }

    @Test
    fun `envelope requires bounded versioned stable identity`() {
        val valid = CompanionIntentEnvelope(
            requestId = "request-1",
            sessionId = "session-1",
            idempotencyKey = "action-1",
            kind = CompanionIntentKind.QUICK_QUERY,
            payload = "hello",
            createdAtEpochMillis = 1,
        )
        valid.validate()

        assertTrue(runCatching { valid.copy(protocolVersion = 99).validate() }.isFailure)
        assertTrue(runCatching { valid.copy(idempotencyKey = "").validate() }.isFailure)
        assertTrue(
            runCatching {
                valid.copy(payload = "x".repeat(MAX_COMPANION_PAYLOAD_CHARS + 1)).validate()
            }.isFailure
        )
    }
}
