package com.orchords.orchordsai.data.ai.mcp

/** Local safety budgets, not vendor limits. Byte counts describe decoded/encoded page size. */
internal data class McpCatalogLimits(
    val maxPages: Int = 64,
    val maxItems: Int = 4096,
    val maxBytes: Long = 8L * 1024 * 1024,
    val maxCursorChars: Int = 4096,
) {
    init {
        require(maxPages > 0 && maxItems > 0 && maxBytes > 0 && maxCursorChars > 0)
    }
}

internal data class McpCatalogPage<T>(
    val items: List<T>,
    val nextCursor: String?,
    val encodedBytes: Long,
)

internal enum class McpCatalogFailure {
    PAGE_LIMIT,
    ITEM_LIMIT,
    BYTE_LIMIT,
    INVALID_BYTE_COUNT,
    CONTINUATION_TOO_LONG,
    REPEATED_CONTINUATION,
    INVALID_IDENTITY,
    DUPLICATE_IDENTITY,
    TIME_LIMIT,
}

internal class McpCatalogException(val reason: McpCatalogFailure) :
    IllegalStateException("MCP catalog could not be completed: ${reason.name}")

/**
 * Returns a complete bounded catalog or throws. The caller publishes only the returned list,
 * so transport failure, cancellation and limit violations cannot publish a partial catalog.
 * Continuation tokens are opaque: an empty token is not the same as an absent token.
 */
internal suspend fun <T> collectMcpCatalog(
    limits: McpCatalogLimits,
    identity: (T) -> String,
    fetchPage: suspend (cursor: String?) -> McpCatalogPage<T>,
): List<T> {
    val items = ArrayList<T>()
    val identities = HashSet<String>()
    val continuations = HashSet<String>()
    var cursor: String? = null
    var bytes = 0L
    var pages = 0
    while (true) {
        if (pages >= limits.maxPages) throw McpCatalogException(McpCatalogFailure.PAGE_LIMIT)
        val page = fetchPage(cursor)
        pages++
        if (page.encodedBytes < 0) throw McpCatalogException(McpCatalogFailure.INVALID_BYTE_COUNT)
        if (page.encodedBytes > limits.maxBytes - bytes) throw McpCatalogException(McpCatalogFailure.BYTE_LIMIT)
        bytes += page.encodedBytes
        if (page.items.size > limits.maxItems - items.size) throw McpCatalogException(McpCatalogFailure.ITEM_LIMIT)
        page.items.forEach { item ->
            val id = identity(item)
            if (id.isBlank()) throw McpCatalogException(McpCatalogFailure.INVALID_IDENTITY)
            if (!identities.add(id)) throw McpCatalogException(McpCatalogFailure.DUPLICATE_IDENTITY)
            items.add(item)
        }
        val next = page.nextCursor ?: return items.toList()
        if (next.length > limits.maxCursorChars) throw McpCatalogException(McpCatalogFailure.CONTINUATION_TOO_LONG)
        if (!continuations.add(next)) throw McpCatalogException(McpCatalogFailure.REPEATED_CONTINUATION)
        cursor = next
    }
}
