package com.orchords.orchordsai.data.ai.tools

import com.orchords.ai.core.Tool
import com.orchords.ai.provider.ProviderSetting
import com.orchords.orchordsai.data.datastore.ORCHORDS_GATEWAY_BASE_URL
import com.orchords.orchordsai.data.datastore.ORCHORDS_SEARCH_BASE_URL
import com.orchords.orchordsai.data.datastore.Settings
import com.orchords.search.OrchordsAISearchService
import com.orchords.search.SearchService
import com.orchords.search.SearchServiceOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Locks the product contract: web search uses Orchords Search only. */
class CreateSearchToolsRoutingTest {
    @Test
    fun `default settings resolve to a usable canonical Orchords Search route`() {
        val settings = Settings()
        val effective = settings.activeSearchOptions()
        val tools: Set<Tool> = createSearchTools(settings)
        val search = tools.firstOrNull { it.name == "search_web" }

        assertEquals(ORCHORDS_SEARCH_BASE_URL, effective.baseUrl)
        assertNotNull(search)
        assertNotNull(search!!.parameters())
        assertSame(OrchordsAISearchService, SearchService.getService(effective))
    }

    @Test
    fun `blank first-party search URL is repaired at runtime`() {
        val configured = SearchServiceOptions.OrchordsAIOptions(baseUrl = "")
        val settings = Settings(searchServices = listOf(configured))

        val effective = settings.activeSearchOptions()

        assertEquals(configured.id, effective.id)
        assertEquals(ORCHORDS_SEARCH_BASE_URL, effective.baseUrl)
    }

    @Test
    fun `stale first-party Orchords origin path is normalized to canonical search route`() {
        val configured = SearchServiceOptions.OrchordsAIOptions(
            baseUrl = "https://api.orchords.com/v1/legacy-search",
            depth = "deep",
        )
        val settings = Settings(searchServices = listOf(configured))

        val effective = settings.activeSearchOptions()

        assertEquals(ORCHORDS_SEARCH_BASE_URL, effective.baseUrl)
        assertEquals("deep", effective.depth)
    }

    @Test
    fun `legacy search provider records cannot take over runtime routing`() {
        val configured = SearchServiceOptions.OrchordsAIOptions(
            baseUrl = ORCHORDS_SEARCH_BASE_URL,
            depth = "deep",
            apiKey = "legacy-search-key",
        )
        val gateway = ProviderSetting.OpenAI(
            baseUrl = ORCHORDS_GATEWAY_BASE_URL,
            apiKey = "gateway-key",
        )
        val settings = Settings(
            providers = listOf(gateway),
            searchServices = listOf(SearchServiceOptions.BingLocalOptions(), configured),
            searchServiceSelected = 0,
        )

        val effective = settings.activeSearchOptions()

        assertEquals(configured.id, effective.id)
        assertEquals(configured.baseUrl, effective.baseUrl)
        assertEquals(configured.depth, effective.depth)
        assertEquals("gateway-key", effective.apiKey)
        assertSame(OrchordsAISearchService, SearchService.getService(effective))
    }

    @Test
    fun `cross origin search endpoint never inherits gateway credential`() {
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

        val effective = settings.activeSearchOptions()

        assertEquals("search-only-key", effective.apiKey)
        assertEquals(configured.baseUrl, effective.baseUrl)
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

        assertEquals("search-only-key", settings.activeSearchOptions().apiKey)
        assertEquals(configured.baseUrl, settings.activeSearchOptions().baseUrl)
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

        assertEquals("search-only-key", settings.activeSearchOptions().apiKey)
        assertEquals(configured.baseUrl, settings.activeSearchOptions().baseUrl)
        assertFalse(configured.baseUrl.canReceiveOrchordsGatewayCredential())
    }

    @Test
    fun `canonical HTTPS Orchords origin may reuse gateway credential`() {
        val configured = SearchServiceOptions.OrchordsAIOptions(
            baseUrl = ORCHORDS_SEARCH_BASE_URL,
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

        val effective = settings.activeSearchOptions()

        assertTrue(configured.baseUrl.canReceiveOrchordsGatewayCredential())
        assertEquals("gateway-key", effective.apiKey)
    }

    @Test
    fun `existing Orchords search key remains a migration fallback when gateway key is absent`() {
        val configured = SearchServiceOptions.OrchordsAIOptions(
            baseUrl = ORCHORDS_SEARCH_BASE_URL,
            apiKey = "existing-search-key",
        )
        val settings = Settings(
            providers = listOf(
                ProviderSetting.OpenAI(baseUrl = ORCHORDS_GATEWAY_BASE_URL, apiKey = "")
            ),
            searchServices = listOf(configured),
            searchServiceSelected = 0,
        )

        assertEquals("existing-search-key", settings.activeSearchOptions().apiKey)
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
