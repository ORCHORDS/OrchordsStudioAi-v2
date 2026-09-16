package com.orchords.orchordsai.data.ai

import com.orchords.ai.provider.GatewayError
import com.orchords.ai.provider.GatewayErrorCategory
import com.orchords.ai.provider.OrchordsGatewayException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderRetryPolicyTest {
    @Test
    fun `network failure before first provider event may retry`() {
        assertTrue(
            canAutomaticallyRetryProviderRequest(
                error = IOException("fixture disconnect"),
                retryCount = 0,
                maxRetries = 3,
                hasReceivedProviderEvent = false,
            )
        )
    }

    @Test
    fun `gateway auth failure never retries even though it is an IOException`() {
        val error = OrchordsGatewayException(
            GatewayError(
                category = GatewayErrorCategory.AUTH,
                httpStatus = 401,
                retryable = false,
            )
        )

        assertFalse(
            canAutomaticallyRetryProviderRequest(
                error = error,
                retryCount = 0,
                maxRetries = 3,
                hasReceivedProviderEvent = false,
            )
        )
    }

    @Test
    fun `retryable gateway server failure may retry before output`() {
        val error = OrchordsGatewayException(
            GatewayError(
                category = GatewayErrorCategory.GATEWAY_SERVER,
                httpStatus = 503,
                retryable = true,
            )
        )

        assertTrue(
            canAutomaticallyRetryProviderRequest(
                error = error,
                retryCount = 0,
                maxRetries = 3,
                hasReceivedProviderEvent = false,
            )
        )
    }

    @Test
    fun `retryable gateway failure never retries after provider output`() {
        val error = OrchordsGatewayException(
            GatewayError(
                category = GatewayErrorCategory.RATE_LIMIT,
                httpStatus = 429,
                retryable = true,
            )
        )

        assertFalse(
            canAutomaticallyRetryProviderRequest(
                error = error,
                retryCount = 0,
                maxRetries = 3,
                hasReceivedProviderEvent = true,
            )
        )
    }

    @Test
    fun `network failure after first text chunk never retries automatically`() {
        assertFalse(postOutputRetryDecision())
    }

    @Test
    fun `network failure after multiple chunks never retries automatically`() {
        var hasReceivedProviderEvent = false
        repeat(3) {
            hasReceivedProviderEvent = true
        }

        assertFalse(
            canAutomaticallyRetryProviderRequest(
                error = IOException("fixture disconnect after multiple chunks"),
                retryCount = 0,
                maxRetries = 3,
                hasReceivedProviderEvent = hasReceivedProviderEvent,
            )
        )
    }

    @Test
    fun `network failure after tool event never retries automatically`() {
        assertFalse(postOutputRetryDecision())
    }

    @Test
    fun `network failure after image or server tool event never retries automatically`() {
        assertFalse(postOutputRetryDecision())
    }

    @Test
    fun `post output failure keeps provider invocation count at one`() {
        assertEquals(
            1,
            simulatedProviderInvocationCount(
                providerEventBeforeFailure = true,
                maxRetries = 3,
            )
        )
    }

    @Test
    fun `pre output failure follows configured retry count`() {
        assertEquals(
            4,
            simulatedProviderInvocationCount(
                providerEventBeforeFailure = false,
                maxRetries = 3,
            )
        )
    }

    @Test
    fun `retry limit is enforced before response begins`() {
        assertFalse(
            canAutomaticallyRetryProviderRequest(
                error = IOException("fixture disconnect"),
                retryCount = 3,
                maxRetries = 3,
                hasReceivedProviderEvent = false,
            )
        )
    }

    @Test
    fun `non network failure is never retried`() {
        assertFalse(
            canAutomaticallyRetryProviderRequest(
                error = IllegalStateException("fixture protocol failure"),
                retryCount = 0,
                maxRetries = 3,
                hasReceivedProviderEvent = false,
            )
        )
    }

    @Test
    fun `cancellation is never retried`() {
        assertFalse(
            canAutomaticallyRetryProviderRequest(
                error = CancellationException("fixture cancellation"),
                retryCount = 0,
                maxRetries = 3,
                hasReceivedProviderEvent = false,
            )
        )
    }

    private fun postOutputRetryDecision(): Boolean =
        canAutomaticallyRetryProviderRequest(
            error = IOException("fixture disconnect after provider activity"),
            retryCount = 0,
            maxRetries = 3,
            hasReceivedProviderEvent = true,
        )

    private fun simulatedProviderInvocationCount(
        providerEventBeforeFailure: Boolean,
        maxRetries: Int,
    ): Int {
        var invocations = 0
        var retryCount = 0

        while (true) {
            invocations += 1
            val shouldRetry = canAutomaticallyRetryProviderRequest(
                error = IOException("fixture disconnect"),
                retryCount = retryCount,
                maxRetries = maxRetries,
                hasReceivedProviderEvent = providerEventBeforeFailure,
            )
            if (!shouldRetry) return invocations
            retryCount += 1
        }
    }
}
