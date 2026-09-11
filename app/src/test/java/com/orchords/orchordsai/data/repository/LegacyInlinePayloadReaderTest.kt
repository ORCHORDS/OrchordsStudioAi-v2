package com.orchords.orchordsai.data.repository

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyInlinePayloadReaderTest {
    @Test
    fun `three MiB legacy payload is reconstructed from bounded chunks`() = runBlocking {
        val source = "x".repeat(3 * 1024 * 1024)
        var largestRequest = 0
        val result = readLegacyInlinePayload(
            expectedUtf8Bytes = source.toByteArray().size.toLong(),
            maxUtf8Bytes = 16L * 1024 * 1024,
            chunkChars = 64 * 1024,
        ) { start, count ->
            largestRequest = maxOf(largestRequest, count)
            if (start >= source.length) "" else source.substring(start, minOf(source.length, start + count))
        }

        assertEquals(source, result)
        assertTrue(largestRequest <= 64 * 1024)
    }

    @Test
    fun `multibyte utf8 byte accounting is exact`() = runBlocking {
        val source = "€".repeat(300_000)
        val result = readLegacyInlinePayload(
            expectedUtf8Bytes = source.toByteArray().size.toLong(),
            maxUtf8Bytes = 16L * 1024 * 1024,
            chunkChars = 32 * 1024,
        ) { start, count ->
            if (start >= source.length) "" else source.substring(start, minOf(source.length, start + count))
        }
        assertEquals(source, result)
    }

    @Test
    fun `over hard limit rejects before reading chunks`() = runBlocking {
        var calls = 0
        val result = readLegacyInlinePayload(
            expectedUtf8Bytes = 16L * 1024 * 1024 + 1,
            maxUtf8Bytes = 16L * 1024 * 1024,
            chunkChars = 64 * 1024,
        ) { _, _ -> calls++; "unexpected" }

        assertNull(result)
        assertEquals(0, calls)
    }

    @Test
    fun `changed row byte length fails closed`() = runBlocking {
        val source = "abcdef"
        val result = readLegacyInlinePayload(
            expectedUtf8Bytes = 7,
            maxUtf8Bytes = 1024,
            chunkChars = 3,
        ) { start, count ->
            if (start >= source.length) "" else source.substring(start, minOf(source.length, start + count))
        }
        assertNull(result)
    }
}
