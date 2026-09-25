package com.orchords.ai.ui

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import com.orchords.ai.util.json
import kotlin.time.Clock
import kotlin.time.Instant

@Serializable
sealed class ToolApprovalState {
    @Serializable
    @SerialName("auto")
    data object Auto : ToolApprovalState()

    @Serializable
    @SerialName("pending")
    data object Pending : ToolApprovalState()

    @Serializable
    @SerialName("approved")
    data object Approved : ToolApprovalState()

    @Serializable
    @SerialName("denied")
    data class Denied(val reason: String = "") : ToolApprovalState()

    @Serializable
    @SerialName("answered")
    data class Answered(val answer: String) : ToolApprovalState()
}

fun ToolApprovalState.canResumeToolExecution(): Boolean {
    return when (this) {
        ToolApprovalState.Approved -> true
        is ToolApprovalState.Denied -> true
        is ToolApprovalState.Answered -> true
        ToolApprovalState.Auto,
        ToolApprovalState.Pending,
            -> false
    }
}

/**
 *
 */
@Serializable
enum class ToolExecutionState {
    @SerialName("prepared")
    PREPARED,

    @SerialName("awaiting_approval")
    AWAITING_APPROVAL,

    @SerialName("awaiting_auth")
    AWAITING_AUTH,

    @SerialName("submitted")
    SUBMITTED,

    @SerialName("running")
    RUNNING,

    @SerialName("succeeded")
    SUCCEEDED,

    @SerialName("failed")
    FAILED,

    @SerialName("cancel_requested")
    CANCEL_REQUESTED,

    @SerialName("cancelled")
    CANCELLED,

    @SerialName("outcome_unknown")
    OUTCOME_UNKNOWN,
}

@Serializable
enum class ServerToolStatus {
    @SerialName("in_progress")
    IN_PROGRESS,

    @SerialName("completed")
    COMPLETED,

    @SerialName("failed")
    FAILED,
}

/** MCP resource representation kept separate from generic documents to avoid implicit fetches. */
@Serializable
enum class McpResourceKind {
    @SerialName("link")
    LINK,

    @SerialName("embedded_text")
    EMBEDDED_TEXT,

    @SerialName("embedded_blob")
    EMBEDDED_BLOB,

    @SerialName("embedded_unknown")
    EMBEDDED_UNKNOWN,
}

/** The kind of text carried by a reasoning part. */
@Serializable
enum class ReasoningType {
    @SerialName("reasoning_text")
    REASONING_TEXT,

    @SerialName("summary_text")
    SUMMARY_TEXT,
}

@Serializable
sealed class UIMessagePart {
    abstract val metadata: JsonObject?

    @Serializable
    @SerialName("text")
    data class Text(
        val text: String,
        override var metadata: JsonObject? = null
    ) : UIMessagePart()

    @Serializable
    @SerialName("image")
    data class Image(
        val url: String,
        override var metadata: JsonObject? = null
    ) : UIMessagePart()

    @Serializable
    @SerialName("video")
    data class Video(
        val url: String,
        override var metadata: JsonObject? = null
    ) : UIMessagePart()

    @Serializable
    @SerialName("audio")
    data class Audio(
        val url: String,
        override var metadata: JsonObject? = null
    ) : UIMessagePart()

    @Serializable
    @SerialName("document")
    data class Document(
        val url: String,
        val fileName: String,
        val mime: String = "text/*",
        override var metadata: JsonObject? = null
    ) : UIMessagePart()

    @Serializable
    @SerialName("mcp_result_status")
    data class McpResultStatus(
        val isError: Boolean,
        override var metadata: JsonObject? = null,
    ) : UIMessagePart() {
        fun modelFallbackText(): String =
            """{"isError":$isError,"status":"${if (isError) "tool_error" else "completed"}"}"""
    }

    @Serializable
    @SerialName("mcp_structured")
    data class McpStructured(
        val content: JsonElement,
        override var metadata: JsonObject? = null,
    ) : UIMessagePart() {
        fun modelFallbackText(): String = """{"structuredContent":$content}"""
    }

    @Serializable
    @SerialName("mcp_resource")
    data class McpResource(
        val kind: McpResourceKind,
        val uri: String,
        val name: String? = null,
        val title: String? = null,
        val description: String? = null,
        val mimeType: String? = null,
        val size: Long? = null,
        val text: String? = null,
        /** Managed local copy only for embedded binary content; never a fetched remote resource. */
        val localUrl: String? = null,
        override var metadata: JsonObject? = null,
    ) : UIMessagePart() {
        fun modelFallbackText(): String = buildString {
            append("[MCP resource ")
            append(kind.name.lowercase())
            append("] ")
            append(title?.takeIf { it.isNotBlank() } ?: name?.takeIf { it.isNotBlank() } ?: uri)
            append(" (")
            append(uri)
            append(")")
            mimeType?.takeIf { it.isNotBlank() }?.let { append(" mime=").append(it) }
            text?.takeIf { it.isNotBlank() }?.let { append("\n").append(it) }
        }
    }

