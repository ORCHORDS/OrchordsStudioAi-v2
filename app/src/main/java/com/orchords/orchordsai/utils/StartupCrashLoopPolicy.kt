package com.orchords.orchordsai.utils

internal const val STARTUP_FAILURE_THRESHOLD = 2
internal const val STARTUP_FAILURE_WINDOW_MS = 30_000L
internal const val CRASH_LOOP_WINDOW_MS = 5 * 60_000L

internal fun nextStartupFailureCount(
    previousCount: Int,
    previousFailureAtMs: Long,
    failureAtMs: Long,
    processUptimeMs: Long,
): Int {
    if (processUptimeMs > STARTUP_FAILURE_WINDOW_MS) return 0
    val consecutive = previousCount > 0 &&
        previousFailureAtMs > 0 &&
        failureAtMs - previousFailureAtMs in 0..CRASH_LOOP_WINDOW_MS
    return if (consecutive) previousCount + 1 else 1
}

internal fun shouldEnterSafeMode(startupFailureCount: Int): Boolean =
    startupFailureCount >= STARTUP_FAILURE_THRESHOLD
