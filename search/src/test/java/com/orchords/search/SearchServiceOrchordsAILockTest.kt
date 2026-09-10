package com.orchords.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SearchServiceOrchordsAILockTest {
    @Test
    fun `default search service options are OrchordsAI`() {
        assertEquals("Orchords Search", SearchServiceOptions.DEFAULT.displayName)
        org.junit.Assert.assertTrue(SearchServiceOptions.DEFAULT is SearchServiceOptions.OrchordsAIOptions)
    }

    @Test
    fun `getService returns OrchordsAISearchService for the default options`() {
        val service = SearchService.getService(SearchServiceOptions.DEFAULT)
        assertSame(OrchordsAISearchService, service)
    }

    @Test
    fun `getService ignores legacy option types and always returns OrchordsAISearchService`() {
        // Stored options from older installs must still resolve to OrchordsAI.
        val legacy = SearchServiceOptions.BingLocalOptions()
        assertSame(OrchordsAISearchService, SearchService.getService(legacy))
    }

    @Test
    fun `displayName registry only exposes Orchords Search for OrchordsAIOptions`() {
        assertEquals("Orchords Search", SearchServiceOptions.TYPES[SearchServiceOptions.OrchordsAIOptions::class])
    }
}
