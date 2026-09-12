package com.orchords.ai.core

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsageCostTest {
    private val profile = OaiPricingProfile(
        revision = "2026-09-12",
        currency = "USD",
        inputPerMillion = BigDecimal("1.25"),
        cachedInputPerMillion = BigDecimal("0.25"),
        outputPerMillion = BigDecimal("5"),
    )

    @Test
    fun `cost separates input cached input and output`() {
        val cost = TokenUsage(promptTokens = 1_000_000, cachedTokens = 250_000, completionTokens = 2_000).cost(profile)

        assertEquals(BigDecimal("0.937500000000"), cost?.input)
        assertEquals(BigDecimal("0.062500000000"), cost?.cachedInput)
        assertEquals(BigDecimal("0.010000000000"), cost?.output)
        assertEquals(BigDecimal("1.010000000000"), cost?.total)
    }

    @Test
    fun `missing pricing remains unknown`() {
        val cost = TokenUsage(promptTokens = 10, completionTokens = 20).cost(profile.copy(outputPerMillion = null))

        assertNull(cost?.total)
        assertNull(cost?.output)
    }

    @Test
    fun `negative effective input is rejected`() {
        assertNull(TokenUsage(promptTokens = 1, cachedTokens = 2, completionTokens = 0).cost(profile))
    }
}
