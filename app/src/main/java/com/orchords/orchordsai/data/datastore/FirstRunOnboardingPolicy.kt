package com.orchords.orchordsai.data.datastore

import kotlinx.serialization.Serializable

/**
 * Durable onboarding intent is intentionally separate from configuration health.
 *
 * UNINITIALIZED exists only for profiles that predate persisted onboarding state. A launched
 * legacy profile must never be sent through full first-run onboarding merely because its current
 * gateway configuration is incomplete or degraded.
 */
@Serializable
enum class OnboardingState {
    UNINITIALIZED,
    REQUIRED,
    COMPLETED,
}

enum class StartupConfigurationRoute {
    ONBOARDING,
    RECOVERY,
    CHAT,
}

data class StartupConfigurationDecision(
    val route: StartupConfigurationRoute,
    val configurationHealth: FirstPartyChatHealth,
)

fun evaluateStartupConfiguration(
    onboardingState: OnboardingState,
    launchCount: Int,
    configurationHealth: FirstPartyChatHealth,
): StartupConfigurationDecision {
    val route = when (onboardingState) {
        OnboardingState.REQUIRED -> StartupConfigurationRoute.ONBOARDING
        OnboardingState.COMPLETED -> when (configurationHealth.state) {
            ConfigurationHealthState.HEALTHY -> StartupConfigurationRoute.CHAT
            ConfigurationHealthState.INCOMPLETE,
            ConfigurationHealthState.DEGRADED,
            ConfigurationHealthState.BLOCKED,
            -> StartupConfigurationRoute.RECOVERY
        }
        OnboardingState.UNINITIALIZED -> when {
            // Exact zero is the only safe legacy signal for an untouched profile. Negative values
            // are treated conservatively as existing/corrupt state rather than resetting a user.
            launchCount == 0 -> StartupConfigurationRoute.ONBOARDING
            configurationHealth.state == ConfigurationHealthState.HEALTHY -> StartupConfigurationRoute.CHAT
            else -> StartupConfigurationRoute.RECOVERY
        }
    }

    return StartupConfigurationDecision(
        route = route,
        configurationHealth = configurationHealth,
    )
}
