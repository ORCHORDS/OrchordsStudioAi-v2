package com.orchords.ai.provider

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Request
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
 * Keep exactly one Authorization value on first-party gateway requests.
 *
 * OpenAI-compatible request builders apply user/model custom headers before
 * adding the persisted provider credential. If a stale custom Authorization
 * header is present, OkHttp can otherwise carry both values. The final value is
 * the provider credential added by the request builder, so that value wins.
 */
internal fun Request.normalizeOrchordsGatewayAuthorization(): Request {
    if (url.host != ORCHORDS_GATEWAY_HOST) return this
    val authorizationValues = headers.values("Authorization")
    if (authorizationValues.size <= 1) return this

    return newBuilder()
        .header("Authorization", authorizationValues.last())
        .build()
}

/**
 * First-party HTTP failure boundary.
 *
 * Deliberately does not read or parse the response body. The Orchords gateway's
 * error-body schema is not a confirmed product contract, so arbitrary JSON/HTML
 * cannot become exception/UI/log content. Status and Retry-After are safe HTTP
 * metadata and are sufficient for the currently verified classifications.
 */
internal class OrchordsGatewayErrorInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().normalizeOrchordsGatewayAuthorization()
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
