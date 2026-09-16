package com.orchords.ai.provider

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response

const val ORCHORDS_GATEWAY_HOST = "api.orchords.com"

enum class GatewayErrorCategory {
    NETWORK,
    AUTH,
    RATE_LIMIT,
    QUOTA,
    INVALID_REQUEST,
    MODEL_UNAVAILABLE,
    SAFETY_OR_POLICY,
    GATEWAY_SERVER,
    PROTOCOL,
    CANCELLED,
    UNKNOWN,
}

data class GatewayError(
    val category: GatewayErrorCategory,
    val httpStatus: Int,
    val retryable: Boolean,
    val retryAfterSeconds: Long? = null,
    val redacted: Boolean = true,
) {
    val userMessage: String
        get() = when (category) {
            GatewayErrorCategory.AUTH -> "Orchords gateway authentication failed (HTTP $httpStatus)"
            GatewayErrorCategory.RATE_LIMIT -> "Orchords gateway rate limit reached (HTTP $httpStatus)"
            GatewayErrorCategory.INVALID_REQUEST -> "Orchords gateway rejected the request (HTTP $httpStatus)"
            GatewayErrorCategory.GATEWAY_SERVER -> "Orchords gateway is temporarily unavailable (HTTP $httpStatus)"
            else -> "Orchords gateway request failed (HTTP $httpStatus)"
        }
}

class OrchordsGatewayException(
    val error: GatewayError,
) : IOException(error.userMessage)

/**
 * First-party HTTP failure boundary.
 *
 * Deliberately does not read or parse the response body. The Orchords gateway's
 * error-body schema is not a confirmed product contract, so arbitrary JSON/HTML
 * cannot become exception/UI/log content. Status and Retry-After are safe HTTP
 * metadata and are sufficient for the currently verified classifications.
 *
 * OpenAI-compatible request builders apply user/model custom headers before the
 * provider credential. If a stale custom Authorization header is present, OkHttp
 * can otherwise send both values. For the first-party gateway we collapse only
 * duplicate Authorization values to the final value, which is the provider
 * credential added by the request builder. This keeps custom headers useful while
 * preventing them from shadowing the persisted gateway credential.
 */
internal class OrchordsGatewayErrorInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val request = if (original.url.host == ORCHORDS_GATEWAY_HOST) {
            val authorizationValues = original.headers.values("Authorization")
            if (authorizationValues.size > 1) {
                original.newBuilder()
                    .header("Authorization", authorizationValues.last())
                    .build()
            } else {
                original
            }
        } else {
            original
        }

        val response = chain.proceed(request)
        if (request.url.host != ORCHORDS_GATEWAY_HOST || response.isSuccessful) {
            return response
        }

        val exception = response.toOrchordsGatewayException()
        response.close()
        throw exception
    }
}

internal fun Response.toOrchordsGatewayException(): OrchordsGatewayException {
    val category = when (code) {
        401, 403 -> GatewayErrorCategory.AUTH
        429 -> GatewayErrorCategory.RATE_LIMIT
        in 400..499 -> GatewayErrorCategory.INVALID_REQUEST
        in 500..599 -> GatewayErrorCategory.GATEWAY_SERVER
        else -> GatewayErrorCategory.UNKNOWN
    }
    val retryAfterSeconds = header("Retry-After")
        ?.takeIf { value -> value.isNotEmpty() && value.all(Char::isDigit) }
        ?.toLongOrNull()

    return OrchordsGatewayException(
        GatewayError(
            category = category,
            httpStatus = code,
            retryable = category == GatewayErrorCategory.RATE_LIMIT ||
                category == GatewayErrorCategory.GATEWAY_SERVER,
            retryAfterSeconds = retryAfterSeconds,
        )
    )
}
