package com.orchords.orchordsai.data.ai.mcp

sealed class McpStatus {
    data object Idle : McpStatus()
    data object Connecting : McpStatus()
    data object Connected : McpStatus()
    data class Reconnecting(val attempt: Int, val maxAttempts: Int) : McpStatus()

    data class Error(val message: String, val detail: String? = null) : McpStatus() {
        companion object {
            fun from(throwable: Throwable, fallbackMessage: String? = null): Error {
                val rawSummary = throwable.message?.takeIf { it.isNotBlank() }
                    ?: fallbackMessage
                    ?: throwable.javaClass.simpleName
                val summary = McpDiagnosticSanitizer.sanitize(rawSummary)
                val detail = McpDiagnosticSanitizer.sanitize(throwable.stackTraceToString())
                return Error(message = summary, detail = detail)
            }
        }
    }

    data object NeedsAuthorization : McpStatus()

    data object Authorizing : McpStatus()
}
