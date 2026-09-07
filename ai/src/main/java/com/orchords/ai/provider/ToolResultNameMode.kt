package com.orchords.ai.provider

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Controls whether OpenAI Chat Completions tool-result messages include the
 * optional compatibility `name` member.
 *
 * AUTO follows known route behavior: native OpenAI omits the field, while the
 * Gemini OpenAI-compatible bridge requires the provider-visible function name.
 * Unknown compatible endpoints conservatively omit it and can explicitly opt
 * in with INCLUDE. No compatibility retry is performed after network I/O.
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
    ToolResultNameMode.AUTO -> host == "generativelanguage.googleapis.com"
}
