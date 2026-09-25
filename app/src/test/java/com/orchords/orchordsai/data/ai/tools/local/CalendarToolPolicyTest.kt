package com.orchords.orchordsai.data.ai.tools.local

import android.provider.CalendarContract
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarToolPolicyTest {
    @Test
    fun `calendar write capability requires contributor access and sync events`() {
        assertFalse(isWritableCalendar(CalendarContract.Calendars.CAL_ACCESS_READ, syncEvents = true))
        assertFalse(isWritableCalendar(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR, syncEvents = false))
        assertTrue(isWritableCalendar(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR, syncEvents = true))
        assertTrue(isWritableCalendar(CalendarContract.Calendars.CAL_ACCESS_OWNER, syncEvents = true))
    }
}
