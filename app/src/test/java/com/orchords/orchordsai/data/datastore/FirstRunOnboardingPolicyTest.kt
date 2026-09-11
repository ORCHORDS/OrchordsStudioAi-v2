package com.orchords.orchordsai.data.datastore

import org.junit.Assert.assertEquals
import org.junit.Test

class FirstRunOnboardingPolicyTest {
    private val healthy = FirstPartyChatHealth(ConfigurationHealthState.HEALTHY, emptyList())
    private val incomplete = FirstPartyChatHealth(
        ConfigurationHealthState.INCOMPLETE,
        listOf(ConfigurationHealthFinding.MISSING_GATEWAY_CREDENTIAL),
    )
    private val blocked = FirstPartyChatHealth(
        ConfigurationHealthState.BLOCKED,
        listOf(ConfigurationHealthFinding.MISSING_FIRST_PARTY_PROVIDER),
    )

    @Test
    fun `fresh uninitialized profile enters onboarding`() {
        assertEquals(
            StartupConfigurationRoute.ONBOARDING,
            evaluateStartupConfiguration(OnboardingState.UNINITIALIZED, 0, incomplete).route,
        )
    }

    @Test
    fun `explicit required state survives later launches until onboarding completes`() {
        assertEquals(
            StartupConfigurationRoute.ONBOARDING,
            evaluateStartupConfiguration(OnboardingState.REQUIRED, 12, incomplete).route,
        )
    }

    @Test
    fun `legacy launched profile with missing configuration enters recovery not onboarding`() {
        assertEquals(
            StartupConfigurationRoute.RECOVERY,
            evaluateStartupConfiguration(OnboardingState.UNINITIALIZED, 12, blocked).route,
        )
    }

    @Test
    fun `completed profile with incomplete gateway enters recovery`() {
        assertEquals(
            StartupConfigurationRoute.RECOVERY,
            evaluateStartupConfiguration(OnboardingState.COMPLETED, 5, incomplete).route,
        )
    }

    @Test
    fun `completed healthy profile enters chat`() {
        assertEquals(
            StartupConfigurationRoute.CHAT,
            evaluateStartupConfiguration(OnboardingState.COMPLETED, 5, healthy).route,
        )
    }

    @Test
    fun `negative legacy launch count fails conservative into recovery`() {
        assertEquals(
            StartupConfigurationRoute.RECOVERY,
            evaluateStartupConfiguration(OnboardingState.UNINITIALIZED, -1, blocked).route,
        )
    }

    @Test
    fun `healthy launched legacy profile is not reset into onboarding`() {
        assertEquals(
            StartupConfigurationRoute.CHAT,
            evaluateStartupConfiguration(OnboardingState.UNINITIALIZED, 1, healthy).route,
        )
    }
}
