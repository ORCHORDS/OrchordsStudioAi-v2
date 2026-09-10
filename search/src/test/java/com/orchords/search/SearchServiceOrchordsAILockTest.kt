package com.orchords.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchServiceOrchordsAILockTest {
    @Test
    fun `default search service remains OrchordsAI`() {
        assertEquals("Orchords Search", SearchServiceOptions.DEFAULT.displayName)
        assertTrue(SearchServiceOptions.DEFAULT is SearchServiceOptions.OrchordsAIOptions)
        assertSame(OrchordsAISearchService, SearchService.getService(SearchServiceOptions.DEFAULT))
    }

    @Test
    fun `Brave is an explicit supported external search tool`() {
        val options = SearchServiceOptions.BraveOptions(apiKey = "search-key")
        assertTrue(SearchService.isRuntimeSupported(options))
        assertSame(BraveSearchService, SearchService.getService(options))
        assertTrue(SearchServiceOptions.BraveOptions::class in SearchServiceOptions.RUNTIME_TYPES)
    }

    @Test
    fun `retired Bing record remains readable but cannot execute as Orchords Search`() {
        val legacy = SearchServiceOptions.BingLocalOptions()
        assertEquals("Bing", legacy.displayName)
        assertFalse(SearchService.isRuntimeSupported(legacy))
        assertSame(UnsupportedSearchService, SearchService.getService(legacy))
    }

    @Test
    fun `runtime type list exposes only verified search adapters`() {
        assertEquals(
            listOf(
                SearchServiceOptions.OrchordsAIOptions::class,
                SearchServiceOptions.BraveOptions::class,
            ),
            SearchServiceOptions.RUNTIME_TYPES,
        )
    }
}
