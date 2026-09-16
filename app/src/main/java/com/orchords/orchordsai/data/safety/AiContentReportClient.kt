package com.orchords.orchordsai.data.safety

import com.orchords.ai.ui.UIMessage
import com.orchords.ai.ui.UIMessagePart
import com.orchords.orchordsai.BuildConfig
import com.orchords.orchordsai.utils.JsonInstant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.UUID

private const val REPORT_ENDPOINT = "https://orchords.com/api/ai-report"
private const val MAX_RENDERED_OUTPUT = 6000
private const val MAX_NOTE = 1200
private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

@Serializable
data class AiContentReportPayload(
    val category: String,
    val output: String,
    val note: String = "",
    val model: String = "",
    val provider: String = "",
    val appVersion: String = BuildConfig.VERSION_NAME,
    val platform: String = "Android",
    val reportId: String = UUID.randomUUID().toString(),
)

sealed interface AiContentReportResult {
    data class Success(val reference: String) : AiContentReportResult
    data class RateLimited(val retryAfterSeconds: Int?) : AiContentReportResult
    data object Unavailable : AiContentReportResult
    data object Rejected : AiContentReportResult
}

/**
 * Build the reportable representation of an assistant message.
 *
 * Only visible text parts are included. Reasoning, tool calls/results,
 * attachments, local file paths and unrelated conversation messages are not
 * projected into a report payload.
 */
fun visibleAssistantOutputForReport(message: UIMessage): String =
    message.parts
        .filterIsInstance<UIMessagePart.Text>()
        .joinToString("\n\n") { it.text }
        .trim()
        .take(MAX_RENDERED_OUTPUT)

fun buildAiContentReportPayload(
    message: UIMessage,
    category: String,
    note: String,
    model: String,
    provider: String,
    reportId: String = UUID.randomUUID().toString(),
): AiContentReportPayload = AiContentReportPayload(
    category = category,
    output = visibleAssistantOutputForReport(message),
    note = note.trim().take(MAX_NOTE),
    model = model.take(160),
    provider = provider.take(160),
    reportId = reportId.take(160),
)

class AiContentReportClient(
    private val httpClient: OkHttpClient,
) {
    suspend fun submit(payload: AiContentReportPayload): AiContentReportResult =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(REPORT_ENDPOINT)
                .header("Accept", "application/json")
                .post(JsonInstant.encodeToString(payload).toRequestBody(JSON_MEDIA_TYPE))
                .build()

            try {
                httpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    when {
                        response.isSuccessful -> {
                            val reference = runCatching {
                                JsonInstant.parseToJsonElement(body)
                                    .jsonObject["id"]
                                    ?.jsonPrimitive
                                    ?.content
                            }.getOrNull().orEmpty()
                            if (reference.isBlank()) AiContentReportResult.Unavailable
                            else AiContentReportResult.Success(reference)
                        }

                        response.code == 429 -> {
                            val retryAfter = response.header("Retry-After")?.toIntOrNull()
                            AiContentReportResult.RateLimited(retryAfter)
                        }

                        response.code in 400..499 -> AiContentReportResult.Rejected
                        else -> AiContentReportResult.Unavailable
                    }
                }
            } catch (_: IOException) {
                AiContentReportResult.Unavailable
            }
        }
}
