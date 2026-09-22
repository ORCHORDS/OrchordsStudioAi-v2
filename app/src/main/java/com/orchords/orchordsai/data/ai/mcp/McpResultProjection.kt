package com.orchords.orchordsai.data.ai.mcp

/**
 * Runtime-only result boundary. Host metadata is intentionally not a conversation part.
 * Generic types keep this policy independent of the Android UI and MCP transport version.
 * Do not make this class serializable or log content/metadata through toString().
 */
internal class McpExecutionResult<C, S, H>(
    content: List<C>,
    val structuredContent: S?,
    val isError: Boolean,
    val hostMetadata: H?,
) {
    val content: List<C> = content.toList()

    override fun toString(): String =
        "McpExecutionResult(blocks=${content.size}, structured=${structuredContent != null}, isError=$isError)"
}

/**
 * Preserve both model-visible channels without ever passing host metadata to a renderer.
 * An explicit acknowledgement keeps a successful empty MCP result from looking unexecuted
 * to legacy callers that still infer execution from non-empty output (#26).
 */
internal suspend fun <C, S, H, P> McpExecutionResult<C, S, H>.projectForConversation(
    renderContent: suspend (C) -> P,
    renderStructured: (S) -> P,
    renderStatus: (isError: Boolean) -> P,
): List<P> = buildList {
    if (isError || (content.isEmpty() && structuredContent == null)) {
        add(renderStatus(isError))
    }
    content.forEach { add(renderContent(it)) }
    structuredContent?.let { add(renderStructured(it)) }
}
