package com.orchords.orchordsai.data.repository

/**
 * Reconstructs one legacy inline MessageNode payload through bounded projected chunks.
 *
 * This is used only when a normal row read cannot fit the platform CursorWindow. The caller
 * supplies the scalar UTF-8 byte length first, so an absurd/corrupt row is rejected before
 * any text chunk is materialized. The observed bytes must still match that snapshot to avoid
 * decoding a row that changed while chunks were read.
 */
internal suspend fun readLegacyInlinePayload(
    expectedUtf8Bytes: Long,
    maxUtf8Bytes: Long,
    chunkChars: Int,
    readChunk: suspend (startChar: Int, maxChars: Int) -> String?,
): String? {
    require(maxUtf8Bytes > 0)
    require(chunkChars > 0)
    if (expectedUtf8Bytes < 0 || expectedUtf8Bytes > maxUtf8Bytes) return null

    val result = StringBuilder(minOf(expectedUtf8Bytes, Int.MAX_VALUE.toLong()).toInt())
    var startChar = 0
    var observedUtf8Bytes = 0L
    while (true) {
        val chunk = readChunk(startChar, chunkChars) ?: return null
        if (chunk.isEmpty()) break

        observedUtf8Bytes += chunk.toByteArray(Charsets.UTF_8).size
        if (observedUtf8Bytes > expectedUtf8Bytes || observedUtf8Bytes > maxUtf8Bytes) return null
        result.append(chunk)
        startChar += chunk.length
    }

    if (observedUtf8Bytes != expectedUtf8Bytes) return null
    return result.toString()
}
