package com.orchords.orchordsai.web.routes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagedFileDownloadPolicyTest {
    @Test
    fun `managed files are always inert attachment downloads`() {
        listOf("payload.html", "script.svg", "doc.xml", "photo.png", "report.pdf").forEach { name ->
            val headers = managedFileDownloadHeaders(name)
            assertEquals("application/octet-stream", headers.contentType)
            assertEquals("nosniff", headers.contentTypeOptions)
            assertTrue(headers.contentDisposition.startsWith("attachment;"))
        }
    }

    @Test
    fun `display name cannot inject path or response header syntax`() {
        val headers = managedFileDownloadHeaders("../nested/evil\"\r\nX-Test: injected.html")
        val fallback = headers.contentDisposition.substringAfter("filename=\"").substringBefore("\"")

        assertFalse(fallback.contains('/'))
        assertFalse(fallback.contains('\\'))
        assertFalse(fallback.contains('"'))
        assertFalse(fallback.contains('\r'))
        assertFalse(fallback.contains('\n'))
        assertFalse(headers.contentDisposition.contains("\r\nX-Test:"))
    }
}
