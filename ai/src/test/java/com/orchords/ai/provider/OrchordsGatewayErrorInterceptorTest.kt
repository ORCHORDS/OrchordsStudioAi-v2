package com.orchords.ai.provider

import java.net.InetAddress
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OrchordsGatewayErrorInterceptorTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient.Builder()
            .dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> =
                    if (hostname == ORCHORDS_GATEWAY_HOST) {
                        listOf(InetAddress.getByName("127.0.0.1"))
                    } else {
                        Dns.SYSTEM.lookup(hostname)
                    }
            })
            .addInterceptor(OrchordsGatewayErrorInterceptor())
            .build()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `auth failure never exposes echoed gateway body`() {
        val sentinel = "SENTINEL_PROMPT bearer-secret /private/path"
        server.enqueue(
            MockResponse.Builder()
                .code(401)
                .body("{\"error\":{\"message\":\"$sentinel\"}}")
                .build()
        )

        val error = assertThrows(OrchordsGatewayException::class.java) {
            client.newCall(orchordsRequest()).execute()
        }

        assertEquals(GatewayErrorCategory.AUTH, error.error.category)
        assertEquals(401, error.error.httpStatus)
        assertFalse(error.error.retryable)
        assertTrue(error.error.redacted)
        assertFalse(error.message.orEmpty().contains(sentinel))
        assertTrue(error.message.orEmpty().length < 160)
    }

    @Test
    fun `rate limit preserves numeric retry after without parsing body`() {
        server.enqueue(
            MockResponse.Builder()
                .code(429)
                .addHeader("Retry-After", "120")
                .body("<html>SENTINEL_RATE_LIMIT_BODY</html>")
                .build()
        )

        val error = assertThrows(OrchordsGatewayException::class.java) {
            client.newCall(orchordsRequest()).execute()
        }

        assertEquals(GatewayErrorCategory.RATE_LIMIT, error.error.category)
        assertEquals(120L, error.error.retryAfterSeconds)
        assertTrue(error.error.retryable)
        assertFalse(error.message.orEmpty().contains("SENTINEL_RATE_LIMIT_BODY"))
    }

    @Test
    fun `invalid retry after is ignored`() {
        server.enqueue(
            MockResponse.Builder()
                .code(503)
                .addHeader("Retry-After", "not-a-delay")
                .body("SENTINEL_SERVER_BODY")
                .build()
        )

        val error = assertThrows(OrchordsGatewayException::class.java) {
            client.newCall(orchordsRequest()).execute()
        }

        assertEquals(GatewayErrorCategory.GATEWAY_SERVER, error.error.category)
        assertNull(error.error.retryAfterSeconds)
        assertTrue(error.error.retryable)
        assertFalse(error.message.orEmpty().contains("SENTINEL_SERVER_BODY"))
    }

    @Test
    fun `non Orchords hosts are not rewritten`() {
        server.enqueue(
            MockResponse.Builder()
                .code(418)
                .body("ordinary-response")
                .build()
        )

        client.newCall(Request.Builder().url(server.url("/other")).build()).execute().use { response ->
            assertEquals(418, response.code)
            assertEquals("ordinary-response", response.body.string())
        }
    }

    private fun orchordsRequest(): Request = Request.Builder()
        .url("http://$ORCHORDS_GATEWAY_HOST:${server.port}/v1/chat/completions")
        .build()
}
