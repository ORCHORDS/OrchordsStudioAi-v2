package com.orchords.orchordsai.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the contract that [WebServerManager.stop] relies on for issue #346:
 * the foreground-service destruction path must tear down the Ktor listener and
 * NSD registration exactly once, even when [WebServerService.onDestroy] runs
 * after an explicit `ACTION_STOP` already sent a stop, or when the OS delivers
 * multiple destruction events.
 *
 * The gate is a pure helper extracted from WebServerManager so this contract
 * is verifiable without Android Context, Ktor, JmDNS, Room, or the rest of the
 * WebServerManager dependency graph.
 */
class ShutdownGateTest {

    @Test
    fun `first enter returns true and marks the gate in-flight`() {
        val gate = ShutdownGate()
        assertTrue("Idle gate must accept its first shutdown", gate.enter())
        assertTrue(gate.isInFlight())
    }

    @Test
    fun `duplicate enter while in flight returns false`() {
        val gate = ShutdownGate()
        assertTrue(gate.enter())
        assertFalse("Concurrent shutdowns must not run in parallel", gate.enter())
        assertTrue(gate.isInFlight())
    }

    @Test
    fun `exit clears the gate so the next shutdown is accepted`() {
        val gate = ShutdownGate()
        gate.enter()
        gate.exit()
        assertFalse(gate.isInFlight())
        assertTrue("After exit, the next shutdown must be allowed", gate.enter())
    }

    @Test
    fun `exit after a failure still releases the gate`() {
        val gate = ShutdownGate()
        gate.enter()
        // Simulate the `finally { shutdownGate.exit() }` body executing after
        // the shutdown work threw. The gate must still be released so a
        // subsequent stop() attempt (e.g. an immediate service restart) is not
        // locked out by a stranded shutdown.
        runCatching { throw IllegalStateException("simulated shutdown failure") }
        gate.exit()
        assertFalse(gate.isInFlight())
        assertTrue(gate.enter())
    }

    @Test
    fun `rapid double destruction produces exactly one effective shutdown`() {
        // Reproduces the WebServerService.onDestroy() happy path:
        //   1. ACTION_STOP -> stop() enters the gate, async work starts
        //   2. onDestroy() -> stop() must NOT re-enter and must NOT toggle state
        //   3. async work completes, gate exits
        val gate = ShutdownGate()
        val enterLog = mutableListOf<Boolean>()
        enterLog += gate.enter()               // ACTION_STOP path
        enterLog += gate.enter()               // onDestroy path (re-entry)
        gate.exit()                            // async stop completes
        enterLog += gate.enter()               // user restarts the service
        gate.exit()
        assertEquals(listOf(true, false, true), enterLog)
    }

    @Test
    fun `idle stop leaves the gate unentered`() {
        // Mirrors WebServerManager.stop()'s fast-path for an already-idle server:
        // if (server == null && !shutdownGate.isInFlight()) return
        // The gate is not entered and the state flow is not toggled into a
        // stale "stopping" surface for an already-stopped server.
        val gate = ShutdownGate()
        val wasIdle = !gate.isInFlight()
        if (wasIdle) {
            // No-op path: do not call enter()
        }
        assertFalse("Idle path must not enter the gate", gate.isInFlight())
    }
}
