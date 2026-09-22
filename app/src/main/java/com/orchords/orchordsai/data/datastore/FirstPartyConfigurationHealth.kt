package com.orchords.orchordsai.data.datastore

import com.orchords.ai.provider.ProviderSetting

/**
 * Coarse configuration-health states used by onboarding/recovery surfaces.
 *
 * This model is intentionally independent of transport/runtime failures. A
 * missing credential is incomplete configuration, while a broken first-party
 * registration is blocked before any request is attempted.
 */
enum class ConfigurationHealthState {
    HEALTHY,
    INCOMPLETE,
    DEGRADED,
    BLOCKED,
}

/** Stable, non-secret reason codes suitable for diagnostics and UI mapping. */
enum class ConfigurationHealthFinding {
    MISSING_FIRST_PARTY_PROVIDER,
    NONCANONICAL_PROVIDER_ID,
    UNEXPECTED_GATEWAY_ROUTE,
    MISSING_OAI_MODEL,
    MISSING_OAI_1_2_MODEL,
    MISSING_GATEWAY_CREDENTIAL,
}

data class FirstPartyChatHealth(
    val state: ConfigurationHealthState,
    val findings: List<ConfigurationHealthFinding>,
)

/**
 * Inspect first-party chat configuration without making a gateway request or
 * retaining credential material in the returned diagnostic object.
 *
 * The raw provider list is accepted so onboarding can distinguish a repairable
 * legacy registration from the canonical effective settings produced by
 * [enforceFirstPartyModelPolicy]. Credential contents are never returned;
 * only non-blank presence is observed.
 */
fun evaluateFirstPartyChatHealth(
    providers: List<ProviderSetting>,
): FirstPartyChatHealth {
    val expected = DEFAULT_PROVIDERS.single() as ProviderSetting.OpenAI
    val openAiProviders = providers.filterIsInstance<ProviderSetting.OpenAI>()
    val provider = openAiProviders.firstOrNull { it.id == expected.id }
        ?: openAiProviders.firstOrNull {
            it.baseUrl.trim().trimEnd('/') == ORCHORDS_GATEWAY_BASE_URL
        }
        ?: return FirstPartyChatHealth(
            state = ConfigurationHealthState.BLOCKED,
            findings = listOf(ConfigurationHealthFinding.MISSING_FIRST_PARTY_PROVIDER),
        )

    val findings = mutableListOf<ConfigurationHealthFinding>()
    var blocked = false
    var degraded = false

    if (provider.id != expected.id) {
        findings += ConfigurationHealthFinding.NONCANONICAL_PROVIDER_ID
        degraded = true
    }

    if (provider.baseUrl.trim().trimEnd('/') != ORCHORDS_GATEWAY_BASE_URL) {
        findings += ConfigurationHealthFinding.UNEXPECTED_GATEWAY_ROUTE
        blocked = true
    }

    val hasCanonicalModel = provider.models.any { model ->
        model.id == ORCHORDS_MODEL_UUID && model.modelId == ORCHORDS_MODEL_ID
    }
    if (!hasCanonicalModel) {
        findings += ConfigurationHealthFinding.MISSING_OAI_MODEL
        blocked = true
    }

    val hasOai12Model = provider.models.any { model ->
        model.id == ORCHORDS_OAI_1_2_MODEL_UUID &&
            model.modelId == ORCHORDS_OAI_1_2_MODEL_ID
    }
    if (!hasOai12Model) {
        findings += ConfigurationHealthFinding.MISSING_OAI_1_2_MODEL
        degraded = true
    }

    val missingCredential = provider.apiKey.isBlank()
    if (missingCredential) {
        findings += ConfigurationHealthFinding.MISSING_GATEWAY_CREDENTIAL
    }

    val state = when {
        blocked -> ConfigurationHealthState.BLOCKED
        missingCredential -> ConfigurationHealthState.INCOMPLETE
        degraded -> ConfigurationHealthState.DEGRADED
        else -> ConfigurationHealthState.HEALTHY
    }

    return FirstPartyChatHealth(state = state, findings = findings)
}
