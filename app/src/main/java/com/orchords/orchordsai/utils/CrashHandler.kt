package com.orchords.orchordsai.utils

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.core.content.edit

private const val TAG = "CrashHandler"
private const val PREFS_NAME = "crash_handler"
private const val KEY_CRASHED = "crashed"
private const val KEY_STACKTRACE = "stacktrace"
private const val KEY_STARTUP_FAILURE_COUNT = "startup_failure_count"
private const val KEY_LAST_STARTUP_FAILURE_AT = "last_startup_failure_at"
private const val MAX_STACKTRACE_LENGTH = 8000

object CrashHandler {
    @Volatile
    private var processStartedAtElapsedMs: Long = 0L

    fun install(context: Context) {
        val appContext = context.applicationContext
        processStartedAtElapsedMs = SystemClock.elapsedRealtime()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught exception on thread ${thread.name}", throwable)
            markCrashed(appContext, thread, throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    fun hasCrashed(context: Context): Boolean {
        val count = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_STARTUP_FAILURE_COUNT, 0)
        return shouldEnterSafeMode(count)
    }

    fun getStackTrace(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_STACKTRACE, null)
    }

    fun clearCrashed(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                remove(KEY_CRASHED)
                remove(KEY_STACKTRACE)
                remove(KEY_STARTUP_FAILURE_COUNT)
                remove(KEY_LAST_STARTUP_FAILURE_AT)
            }
    }

    private fun markCrashed(context: Context, thread: Thread, throwable: Throwable) {
        val stackTrace = buildString {
            appendLine("Thread: ${thread.name}")
            appendLine(throwable.stackTraceToString())
        }.take(MAX_STACKTRACE_LENGTH)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val failureAtMs = System.currentTimeMillis()
        val processUptimeMs = (SystemClock.elapsedRealtime() - processStartedAtElapsedMs)
            .coerceAtLeast(0L)
        val startupFailureCount = nextStartupFailureCount(
            previousCount = prefs.getInt(KEY_STARTUP_FAILURE_COUNT, 0),
            previousFailureAtMs = prefs.getLong(KEY_LAST_STARTUP_FAILURE_AT, 0L),
            failureAtMs = failureAtMs,
            processUptimeMs = processUptimeMs,
        )
        prefs.edit(commit = true) {
            putBoolean(KEY_CRASHED, true)
            putString(KEY_STACKTRACE, stackTrace)
            putInt(KEY_STARTUP_FAILURE_COUNT, startupFailureCount)
            if (startupFailureCount > 0) {
                putLong(KEY_LAST_STARTUP_FAILURE_AT, failureAtMs)
            } else {
                remove(KEY_LAST_STARTUP_FAILURE_AT)
            }
        }
    }
}
