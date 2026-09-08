package com.orchords.orchordsai.data.ai.tools

import com.orchords.ai.core.Tool
import com.orchords.orchordsai.data.datastore.Settings
import com.orchords.search.OrchordsAISearchService
import com.orchords.search.SearchService
import com.orchords.search.SearchServiceOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test

/** Locks the product contract: web search uses OrchordsAI only. */
class CreateSearchToolsRoutingTest {
    @Test
    fun `default search selection resolves to OrchordsAI`() {
        val settings = Settings()
        val tools: Set<Tool> = createSearchTools(settings)
        val search = tools.firstOrNull { it.name == "search_web" }
        assertNotNull(search)
        assertNotNull(search!!.parameters())
        assertSame(OrchordsAISearchService, SearchService.getService(SearchServiceOptions.OrchordsAIOptions()))
    }

    @Test
    fun `legacy search options are routed to OrchordsAI`() {
        val legacy = listOf(
            SearchServiceOptions.BingLocalOptions(),
            SearchServiceOptions.ZhipuOptions(),
            SearchServiceOptions.TavilyOptions(),
            SearchServiceOptions.OrchordsAIOptions(),
        )
        legacy.forEach { option ->
            assertSame(OrchordsAISearchService, SearchService.getService(option))
            val tools = createSearchTools(Settings(searchServices = listOf(option), searchServiceSelected = 0))
            assertEquals(1, tools.count { it.name == "search_web" })
        }
    }
}
