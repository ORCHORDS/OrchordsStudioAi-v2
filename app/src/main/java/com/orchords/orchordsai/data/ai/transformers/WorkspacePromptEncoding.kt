package com.orchords.orchordsai.data.ai.transformers

import kotlinx.serialization.json.JsonPrimitive
import com.orchords.orchordsai.data.ai.tools.normalizeRootfsPath

internal fun encodeWorkspacePromptMetadata(value: String): String = JsonPrimitive(value).toString()

/** Canonicalize persisted/restored cwd before presenting it as trusted Rootfs execution context. */
internal fun normalizeWorkspacePromptCwd(value: String?): String? {
    val raw = value?.takeIf { it.isNotBlank() } ?: return null
    return runCatching { normalizeRootfsPath(raw) }.getOrNull()
}
