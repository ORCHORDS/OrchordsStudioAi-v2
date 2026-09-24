package com.orchords.orchordsai.data.ai.mcp

import com.orchords.ai.ui.UIMessagePart
import com.orchords.orchordsai.utils.JsonInstant
import io.modelcontextprotocol.kotlin.sdk.types.AudioContent
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

/** Adapter for the pinned MCP SDK 0.15.0 object-valued structured-result contract. */
internal suspend fun CallToolResult.toConversationParts(
    renderImage: suspend (ImageContent) -> UIMessagePart,
    renderAudio: suspend (AudioContent) -> UIMessagePart,
): List<UIMessagePart> {
    val result = McpExecutionResult(content, structuredContent, isError == true, meta)
    return result.projectForConversation(
        renderContent = { block ->
            when (block) {
                is TextContent -> UIMessagePart.Text(block.text)
                is ImageContent -> renderMcpMedia("image") { renderImage(block) }
                is AudioContent -> renderMcpMedia("audio") { renderAudio(block) }
                else -> UIMessagePart.Text(
                    JsonInstant.encodeToJsonElement(block).withoutMcpProtocolMetadata().toString()
                )
            }
        },
        renderStructured = { payload ->
            UIMessagePart.Text(buildJsonObject { put("structuredContent", payload) }.toString())
        },
        renderStatus = { error ->
            UIMessagePart.Text(buildJsonObject {
                put("isError", error)
                put("status", if (error) "tool_error" else "completed")
            }.toString())
        },
    )
}

/**
 * Protocol _meta belongs to the host, not to the conversation/model. Only strip known
 * protocol positions; a user dataset in structuredContent may legitimately contain _meta.
 * Resource URIs remain references. This projection never follows them or downloads files.
 */
private fun JsonElement.withoutMcpProtocolMetadata(): JsonElement {
    val block = this as? JsonObject ?: return this
    return JsonObject(block.filterKeys { it != "_meta" }.mapValues { (key, value) ->
        if (key == "resource" && value is JsonObject) {
            JsonObject(value.filterKeys { it != "_meta" })
        } else {
            value
        }
    })
}

private suspend fun renderMcpMedia(
    contentType: String,
    render: suspend () -> UIMessagePart,
): UIMessagePart = try {
    render()
} catch (error: CancellationException) {
    throw error
} catch (_: Exception) {
    UIMessagePart.Text(buildJsonObject {
        put("contentType", contentType)
        put("status", "omitted")
        put("reason", "invalid_or_too_large")
    }.toString())
}
