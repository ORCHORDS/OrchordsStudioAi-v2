package com.orchords.orchordsai.data.extensions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltInSkillLifecycleTest {
    private val catalog = setOf("alpha-skill", "beta-skill", "gamma-skill")

    @Test
    fun `fresh lifecycle installs every catalog skill`() {
        val state = bootstrapBuiltInSkillLifecycleState(
            catalogNames = catalog,
            existingBundledNames = emptySet(),
            priorState = null,
        )

        assertEquals(catalog, installableBuiltInSkillNames(catalog, state, restoreRemoved = false))
        assertTrue(state.removedSkillNames.isEmpty())
    }

    @Test
    fun `legacy installed library treats absent known skills as removed`() {
        val state = bootstrapBuiltInSkillLifecycleState(
            catalogNames = catalog,
            existingBundledNames = setOf("alpha-skill", "gamma-skill"),
            priorState = null,
        )

        assertEquals(setOf("beta-skill"), state.removedSkillNames)
        assertEquals(setOf("alpha-skill", "gamma-skill"), installableBuiltInSkillNames(catalog, state, false))
    }

    @Test
    fun `explicit built-in deletion stays tombstoned`() {
        val initial = BuiltInSkillLifecycleState(
            catalogVersion = 2,
            knownSkillNames = catalog,
        )
        val deleted = recordBuiltInSkillRemoval(initial, "beta-skill", catalog)

        assertTrue("beta-skill" in deleted.removedSkillNames)
        assertFalse("beta-skill" in installableBuiltInSkillNames(catalog, deleted, restoreRemoved = false))
    }

    @Test
    fun `deleting a non built-in skill does not change lifecycle`() {
        val initial = BuiltInSkillLifecycleState(
            catalogVersion = 2,
            knownSkillNames = catalog,
        )

        assertEquals(initial, recordBuiltInSkillRemoval(initial, "custom-skill", catalog))
    }

    @Test
    fun `repair explicitly restores removed built-ins`() {
        val state = BuiltInSkillLifecycleState(
            catalogVersion = 2,
            knownSkillNames = catalog,
            removedSkillNames = setOf("beta-skill"),
        )

        assertEquals(catalog, installableBuiltInSkillNames(catalog, state, restoreRemoved = true))
    }

    @Test
    fun `future catalog additions remain installable`() {
        val oldCatalog = setOf("alpha-skill", "beta-skill")
        val state = BuiltInSkillLifecycleState(
            catalogVersion = 1,
            knownSkillNames = oldCatalog,
            removedSkillNames = setOf("beta-skill"),
        )

        assertEquals(
            setOf("alpha-skill", "gamma-skill"),
            installableBuiltInSkillNames(catalog, state, restoreRemoved = false),
        )
    }
}
