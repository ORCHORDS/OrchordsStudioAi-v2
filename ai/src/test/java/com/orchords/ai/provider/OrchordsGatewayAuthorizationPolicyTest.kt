package com.orchords.ai.provider

import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Test

class OrchordsGatewayAuthorizationPolicyTest {
    @Test
    fun `persisted provider credential wins over stale custom authorization`() {
        val request = Request.Builder()
            .url("https://$ORCHORDS_GATEWAY_HOST/v1/chat/completions")
            .addHeader("Authorization", "Bearer stale-custom")
            .addHeader("Authorization", "Bearer persisted-provider")
            .build()

        val normalized = request.normalizeOrchordsGatewayAuthorization()

        assertEquals(
            listOf("Bearer persisted-provider"),
            normalized.headers.values("Authorization"),
        )
    }

    @Test
    fun `single gateway authorization is preserved`() {
        val request = Request.Builder()
            .url("https://$ORCHORDS_GATEWAY_HOST/v1/chat/completions")
            .addHeader("Authorization", "Bearer persisted-provider")
            .build()

        val normalized = request.normalizeOrchordsGatewayAuthorization()

        assertEquals(
            listOf("Bearer persisted-provider"),
            normalized.headers.values("Authorization"),
        )
    }

    @Test
    fun `non gateway authorization headers are untouched`() {
        val request = Request.Builder()
            .url("https://example.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer first")
            .addHeader("Authorization", "Bearer second")
            .build()

        val normalized = request.normalizeOrchordsGatewayAuthorization()

        assertEquals(
            listOf("Bearer first", "Bearer second"),
            normalized.headers.values("Authorization"),
        )
    }
}
