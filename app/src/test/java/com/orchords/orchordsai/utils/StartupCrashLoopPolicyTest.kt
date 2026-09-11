package com.orchords.orchordsai.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupCrashLoopPolicyTest {
    @Test
    fun `first startup failure does not force safe mode`() {
        val count = nextStartupFailureCount(
            previousCount = 0,
            previousFailureAtMs = 0,
            failureAtMs = 1_000,
            processUptimeMs = 2_000,
        )

        assertEquals(1, count)
        assertFalse(shouldEnterSafeMode(count))
    }

    @Test
    fun `second consecutive startup failure enters safe mode`() {
        val count = nextStartupFailureCount(
            previousCount = 1,
            previousFailureAtMs = 1_000,
            failureAtMs = 2_000,
            processUptimeMs = 2_000,
        )

        assertEquals(2, count)
        assertTrue(shouldEnterSafeMode(count))
    }

    @Test
    fun `stale startup failure starts a new sequence`() {
        assertEquals(
            1,
            nextStartupFailureCount(
                previousCount = 1,
                previousFailureAtMs = 1_000,
                failureAtMs = 1_000 + CRASH_LOOP_WINDOW_MS + 1,
                processUptimeMs = 2_000,
            )
        )
    }

    @Test
    fun `runtime crash after startup window clears startup failure sequence`() {
        assertEquals(
            0,
            nextStartupFailureCount(
                previousCount = 2,
                previousFailureAtMs = 2_000,
                failureAtMs = 3_000,
                processUptimeMs = STARTUP_FAILURE_WINDOW_MS + 1,
            )
        )
    }

    @Test
    fun `safe mode threshold is explicit`() {
        assertFalse(shouldEnterSafeMode(STARTUP_FAILURE_THRESHOLD - 1))
        assertTrue(shouldEnterSafeMode(STARTUP_FAILURE_THRESHOLD))
    }
}
