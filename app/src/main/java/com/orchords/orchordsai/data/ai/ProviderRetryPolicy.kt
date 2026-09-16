package com.orchords.orchordsai.data.ai

import com.orchords.ai.provider.OrchordsGatewayException
import java.io.IOException

internal fun canAutomaticallyRetryProviderRequest(
    error: Throwable,
    retryCount: Int,
    maxRetries: Int,
    hasReceivedProviderEvent: Boolean,
): Boolean {
    if (hasReceivedProviderEvent || retryCount >= maxRetries) return false

    return when (error) {
        is OrchordsGatewayException -> error.error.retryable
        is IOException -> true
        else -> false
    }
}
