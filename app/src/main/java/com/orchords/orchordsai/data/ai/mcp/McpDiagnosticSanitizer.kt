package com.orchords.orchordsai.data.ai.mcp

/**
 * Removes credential-shaped values from MCP/OAuth diagnostics before they
 * reach UI, clipboard or status detail surfaces. Keep this conservative:
 * losing a little debug detail is preferable to exposing a token.
 */
object McpDiagnosticSanitizer {
    private const val REDACTED = "[REDACTED]"

    private val headerPatterns = listOf(
        Regex("(?im)(Authorization\\s*:\\s*)(?:Bearer|Basic)?\\s*[^\\s,;]+"),
        Regex("(?im)(Proxy-Authorization\\s*:\\s*)(?:Bearer|Basic)?\\s*[^\\s,;]+"),
        Regex("(?im)(Cookie\\s*:\\s*)[^\\r\\n]+"),
    )

    private val structuredSecret = Regex(
        "(?i)([\"']?(?:access_token|refresh_token|client_secret|api[_-]?key|token)[\"']?\\s*[:=]\\s*[\"']?)([^\"'\\s&,;}]+)"
    )

    private val urlSecret = Regex(
        "(?i)([?&](?:access_token|refresh_token|client_secret|api[_-]?key|token|code)=)([^&#\\s]+)"
    )

    private val githubToken = Regex(
        "(?i)\\b(?:github_pat_[A-Za-z0-9_]+|gh[pousr]_[A-Za-z0-9]+)\\b"
    )

    fun sanitize(value: String): String {
        var result = value
        headerPatterns.forEach { pattern ->
            result = pattern.replace(result) { match ->
                match.groupValues[1] + REDACTED
            }
        }
        result = urlSecret.replace(result) { match -> match.groupValues[1] + REDACTED }
        result = structuredSecret.replace(result) { match -> match.groupValues[1] + REDACTED }
        result = githubToken.replace(result, REDACTED)
        return result
    }
}
