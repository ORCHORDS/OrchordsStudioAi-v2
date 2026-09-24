package com.orchords.orchordsai.data.ai.mcp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class McpContentBoundsTest {
    @Test
    fun `bounded base64 decoder accepts content within the limit`() {
        assertArrayEquals(byteArrayOf(0), decodeBoundedMcpBase64("AA==", maxDecodedBytes = 1))
    }

    @Test
    fun `bounded base64 decoder rejects decoded content over the limit`() {
        val error = runCatching {
            decodeBoundedMcpBase64("AAAA", maxDecodedBytes = 2)
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `bounded text rejects multi byte content beyond byte budget`() {
        val error = runCatching {
            requireBoundedMcpText("éé", maxBytes = 3)
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `bounded text accepts content at byte budget`() {
        assertTrue(requireBoundedMcpText("abc", maxBytes = 3) == "abc")
    }

    @Test
    fun `bounded base64 decoder rejects oversized encoded input before decoding`() {
        val error = runCatching {
            decodeBoundedMcpBase64("AAAAA", maxDecodedBytes = 2)
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }
}
