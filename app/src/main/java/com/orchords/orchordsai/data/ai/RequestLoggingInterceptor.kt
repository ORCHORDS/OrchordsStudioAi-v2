package com.orchords.orchordsai.data.ai

import com.orchords.common.android.LogEntry
import com.orchords.common.android.Logging
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

private const val HEADER_PRESENT = "<present>"
private val SAFE_LOG_HEADER_NAMES = listOf(
    "Content-Type",
    "Content-Length",
    "Content-Encoding",
)

class RequestLoggingInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        if (!Logging.isRequestLoggingEnabled()) {
            return chain.proceed(chain.request())
        }

        val request = chain.request()
        val startTime = System.currentTimeMillis()

        val response: Response
        try {
            response = chain.proceed(request)
        } catch (e: Exception) {
            Logging.logRequest(
                buildSafeRequestLog(
                    request = request,
                    durationMs = System.currentTimeMillis() - startTime,
                    error = e,
                )
            )
            throw e
        }

        Logging.logRequest(
            buildSafeRequestLog(
                request = request,
                responseCode = response.code,
                responseHeaders = response.headers,
                durationMs = System.currentTimeMillis() - startTime,
            )
        )

        return response
    }
}

/**
 * Build the only request-log representation allowed to cross into the in-app
 * diagnostics buffer. This is deliberately allowlist-first: request/response
 * bodies, header values, URL credentials/path/query/fragment, and arbitrary
 * exception messages never enter the [LogEntry.RequestLog] object.
 */
internal fun buildSafeRequestLog(
    request: Request,
    responseCode: Int? = null,
    responseHeaders: Headers = Headers.Builder().build(),
    durationMs: Long? = null,
    error: Throwable? = null,
): LogEntry.RequestLog = LogEntry.RequestLog(
    tag = "HTTP",
    url = request.url.toSafeLogOrigin(),
    method = request.method,
    requestHeaders = request.headers.toSafeLogMetadata(),
    requestBody = null,
    responseCode = responseCode,
    responseHeaders = responseHeaders.toSafeLogMetadata(),
    durationMs = durationMs,
    error = error?.toSafeLogErrorType(),
)

/** Keep only route-planning origin data; never retain user-info, path, query or fragment. */
internal fun HttpUrl.toSafeLogOrigin(): String = HttpUrl.Builder()
    .scheme(scheme)
    .host(host)
    .port(port)
    .build()
    .toString()

/**
 * Preserve only the presence of a tiny set of structural HTTP headers.
 * Header values are never copied because even normally-benign headers can be
 * supplied by a user/configuration and therefore cannot be assumed non-secret.
 */
internal fun Headers.toSafeLogMetadata(): Map<String, String> = buildMap {
    SAFE_LOG_HEADER_NAMES.forEach { name ->
        if (this@toSafeLogMetadata[name] != null) {
            put(name, HEADER_PRESENT)
        }
    }
}

internal fun Throwable.toSafeLogErrorType(): String =
    javaClass.simpleName.takeIf { it.isNotBlank() }?.take(80) ?: "Throwable"
