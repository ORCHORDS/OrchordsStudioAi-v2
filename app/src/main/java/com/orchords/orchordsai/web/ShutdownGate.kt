package com.orchords.orchordsai.web

/**
 * Tracks whether a foreground-service owned Ktor/NSD shutdown is already in flight.
 *
 * The local web server is owned in application scope but the foreground service that
 * represents it can be destroyed through paths other than the explicit
 * `ACTION_STOP` (memory pressure, user stop, task removal). When that happens we
 * still need to tear down the Ktor listener and unregister mDNS, but the stop
 * must be idempotent: a duplicate stop must not toggle the visible UI state into a
 * stale "stopping" surface, and `restart()` chains `stop() -> start()` so a stop
 * issued while no server is live must be a no-op.
 *
 * This helper isolates the gate logic so it can be unit-tested without Android
 * `Context`, Ktor, JmDNS, Room, or other Android-only collaborators that the
 * concrete [WebServerManager] requires.
 */
class ShutdownGate {
    @Volatile
    private var inFlight: Boolean = false

    /**
     * Enter the gate when a shutdown should actually run. Returns `true` if the
     * caller should proceed with the stop work, `false` if the call should be a
     * no-op (either because no server is live and no shutdown is in flight, or
     * because a shutdown is already underway and a duplicate stop has arrived).
     */
    @Synchronized
    fun enter(): Boolean {
        if (inFlight) return false
        inFlight = true
        return true
    }

    /**
     * Leave the gate. Always safe to call after a successful [enter] even if the
     * shutdown body threw — callers are expected to invoke this from a `finally`
     * block so a failure mid-shutdown does not permanently lock out future stops.
     */
    @Synchronized
    fun exit() {
        inFlight = false
    }

    /** True iff a shutdown is currently being processed by some caller. */
    fun isInFlight(): Boolean = inFlight
}
