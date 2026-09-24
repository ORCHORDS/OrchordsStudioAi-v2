package com.orchords.orchordsai.data.ai.mcp

import kotlin.io.encoding.Base64

/**
 * Reuses the app's existing 20 MiB upload ceiling for inline MCP media.
 * The encoded-length gate avoids allocating an unbounded decoded buffer first.
 */
internal const val MAX_MCP_INLINE_MEDIA_BYTES: Int = 20 * 1024 * 1024

internal fun decodeBoundedMcpBase64(
    data: String,
    maxDecodedBytes: Int = MAX_MCP_INLINE_MEDIA_BYTES,
): ByteArray {
    require(maxDecodedBytes > 0) { "maxDecodedBytes must be positive" }
    val maxEncodedChars = ((maxDecodedBytes.toLong() + 2L) / 3L) * 4L
    require(data.length.toLong() <= maxEncodedChars) { "MCP inline media exceeds the configured byte limit" }

    val decoded = Base64.decode(data)
    require(decoded.size <= maxDecodedBytes) { "MCP inline media exceeds the configured byte limit" }
    return decoded
}
