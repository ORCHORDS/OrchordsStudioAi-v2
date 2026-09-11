package com.orchords.orchordsai.data.files

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Test

class ExternalFolderGrantPolicyTest {
    @Test
    fun `write authority never exceeds flags returned by Android`() {
        val read = Intent.FLAG_GRANT_READ_URI_PERMISSION
        val write = Intent.FLAG_GRANT_WRITE_URI_PERMISSION

        assertEquals(read, effectiveTreeGrantFlags(read, requestWrite = true))
        assertEquals(read, effectiveTreeGrantFlags(read or write, requestWrite = false))
        assertEquals(read or write, effectiveTreeGrantFlags(read or write, requestWrite = true))
    }

    @Test
    fun `unrelated activity result flags are never persisted`() {
        val read = Intent.FLAG_GRANT_READ_URI_PERMISSION
        val unrelated = Intent.FLAG_ACTIVITY_NEW_TASK

        assertEquals(read, effectiveTreeGrantFlags(read or unrelated, requestWrite = true))
    }
}
