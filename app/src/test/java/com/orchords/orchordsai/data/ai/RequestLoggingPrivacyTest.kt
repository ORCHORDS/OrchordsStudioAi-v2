package com.orchords.orchordsai.data.ai

import java.io.IOException
import java.io.File
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestLoggingPrivacyTest {
    @Test
    fun `request diagnostics exclude URL content headers body and exception message`() {
        val sentinel = "PRIVATE_SENTINEL_220"
        val request = Request.Builder()
            .url("https://user:$sentinel@example.com/private/$sentinel?token=$sentinel#fragment-$sentinel")
            .header("Authorization", "Bearer $sentinel")
            .header("Cookie", "session=$sentinel")
            .header("X-Custom-Secret", sentinel)
            .header("Content-Type", "application/json; private=$sentinel")
            .post(sentinel.toRequestBody("application/json".toMediaType()))
            .build()
        val responseHeaders = Headers.Builder()
            .add("Set-Cookie", "session=$sentinel")
            .add("X-Response-Secret", sentinel)
            .add("Content-Type", "application/json; private=$sentinel")
            .build()

        val log = buildSafeRequestLog(
            request = request,
            responseCode = 401,
            responseHeaders = responseHeaders,
            durationMs = 17,
            error = IOException("upstream echoed $sentinel"),
        )

        assertEquals("https://example.com/", log.url)
        assertEquals("POST", log.method)
        assertEquals(401, log.responseCode)
        assertEquals(17L, log.durationMs)
        assertNull(log.requestBody)
        assertEquals(mapOf("Content-Type" to "<present>"), log.requestHeaders)
        assertEquals(mapOf("Content-Type" to "<present>"), log.responseHeaders)
        assertEquals("IOException", log.error)

        val diagnostic = listOf(
            log.url,
            log.requestHeaders.toString(),
            log.requestBody.orEmpty(),
            log.responseHeaders.toString(),
            log.error.orEmpty(),
        ).joinToString("\n")
        assertFalse(diagnostic.contains(sentinel))
        assertFalse(diagnostic.contains("Authorization"))
        assertFalse(diagnostic.contains("Cookie"))
        assertFalse(diagnostic.contains("/private/"))
        assertFalse(diagnostic.contains("?token="))
    }

    @Test
    fun `request diagnostics retain only allowlisted header presence`() {
        val headers = Headers.Builder()
            .add("Content-Type", "text/plain; charset=utf-8")
            .add("Content-Length", "123")
            .add("Content-Encoding", "gzip")
            .add("Accept-Language", "en-US")
            .add("User-Agent", "private-device-description")
            .build()

        assertEquals(
            mapOf(
                "Content-Type" to "<present>",
                "Content-Length" to "<present>",
                "Content-Encoding" to "<present>",
            ),
            headers.toSafeLogMetadata(),
        )
    }

    @Test
    fun `shared client cannot reintroduce OkHttp platform header logger`() {
        val source = File("src/main/java/com/orchords/orchordsai/di/DataSourceModule.kt")
        require(source.isFile) { "DataSourceModule.kt not found from ${File(".").canonicalPath}" }
        val text = source.readText()

        assertFalse(text.contains("HttpLoggingInterceptor"))
        assertTrue(text.contains(".addNetworkInterceptor(RequestLoggingInterceptor())"))
    }
}