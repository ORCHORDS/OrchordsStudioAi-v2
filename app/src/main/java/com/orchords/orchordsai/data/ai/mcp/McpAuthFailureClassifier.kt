package com.orchords.orchordsai.data.ai.mcp

/** Pure classification of MCP/OAuth failures so UI/session policy is testable without Android. */
internal object McpAuthFailureClassifier {
    fun isUnauthorized(error: Throwable): Boolean {
        val message = errorChainText(error)
        return message.contains("401") ||
            message.contains("unauthorized") ||
            message.contains("invalid_token") ||
            message.contains("invalid access token") ||
            message.contains("missing or invalid")
    }

    fun isPermissionDenied(error: Throwable): Boolean {
        val message = errorChainText(error)
        return message.contains("403") ||
            message.contains("forbidden") ||
            message.contains("insufficient_scope") ||
            message.contains("insufficient scope") ||
            message.contains("resource not accessible") ||
            message.contains("permission denied")
    }

    fun isInvalidGrant(error: Throwable): Boolean {
        val message = errorChainText(error)
        return message.contains("invalid_grant") ||
            message.contains("refresh token is invalid") ||
            message.contains("refresh token expired") ||
            message.contains("refresh token revoked")
    }

    private fun errorChainText(error: Throwable): String =
        generateSequence(error) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" ")
            .lowercase()
}