    @Serializable
    @SerialName("reasoning")
    data class Reasoning(
        val reasoning: String,
        val createdAt: Instant = Clock.System.now(),
        val finishedAt: Instant? = Clock.System.now(),
        override var metadata: JsonObject? = null,
        val reasoningType: ReasoningType = ReasoningType.REASONING_TEXT,
    ) : UIMessagePart()

    @Deprecated("Deprecated")
    @Serializable
    @SerialName("search")
    data object Search : UIMessagePart() {
        override var metadata: JsonObject? = null
    }

    @Deprecated("Use UIMessagePart.Tool instead")
    @Serializable
    @SerialName("tool_call")
    data class ToolCall(
        val toolCallId: String,
        val toolName: String,
        val arguments: String,
        val approvalState: ToolApprovalState = ToolApprovalState.Auto,
        override var metadata: JsonObject? = null
    ) : UIMessagePart() {
        fun merge(other: ToolCall): ToolCall {
            return ToolCall(
                toolCallId = toolCallId,
                toolName = toolName + other.toolName,
                arguments = arguments + other.arguments,
                approvalState = approvalState,
                metadata = if (other.metadata != null) other.metadata else metadata,
            )
        }
    }

    @Deprecated("Use UIMessagePart.Tool instead")
    @Serializable
    @SerialName("tool_result")
    data class ToolResult(
        val toolCallId: String,
        val toolName: String,
        val content: JsonElement,
        val arguments: JsonElement,
        override var metadata: JsonObject? = null
    ) : UIMessagePart()

    /**
     *
     */
    @Serializable
    @SerialName("server_tool")
    data class ServerTool(
        val toolCallId: String,
        val toolName: String,
        val input: JsonElement? = null,
        val output: JsonElement? = null,
        val status: ServerToolStatus,
        override var metadata: JsonObject? = null,
    ) : UIMessagePart() {
        val isFinished: Boolean
            get() = status == ServerToolStatus.COMPLETED || status == ServerToolStatus.FAILED
    }

    @Serializable
    @SerialName("tool")
    data class Tool(
        val toolCallId: String,
        val toolName: String,
        val input: String,
        val output: List<UIMessagePart> = emptyList(),
        val approvalState: ToolApprovalState = ToolApprovalState.Auto,
        override var metadata: JsonObject? = null,
        /**
         * Explicit terminal marker for new executions. Null preserves backward compatibility:
         * legacy persisted tools still infer completion from non-empty output.
         */
        val executionCompleted: Boolean? = null,
        /** Explicit lifecycle for new persisted executions; null keeps legacy messages readable. */
        val executionState: ToolExecutionState? = null,
    ) : UIMessagePart() {
        /** Whether this tool call reached a terminal local execution outcome. */
        val isExecuted: Boolean get() = when (executionState) {
            ToolExecutionState.SUCCEEDED,
            ToolExecutionState.FAILED,
            ToolExecutionState.CANCELLED,
                -> true
            ToolExecutionState.PREPARED,
            ToolExecutionState.AWAITING_APPROVAL,
            ToolExecutionState.AWAITING_AUTH,
            ToolExecutionState.SUBMITTED,
            ToolExecutionState.RUNNING,
            ToolExecutionState.CANCEL_REQUESTED,
            ToolExecutionState.OUTCOME_UNKNOWN,
                -> false
            null -> executionCompleted ?: output.isNotEmpty()
        }

        /** Whether the tool is pending user approval */
        val isPending: Boolean get() =
            executionState == ToolExecutionState.AWAITING_APPROVAL ||
                approvalState is ToolApprovalState.Pending

        /** Whether generation can resume and handle this tool immediately. */
        val canResumeExecution: Boolean get() =
            !isExecuted &&
                executionState !in setOf(
                    ToolExecutionState.AWAITING_APPROVAL,
                    ToolExecutionState.AWAITING_AUTH,
                    ToolExecutionState.SUBMITTED,
                    ToolExecutionState.RUNNING,
                    ToolExecutionState.CANCEL_REQUESTED,
                    ToolExecutionState.OUTCOME_UNKNOWN,
                ) &&
                approvalState.canResumeToolExecution()

        /** Parse input string as JsonElement */
        fun inputAsJson(): JsonElement = runCatching {
            json.parseToJsonElement(input.ifBlank { "{}" })
        }.getOrElse { JsonObject(emptyMap()) }

        fun merge(other: Tool): Tool {
            return Tool(
                toolCallId = toolCallId,
                toolName = toolName + other.toolName,
                input = input + other.input,
                output = output + other.output,
                approvalState = approvalState,
                metadata = if (other.metadata != null) other.metadata else metadata,
                executionCompleted = other.executionCompleted ?: executionCompleted,
                executionState = other.executionState ?: executionState,
            )
        }
    }
}
