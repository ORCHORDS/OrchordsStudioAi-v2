package com.orchords.ai.provider

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Controls whether OpenAI Chat Completions tool-result messages include the
 * optional compatibility `name` member.
 *
 * AUTO resolves to a deterministic OMIT for every host. The first-party
 * oai-1.0 contract never relies on a name field; non-Orchards OpenAI-compatible
 * endpoints must explicitly opt in with INCLUDE if their bridge requires it.
 * Arbitrary OpenAI-compatible endpoints are out of scope for AUTO (#353).
 */
@Serializable
enum class ToolResultNameMode {
    @SerialName("auto")
    AUTO,

    @SerialName("include")
    INCLUDE,

    @SerialName("omit")
    OMIT,
}

internal fun resolveToolResultName(
    mode: ToolResultNameMode,
    host: String,
): Boolean = when (mode) {
    ToolResultNameMode.INCLUDE -> true
    ToolResultNameMode.OMIT -> false
    ToolResultNameMode.AUTO -> false
}
