package com.orchords.orchordsai.data.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class CompatibilityRetryPolicyTest {
    @Test
    fun `compatibility rejection is never retried automatically`() {
        var invocations = 0
        var retryCount = 0
        val rejection = IllegalStateException("HTTP 400 compatibility rejection")

        while (true) {
            invocations += 1
            val shouldRetry = canAutomaticallyRetryProviderRequest(
                error = rejection,
                retryCount = retryCount,
                maxRetries = 3,
                hasReceivedProviderEvent = false,
            )
            if (!shouldRetry) break
            retryCount += 1
        }

        assertEquals(1, invocations)
    }
}
