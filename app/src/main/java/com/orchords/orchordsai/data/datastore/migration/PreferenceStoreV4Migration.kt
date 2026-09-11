package com.orchords.orchordsai.data.datastore.migration

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import com.orchords.orchordsai.data.datastore.OnboardingState
import com.orchords.orchordsai.data.datastore.SettingsStore

/**
 * Introduces durable first-run onboarding intent without resetting existing launched profiles.
 *
 * Fresh/untouched profiles (exact launch count zero) require onboarding. Any profile that has
 * already launched is treated as an existing user and marked completed so broken/missing gateway
 * configuration is routed to recovery instead of replaying first-run onboarding.
 */
class PreferenceStoreV4Migration : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean {
        val version = currentData[SettingsStore.VERSION]
        return version == null || version < 4
    }

    override suspend fun migrate(currentData: Preferences): Preferences {
        val prefs = currentData.toMutablePreferences()

        if (prefs[SettingsStore.ONBOARDING_STATE] == null) {
            val launchCount = prefs[SettingsStore.LAUNCH_COUNT] ?: 0
            prefs[SettingsStore.ONBOARDING_STATE] = if (launchCount == 0) {
                OnboardingState.REQUIRED.name
            } else {
                OnboardingState.COMPLETED.name
            }
        }

        prefs[SettingsStore.VERSION] = 4
        return prefs.toPreferences()
    }

    override suspend fun cleanUp() {}
}
