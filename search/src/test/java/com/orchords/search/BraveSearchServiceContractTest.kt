package com.orchords.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BraveSearchServiceContractTest {
    @Test
    fun `request uses official Brave web endpoint and subscription header`() {
        val request = buildBraveSearchRequest(
            query = "  android data store migration  ",
            resultSize = 10,
            apiKey = "brave-secret",
        )

        assertEquals("https", request.url.scheme)
        assertEquals("api.search.brave.com", request.url.host)
        assertEquals("/res/v1/web/search", request.url.encodedPath)
        assertEquals("android data store migration", request.url.queryParameter("q"))
        assertEquals("10", request.url.queryParameter("count"))
        assertEquals("brave-secret", request.header("X-Subscription-Token"))
        assertFalse(request.headers.toString().contains("Authorization:"))
    }

    @Test
    fun `result count is bounded to Brave documented maximum`() {
        val request = buildBraveSearchRequest("weather malaysia", 999, "key")
        assertEquals("20", request.url.queryParameter("count"))
    }

    @Test
    fun `empty credential fails before network request is built`() {
        val failure = runCatching {
            buildBraveSearchRequest("weather malaysia", 10, "")
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun `query beyond documented size fails locally`() {
        val failure = runCatching {
            buildBraveSearchRequest("a".repeat(601), 10, "key")
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun `response parser preserves source title url and description`() {
        val result = parseBraveSearchResponse(
            """
            {
              "type":"search",
              "web":{
                "type":"search",
                "results":[{
                  "type":"search_result",
                  "title":"Official documentation",
                  "url":"https://example.test/docs",
                  "description":"Verified source text"
                }]
              }
            }
            """.trimIndent()
        )

        assertEquals(1, result.items.size)
        assertEquals("Official documentation", result.items.single().title)
        assertEquals("https://example.test/docs", result.items.single().url)
        assertEquals("Verified source text", result.items.single().text)
    }
}
