package com.orchords.orchordsai.data.extensions

import com.orchords.orchordsai.data.ai.planning.PLANNING_MODE_ID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryCatalogPreviewTest {
    @Test
    fun `expanded catalog preserves every original definition and appends all three types`() {
        val catalog = BuiltInLibrary.catalog
        assertEquals(builtInModes(), catalog.modes.take(builtInModes().size))
        assertEquals(builtInLorebooks(), catalog.lorebooks.take(builtInLorebooks().size))
        val originalSkills = builtInEngineeringSkills() + builtInProductivitySkills()
        assertEquals(originalSkills, catalog.skills.take(originalSkills.size))
        val items = libraryPreviewItems(catalog)
        assertEquals(101, items.size)
        assertEquals(items.size, items.map { it.id }.distinct().size)
        assertEquals(25, items.count { it.kind == LibraryContentKind.MODE })
        assertEquals(16, items.count { it.kind == LibraryContentKind.LOREBOOK })
        assertEquals(60, items.count { it.kind == LibraryContentKind.SKILL })
    }

    @Test
    fun `planning mode is a single stable built-in and append is idempotent`() {
        val planningId = PLANNING_MODE_ID.toString()
        val planning = BuiltInLibrary.catalog.modes.single { it.id == planningId }
        val first = appendMissingById(emptyList(), listOf(planning)) { it.id }
        val second = appendMissingById(first, listOf(planning)) { it.id }

        assertEquals(listOf(planning), first)
        assertEquals(first, second)
        assertEquals(1, second.count { it.id == planningId })
    }

    @Test
    fun `search matches all query terms without bypassing category selection`() {
        val items = libraryPreviewItems(BuiltInLibrary.catalog)
        val result = filterLibraryPreview(items, "  MIGRATION   rehearsal ", LibraryContentKind.SKILL)
        assertTrue(result.any { it.name == "orchords-migration-rehearsal" })
        assertTrue(result.all { it.kind == LibraryContentKind.SKILL })
        assertTrue(filterLibraryPreview(items, "no-such-catalog-token-98475").isEmpty())
        assertEquals(items, filterLibraryPreview(items, "  "))
        assertTrue(filterLibraryPreview(items, "", LibraryContentKind.LOREBOOK).all {
            it.kind == LibraryContentKind.LOREBOOK && it.body.isNotBlank()
        })
    }

    @Test
    fun `every expanded skill has distinct prerequisites workflow output verification and failure behavior`() {
        val additions = expandedEngineeringSkills() + expandedKnowledgeSkills() + expandedCreativeSkills()
        assertEquals(30, additions.size)
        additions.forEach { skill ->
            listOf("Prerequisites", "Workflow", "Output", "Verification", "Failure behavior", "Boundaries").forEach {
                assertTrue("${skill.name}: missing $it", skill.body.contains("## $it"))
            }
            assertFalse(skill.body.contains("TODO"))
        }
    }

    @Test
    fun `bundled catalog rejects duplicate identities blank triggers and unsupported version values`() {
        val catalog = BuiltInLibrary.catalog
        assertTrue(runCatching { validateLibraryCatalog(catalog.copy(version = 0)) }.isFailure)
        assertTrue(runCatching { validateLibraryCatalog(catalog.copy(modes = catalog.modes + catalog.modes.first())) }.isFailure)
        assertTrue(runCatching { validateLibraryCatalog(catalog.copy(skills = catalog.skills + catalog.skills.first())) }.isFailure)
        val book = catalog.lorebooks.first()
        val malformed = book.copy(entries = listOf(book.entries.first().copy(keywords = listOf(""))))
        assertTrue(runCatching { validateLibraryCatalog(catalog.copy(lorebooks = listOf(malformed))) }.isFailure)
    }
}
