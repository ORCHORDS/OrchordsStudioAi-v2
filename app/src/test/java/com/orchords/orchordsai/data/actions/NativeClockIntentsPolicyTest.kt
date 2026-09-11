package com.orchords.orchordsai.data.actions

import java.util.Calendar
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeClockIntentsPolicyTest {
    @Test
    fun `validates bounded alarms and timers before any Clock intent is built`() {
        validateNativeAlarmRequest(
            NativeAlarmRequest(
                hour = 6,
                minute = 30,
                daysOfWeek = setOf(Calendar.MONDAY, Calendar.FRIDAY),
                label = "Wake up",
            )
        )
        validateNativeTimerRequest(NativeTimerRequest(durationSeconds = 900, label = "Cooking"))

        assertRejected { validateNativeAlarmRequest(NativeAlarmRequest(hour = 24, minute = 0)) }
        assertRejected { validateNativeAlarmRequest(NativeAlarmRequest(hour = 6, minute = 60)) }
        assertRejected { validateNativeAlarmRequest(NativeAlarmRequest(hour = 6, minute = 30, daysOfWeek = setOf(0))) }
        assertRejected { validateNativeAlarmRequest(NativeAlarmRequest(hour = 6, minute = 30, label = "x".repeat(MAX_CLOCK_LABEL_CHARS + 1))) }
        assertRejected { validateNativeTimerRequest(NativeTimerRequest(durationSeconds = 0)) }
        assertRejected { validateNativeTimerRequest(NativeTimerRequest(durationSeconds = MAX_TIMER_SECONDS + 1)) }
        assertRejected { validateNativeTimerRequest(NativeTimerRequest(durationSeconds = 60, label = "x".repeat(MAX_CLOCK_LABEL_CHARS + 1))) }
    }

    private fun assertRejected(block: () -> Unit) {
        assertTrue(runCatching(block).isFailure)
    }
}
