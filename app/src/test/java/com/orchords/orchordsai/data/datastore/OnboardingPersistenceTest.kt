package com.orchords.orchordsai.data.datastore

import com.orchords.orchordsai.data.datastore.migration.PreferenceStoreV4Migration
import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingPersistenceTest {
    @Test
    fun `settings default keeps legacy state unresolved until migration`() {
        assertEquals(OnboardingState.UNINITIALIZED, Settings().onboardingState)
    }

    @Test
    fun `fresh migration requires onboarding`() = runTest {
        val migration = PreferenceStoreV4Migration()
        val migrated = migration.migrate(mutablePreferencesOf())

        assertEquals(4, migrated[SettingsStore.VERSION])
        assertEquals(OnboardingState.REQUIRED.name, migrated[SettingsStore.ONBOARDING_STATE])
    }

    @Test
    fun `launched legacy profile migrates to completed`() = runTest {
        val migration = PreferenceStoreV4Migration()
        val migrated = migration.migrate(mutablePreferencesOf(SettingsStore.LAUNCH_COUNT to 2))

        assertEquals(OnboardingState.COMPLETED.name, migrated[SettingsStore.ONBOARDING_STATE])
    }

    @Test
    fun `explicit required state is preserved`() = runTest {
        val migration = PreferenceStoreV4Migration()
        val migrated = migration.migrate(
            mutablePreferencesOf(
                SettingsStore.LAUNCH_COUNT to 9,
                SettingsStore.ONBOARDING_STATE to OnboardingState.REQUIRED.name,
            )
        )

        assertEquals(OnboardingState.REQUIRED.name, migrated[SettingsStore.ONBOARDING_STATE])
    }

    @Test
    fun `version four does not migrate again`() = runTest {
        val migration = PreferenceStoreV4Migration()
        assertFalse(migration.shouldMigrate(mutablePreferencesOf(SettingsStore.VERSION to 4)))
        assertTrue(migration.shouldMigrate(mutablePreferencesOf(SettingsStore.VERSION to 3)))
    }
}
