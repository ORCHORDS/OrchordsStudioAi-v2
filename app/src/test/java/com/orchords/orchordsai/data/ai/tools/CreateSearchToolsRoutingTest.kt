package com.orchords.orchordsai.data.ai.tools

import com.orchords.ai.core.Tool
import com.orchords.ai.provider.ProviderSetting
import com.orchords.orchordsai.data.datastore.ORCHORDS_GATEWAY_BASE_URL
import com.orchords.orchordsai.data.datastore.Settings
import com.orchords.search.BraveSearchService
import com.orchords.search.OrchordsAISearchService
import com.orchords.search.SearchService
import com.orchords.search.SearchServiceOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Locks the product contract: oai-1.0 may use verified external search tools. */
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
    fun `selected Brave configuration routes to Brave without gateway credential substitution`() {
        val brave = SearchServiceOptions.BraveOptions(apiKey = "brave-search-key")
        val gateway = ProviderSetting.OpenAI(
            baseUrl = ORCHORDS_GATEWAY_BASE_URL,
            apiKey = "gateway-secret",
        )
        val settings = Settings(
            providers = listOf(gateway),
            searchServices = listOf(SearchServiceOptions.OrchordsAIOptions(), brave),
            searchServiceSelected = 1,
        )

        val effective = settings.activeSearchOptions()

        assertTrue(effective is SearchServiceOptions.BraveOptions)
        assertEquals("brave-search-key", (effective as SearchServiceOptions.BraveOptions).apiKey)
        assertSame(BraveSearchService, SearchService.getService(effective))
    }

    @Test
    fun `unsupported selected legacy provider falls back to configured supported service`() {
        val brave = SearchServiceOptions.BraveOptions(apiKey = "brave-search-key")
        val settings = Settings(
            searchServices = listOf(SearchServiceOptions.BingLocalOptions(), brave),
            searchServiceSelected = 0,
        )

        val effective = settings.activeSearchOptions()

        assertSame(brave, effective)
        assertSame(BraveSearchService, SearchService.getService(effective))
    }

    @Test
    fun `cross origin Orchords search endpoint never inherits gateway credential`() {
        val configured = SearchServiceOptions.OrchordsAIOptions(
            baseUrl = "https://search.example.test/v1/search",
            apiKey = "search-only-key",
        )
        val settings = Settings(
            providers = listOf(
                ProviderSetting.OpenAI(
                    baseUrl = ORCHORDS_GATEWAY_BASE_URL,
                    apiKey = "gateway-secret",
                )
            ),
            searchServices = listOf(configured),
        )

        val effective = settings.activeSearchOptions() as SearchServiceOptions.OrchordsAIOptions

        assertEquals("search-only-key", effective.apiKey)
        assertFalse(configured.baseUrl.canReceiveOrchordsGatewayCredential())
    }

    @Test
    fun `cleartext Orchords host never inherits gateway credential`() {
        val configured = SearchServiceOptions.OrchordsAIOptions(
            baseUrl = "http://api.orchords.com/v1/search",
            apiKey = "search-only-key",
        )
        val settings = Settings(
            providers = listOf(
                ProviderSetting.OpenAI(
                    baseUrl = ORCHORDS_GATEWAY_BASE_URL,
                    apiKey = "gateway-secret",
                )
            ),
            searchServices = listOf(configured),
        )

        assertEquals(
            "search-only-key",
            (settings.activeSearchOptions() as SearchServiceOptions.OrchordsAIOptions).apiKey,
        )
        assertFalse(configured.baseUrl.canReceiveOrchordsGatewayCredential())
    }

    @Test
    fun `non canonical Orchords port never inherits gateway credential`() {
        val configured = SearchServiceOptions.OrchordsAIOptions(
            baseUrl = "https://api.orchords.com:8443/v1/search",
            apiKey = "search-only-key",
        )
        val settings = Settings(
            providers = listOf(
                ProviderSetting.OpenAI(
                    baseUrl = ORCHORDS_GATEWAY_BASE_URL,
                    apiKey = "gateway-secret",
                )
            ),
            searchServices = listOf(configured),
        )

        assertEquals(
            "search-only-key",
            (settings.activeSearchOptions() as SearchServiceOptions.OrchordsAIOptions).apiKey,
        )
        assertFalse(configured.baseUrl.canReceiveOrchordsGatewayCredential())
    }

    @Test
    fun `canonical HTTPS Orchords origin may reuse gateway credential`() {
        val configured = SearchServiceOptions.OrchordsAIOptions(
            baseUrl = "https://api.orchords.com/v1/search",
            apiKey = "search-fallback-key",
        )
        val settings = Settings(
            providers = listOf(
                ProviderSetting.OpenAI(
                    baseUrl = ORCHORDS_GATEWAY_BASE_URL,
                    apiKey = "gateway-key",
                )
            ),
            searchServices = listOf(configured),
        )

        val effective = settings.activeSearchOptions() as SearchServiceOptions.OrchordsAIOptions

        assertTrue(configured.baseUrl.canReceiveOrchordsGatewayCredential())
        assertEquals("gateway-key", effective.apiKey)
    }

    @Test
    fun `existing Orchords search key remains a fallback when gateway key is absent`() {
        val configured = SearchServiceOptions.OrchordsAIOptions(
            baseUrl = "https://api.orchords.com/v1/search",
            apiKey = "existing-search-key",
        )
        val settings = Settings(
            providers = listOf(
                ProviderSetting.OpenAI(baseUrl = ORCHORDS_GATEWAY_BASE_URL, apiKey = "")
            ),
            searchServices = listOf(configured),
            searchServiceSelected = 0,
        )

        assertEquals(
            "existing-search-key",
            (settings.activeSearchOptions() as SearchServiceOptions.OrchordsAIOptions).apiKey,
        )
    }

    @Test
    fun `search tool tells model to synthesize and refine instead of declaring no results`() {
        val search = createSearchTools(Settings()).single { it.name == "search_web" }
        val description = search.description

        assertTrue(description.contains("answer: a search-provider sourced answer"))
        assertTrue(description.contains("run another more targeted search before answering"))
        assertTrue(description.contains("do not claim that search is unavailable"))
        assertTrue(description.contains("answer the user's original question directly"))
    }
}
