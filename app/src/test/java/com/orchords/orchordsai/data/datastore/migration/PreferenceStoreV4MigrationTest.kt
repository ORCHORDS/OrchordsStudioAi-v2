package com.orchords.orchordsai.data.datastore.migration

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.orchords.orchordsai.data.datastore.OnboardingState
import com.orchords.orchordsai.data.datastore.SettingsStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferenceStoreV4MigrationTest {
    private val migration = PreferenceStoreV4Migration()

    @Test
    fun `fresh profile is marked onboarding required`() = runTest {
        val migrated = migration.migrate(mutablePreferencesOf())

        assertEquals(4, migrated[SettingsStore.VERSION])
        assertEquals(OnboardingState.REQUIRED.name, migrated[SettingsStore.ONBOARDING_STATE])
    }

    @Test
    fun `previously launched profile is preserved as existing user`() = runTest {
        val migrated = migration.migrate(
            mutablePreferencesOf(SettingsStore.LAUNCH_COUNT to 7)
        )

        assertEquals(OnboardingState.COMPLETED.name, migrated[SettingsStore.ONBOARDING_STATE])
    }

    @Test
    fun `explicit incomplete onboarding survives migration`() = runTest {
        val migrated = migration.migrate(
            mutablePreferencesOf(
                SettingsStore.LAUNCH_COUNT to 12,
                SettingsStore.ONBOARDING_STATE to OnboardingState.REQUIRED.name,
            )
        )

        assertEquals(OnboardingState.REQUIRED.name, migrated[SettingsStore.ONBOARDING_STATE])
    }

    @Test
    fun `negative launch count is never treated as a fresh install`() = runTest {
        val migrated = migration.migrate(
            mutablePreferencesOf(SettingsStore.LAUNCH_COUNT to -1)
        )

        assertEquals(OnboardingState.COMPLETED.name, migrated[SettingsStore.ONBOARDING_STATE])
    }

    @Test
    fun `version four does not migrate again`() = runTest {
        val current = mutablePreferencesOf(
            SettingsStore.VERSION to 4,
            SettingsStore.ONBOARDING_STATE to OnboardingState.COMPLETED.name,
        )

        assertFalse(migration.shouldMigrate(current))
        assertTrue(migration.shouldMigrate(mutablePreferencesOf(SettingsStore.VERSION to 3)))
    }
}
