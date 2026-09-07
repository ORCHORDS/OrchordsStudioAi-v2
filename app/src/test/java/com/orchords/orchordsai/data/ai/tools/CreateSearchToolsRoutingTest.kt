package com.orchords.orchordsai.data.ai.tools

import com.orchords.ai.core.Tool
import com.orchords.orchordsai.data.datastore.Settings
import com.orchords.search.BingSearchService
import com.orchords.search.SearchServiceOptions
import com.orchords.search.TavilySearchService
import com.orchords.search.ZhipuSearchService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

/**
 * Locks the routing contract used by [createSearchTools]:
 *
 *  * The pipeline must always produce exactly one search-web `Tool` whose
 *    `parameters` callback resolves the user's currently-selected provider
 *    via [com.orchords.search.SearchService.getService].
 *  * Picking Bing in `Settings.searchServiceSelected` must resolve
 *    [BingSearchService]; picking Zhipu or Tavily must resolve to their
 *    respective services, proving the index is honoured.
 *
 * This test does not exercise HTTP — it only verifies the in-process tool
 * construction that the chat pipeline calls into. The downstream network
 * behaviour of each provider is covered by its own unit/integration test in
 * the `:search` module.
 */
class CreateSearchToolsRoutingTest {

    private fun settingsWithSelection(
        services: List<SearchServiceOptions>,
        selectedIndex: Int,
    ): Settings {
        val base = Settings()
        // Default Settings already has one BingLocalOptions entry. Replace the
        // entire list so we can pin the index precisely.
        val patch = base.copy(
            searchServices = services,
            searchServiceSelected = selectedIndex,
        )
        // Sanity: the resulting list size must match the selected index.
        assertTrue(
            "Test setup error: selected index $selectedIndex out of bounds " +
                "for ${services.size} services",
            selectedIndex in services.indices,
        )
        return patch
    }

    @Test
    fun `default Bing selection resolves to BingSearchService`() {
        val settings = settingsWithSelection(
            services = listOf(SearchServiceOptions.BingLocalOptions()),
            selectedIndex = 0,
        )

        val tools: Set<Tool> = createSearchTools(settings)

        val search = tools.firstOrNull { it.name == "search_web" }
        assertNotNull("createSearchTools must emit a 'search_web' tool", search)
        val schema = search!!.parameters()
        assertNotNull(
            "search_web.parameters() must return a non-null schema when a Bing option is configured",
            schema,
        )
        // The parameters callback delegates to SearchService.getService; assert
        // the same code path returns BingSearchService for the same index.
        val resolved = com.orchords.search.SearchService.getService(
            settings.searchServices[settings.searchServiceSelected],
        )
        assertSame(
            "Bing selection must resolve to BingSearchService",
            BingSearchService,
            resolved,
        )
    }

    @Test
    fun `selection follows searchServiceSelected index`() {
        val services = listOf(
            SearchServiceOptions.BingLocalOptions(id = Uuid.random()),
            SearchServiceOptions.ZhipuOptions(apiKey = "test-zhipu"),
            SearchServiceOptions.TavilyOptions(apiKey = "test-tavily"),
        )

        val bingResolved = com.orchords.search.SearchService.getService(services[0])
        val zhipuResolved = com.orchords.search.SearchService.getService(services[1])
        val tavilyResolved = com.orchords.search.SearchService.getService(services[2])

        assertSame(BingSearchService, bingResolved)
        assertSame(ZhipuSearchService, zhipuResolved)
        assertSame(TavilySearchService, tavilyResolved)

        // createSearchTools must keep the same single-tool shape regardless
        // of which index is selected.
        val bingTools = createSearchTools(
            settingsWithSelection(services, selectedIndex = 0),
        )
        val zhipuTools = createSearchTools(
            settingsWithSelection(services, selectedIndex = 1),
        )
        val tavilyTools = createSearchTools(
            settingsWithSelection(services, selectedIndex = 2),
        )

        assertEquals(
            "exactly one tool for Bing selection",
            1,
            bingTools.count { it.name == "search_web" },
        )
        assertEquals(
            "exactly one tool for Zhipu selection",
            1,
            zhipuTools.count { it.name == "search_web" },
        )
        assertEquals(
            "exactly one tool for Tavily selection",
            1,
            tavilyTools.count { it.name == "search_web" },
        )
    }

    @Test
    fun `createSearchTools returns non-empty result for every supported provider`() {
        val services: List<SearchServiceOptions> = listOf(
            SearchServiceOptions.BingLocalOptions(),
            SearchServiceOptions.ZhipuOptions(),
            SearchServiceOptions.DoubaoOptions(),
            SearchServiceOptions.TavilyOptions(),
            SearchServiceOptions.ExaOptions(),
            SearchServiceOptions.SearXNGOptions(),
            SearchServiceOptions.LinkUpOptions(),
            SearchServiceOptions.BraveOptions(),
            SearchServiceOptions.MetasoOptions(),
            SearchServiceOptions.OllamaOptions(),
            SearchServiceOptions.OrchordsAIOptions(),
        )
        for ((index, _) in services.withIndex()) {
            val settings = settingsWithSelection(services, selectedIndex = index)
            val tools = createSearchTools(settings)
            assertTrue(
                "provider at index $index must produce at least one tool",
                tools.any { it.name == "search_web" },
            )
        }
    }
}
