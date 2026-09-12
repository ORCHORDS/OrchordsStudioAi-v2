package com.orchords.orchordsai.data.ai.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class McpCloudflareStreamableHttpHeadersTest {

    @Test
    fun `recognizes canonical cloudflare mcp hosts`() {
        assertTrue(isCloudflareMcpHost("https://mcp.cloudflare.com/mcp"))
        assertTrue(isCloudflareMcpHost("https://gateway.cloudflare.com/v1/acct"))
        assertTrue(isCloudflareMcpHost("https://account.mcp.cloudflare.com/"))
    }

    @Test
    fun `rejects non-cloudflare hosts`() {
        assertFalse(isCloudflareMcpHost("https://mcp.example.com/"))
        assertFalse(isCloudflareMcpHost("https://cloudflare-evil.example.com/"))
        assertFalse(isCloudflareMcpHost("not-a-url"))
    }

    @Test
    fun `streamable accept constant advertises both json and sse`() {
        assertEquals("application/json, text/event-stream", MCP_STREAMABLE_ACCEPT)
    }

    @Test
    fun `host detection does not leak when scheme or path is malformed`() {
        assertFalse(isCloudflareMcpHost(""))
        assertFalse(isCloudflareMcpHost("mcp.cloudflare.com"))
    }
}
