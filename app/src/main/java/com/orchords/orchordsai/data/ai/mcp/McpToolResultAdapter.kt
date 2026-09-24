package com.orchords.orchordsai.data.ai.mcp

import com.orchords.ai.ui.UIMessagePart
import com.orchords.orchordsai.utils.JsonInstant
import io.modelcontextprotocol.kotlin.sdk.types.AudioContent
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.EmbeddedResource
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.ResourceLink
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
    renderEmbeddedResource: suspend (EmbeddedResource) -> UIMessagePart,
): List<UIMessagePart> {
    val result = McpExecutionResult(content, structuredContent, isError == true, meta)
    return result.projectForConversation(
        renderContent = { block ->
            when (block) {
                is TextContent -> renderMcpPayload("text") {
                    UIMessagePart.Text(requireBoundedMcpText(block.text))
                }
                is ImageContent -> renderMcpPayload("image") { renderImage(block) }
                is AudioContent -> renderMcpPayload("audio") { renderAudio(block) }
                is ResourceLink -> renderMcpPayload("resource_link") {
                    UIMessagePart.McpResource(
                        kind = com.orchords.ai.ui.McpResourceKind.LINK,
                        uri = requireBoundedMcpText(block.uri),
                        name = block.name.takeIf { it.isNotBlank() }?.let(::requireBoundedMcpText),
                        title = block.title?.let(::requireBoundedMcpText),
                        description = block.description?.let(::requireBoundedMcpText),
                        mimeType = block.mimeType?.let(::requireBoundedMcpText),
                        size = block.size,
                    )
                }
                is EmbeddedResource -> renderMcpPayload("embedded_resource") {
                    renderEmbeddedResource(block)
                }
                else -> renderMcpPayload("unknown") {
                    UIMessagePart.Text(
                        requireBoundedMcpText(
                            JsonInstant.encodeToJsonElement(block)
                                .withoutMcpProtocolMetadata()
                                .toString()
                        )
                    )
                }
            }
        },
        renderStructured = { payload ->
            UIMessagePart.McpStructured(requireBoundedMcpJson(payload))
        },
        renderStatus = { error ->
            UIMessagePart.McpResultStatus(isError = error)
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

private suspend fun renderMcpPayload(
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
