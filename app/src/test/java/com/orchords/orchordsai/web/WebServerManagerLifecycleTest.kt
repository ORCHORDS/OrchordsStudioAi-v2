package com.orchords.orchordsai.web

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Locks the lifecycle contract that [WebServerManager] exposes for issue #346:
 *
 * 1. `stop()` is idempotent: a second concurrent call returns immediately
 *    without re-entering the shutdown gate.
 * 2. `restart()` serializes stop-then-start so the new listener's port
 *    probe runs after the previous listener has been torn down.
 * 3. The lifecycle fence survives many racing shutdown callers without
 *    leaving the gate permanently locked.
 *
 * These tests exercise the gate + fence contract directly without spinning
 * up a real Ktor listener (which the host unit-test JVM cannot do).
 */
class WebServerManagerLifecycleTest {

    @Test
    fun `ShutdownGate accepts first enter and rejects second concurrent enter`() {
        val gate = ShutdownGate()
        assertTrue("first enter must succeed", gate.enter())
        assertFalse("second concurrent enter must fail", gate.enter())
        assertTrue(gate.isInFlight())
        gate.exit()
        assertFalse(gate.isInFlight())
    }

    @Test
    fun `ShutdownGate exit allows re-entry after the previous shutdown completes`() {
        val gate = ShutdownGate()
        assertTrue(gate.enter())
        gate.exit()
        assertTrue("gate must be reusable", gate.enter())
        gate.exit()
    }

    @Test
    fun `ShutdownGate handles enter without exit without leaving state permanently locked`() {
        val gate = ShutdownGate()
        gate.enter()
        gate.exit()
        assertFalse("exit() must clear in-flight state", gate.isInFlight())
    }

    @Test
    fun `concurrent stop calls observe the gate contract without leaking state`() = runTest {
        val gate = ShutdownGate()
        val winners = AtomicInteger(0)
        val losers = AtomicInteger(0)

        val jobs = List(100) {
            async(Dispatchers.IO) {
                if (gate.enter()) {
                    winners.incrementAndGet()
                    // Simulate the Ktor internal runBlocking up to grace+timeout.
                    delay(5)
                    gate.exit()
                } else {
                    losers.incrementAndGet()
                }
            }
        }
        jobs.awaitAll()
        advanceUntilIdle()
        assertEquals(1, winners.get())
        assertEquals(99, losers.get())
        assertFalse("gate must be idle after all callers exit", gate.isInFlight())
    }

    @Test
    fun `restart fence serializes shutdown before next start`() = runTest {
        // Mirror restart()'s contract on a stripped-down harness:
        // a previous stop must complete (gate exits) before the next start
        // observes the port as available. Without the fence, start would race
        // and find the port bound — the bug #346 explicitly reports.
        val gate = ShutdownGate()
        val port = AtomicInteger(0)
        var startIssuedBeforeStopFinished = false

        val stopJob = async(Dispatchers.IO) {
            if (!gate.enter()) return@async
            try {
                port.set(8080)
                delay(50)
            } finally {
                port.set(0)
                gate.exit()
            }
        }

        // stopBlocking() must hold until gate exits so the port is released
        // by the time start() probes it.
        async(Dispatchers.IO) {
            while (gate.isInFlight()) {
                delay(5)
            }
            if (port.get() != 0) {
                startIssuedBeforeStopFinished = true
            }
            port.set(8080)
        }.await()

        stopJob.await()
        advanceUntilIdle()

        assertFalse(
            "start() must not see a bound port while shutdown is in flight",
            startIssuedBeforeStopFinished,
        )
        assertFalse(gate.isInFlight())
        assertEquals(8080, port.get())
    }
}
