package com.orchords.orchordsai.data.actions

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.AlarmClock
import java.util.Calendar

internal const val MAX_CLOCK_LABEL_CHARS = 200
internal const val MAX_TIMER_SECONDS = 86_400
private val VALID_WEEKDAYS = setOf(
    Calendar.SUNDAY,
    Calendar.MONDAY,
    Calendar.TUESDAY,
    Calendar.WEDNESDAY,
    Calendar.THURSDAY,
    Calendar.FRIDAY,
    Calendar.SATURDAY,
)

data class NativeAlarmRequest(
    val hour: Int,
    val minute: Int,
    val daysOfWeek: Set<Int> = emptySet(),
    val label: String = "",
    val vibrate: Boolean? = null,
    val showClockUi: Boolean = true,
)

data class NativeTimerRequest(
    val durationSeconds: Int,
    val label: String = "",
    val showClockUi: Boolean = true,
)

internal fun validateNativeAlarmRequest(request: NativeAlarmRequest) {
    require(request.hour in 0..23) { "Alarm hour must be 0..23" }
    require(request.minute in 0..59) { "Alarm minute must be 0..59" }
    require(request.daysOfWeek.all { it in VALID_WEEKDAYS }) { "Alarm weekdays are invalid" }
    require(request.label.length <= MAX_CLOCK_LABEL_CHARS) { "Alarm label is too long" }
}

internal fun validateNativeTimerRequest(request: NativeTimerRequest) {
    require(request.durationSeconds in 1..MAX_TIMER_SECONDS) {
        "Timer length must be between 1 and $MAX_TIMER_SECONDS seconds"
    }
    require(request.label.length <= MAX_CLOCK_LABEL_CHARS) { "Timer label is too long" }
}

/** First-party allowlist for Android Clock intents. Model/user text never becomes an intent action. */
object NativeClockIntents {
    fun setAlarm(request: NativeAlarmRequest): Intent {
        validateNativeAlarmRequest(request)
        return Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, request.hour)
            putExtra(AlarmClock.EXTRA_MINUTES, request.minute)
            if (request.daysOfWeek.isNotEmpty()) {
                putIntegerArrayListExtra(AlarmClock.EXTRA_DAYS, ArrayList(request.daysOfWeek.sorted()))
            }
            if (request.label.isNotEmpty()) putExtra(AlarmClock.EXTRA_MESSAGE, request.label)
            request.vibrate?.let { putExtra(AlarmClock.EXTRA_VIBRATE, it) }
            putExtra(AlarmClock.EXTRA_SKIP_UI, !request.showClockUi)
        }
    }

    fun setTimer(request: NativeTimerRequest): Intent {
        validateNativeTimerRequest(request)
        return Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, request.durationSeconds)
            if (request.label.isNotEmpty()) putExtra(AlarmClock.EXTRA_MESSAGE, request.label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, !request.showClockUi)
        }
    }

    fun showAlarms(): Intent = Intent(AlarmClock.ACTION_SHOW_ALARMS)

    fun showTimers(): Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(AlarmClock.ACTION_SHOW_TIMERS)
    } else {
        null
    }

    fun isSupported(context: Context, intent: Intent): Boolean =
        intent.resolveActivity(context.packageManager) != null
}
