package com.orchords.orchordsai.data.ai.mcp

internal suspend fun runMcpFoundationContracts(): Int {
    var count = 0
    suspend fun scenario(name: String, block: suspend () -> Unit) {
        block()
        count++
        println("PASS $name")
    }

    suspend fun collect(
        pages: Map<String?, McpCatalogPage<String>>,
        limits: McpCatalogLimits = McpCatalogLimits(),
        seen: MutableList<String?> = mutableListOf(),
    ): List<String> = collectMcpCatalog(limits, { it }) { cursor ->
        seen.add(cursor)
        pages[cursor] ?: error("Unexpected test cursor")
    }

    scenario("all catalog pages are collected in order") {
        val seen = mutableListOf<String?>()
        val result = collect(
            mapOf(
                null to McpCatalogPage(listOf("a"), "next", 5),
                "next" to McpCatalogPage(listOf("b", "c"), null, 8),
            ),
            seen = seen,
        )
        check(result == listOf("a", "b", "c"))
        check(seen == listOf(null, "next"))
    }
    scenario("empty page with cursor is followed") {
        check(
            collect(
                mapOf(
                    null to McpCatalogPage(emptyList(), "", 1),
                    "" to McpCatalogPage(listOf("last"), null, 1),
                )
            ) == listOf("last")
        )
    }
    scenario("repeated cursor fails") {
        check(
            runCatching {
                collect(
                    mapOf(
                        null to McpCatalogPage(listOf("a"), "cursor", 1),
                        "cursor" to McpCatalogPage(listOf("b"), "cursor", 1),
                    )
                )
            }.exceptionOrNull() is McpCatalogException
        )
    }
    scenario("duplicate identity across pages is rejected") {
        check(
            runCatching {
                collect(
                    mapOf(
                        null to McpCatalogPage(listOf("a"), "n", 1),
                        "n" to McpCatalogPage(listOf("a"), null, 1),
                    )
                )
            }.exceptionOrNull() is McpCatalogException
        )
    }
    scenario("item limit fails instead of truncating") {
        check(
            runCatching {
                collect(
                    mapOf(null to McpCatalogPage(listOf("a", "b"), null, 1)),
                    McpCatalogLimits(maxItems = 1),
                )
            }.exceptionOrNull() is McpCatalogException
        )
    }
    scenario("byte budget is cumulative") {
        check(
            runCatching {
                collect(
                    mapOf(
                        null to McpCatalogPage(listOf("a"), "n", 3),
                        "n" to McpCatalogPage(listOf("b"), null, 3),
                    ),
                    McpCatalogLimits(maxBytes = 5),
                )
            }.exceptionOrNull() is McpCatalogException
        )
    }
    return count
}
