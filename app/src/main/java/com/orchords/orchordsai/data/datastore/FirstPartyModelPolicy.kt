package com.orchords.orchordsai.data.datastore

import com.orchords.ai.provider.ProviderSetting

/**
 * Enforce the product-wide first-party model policy from #26/#416.
 *
 * Legacy provider/model records may still be decoded for migration compatibility,
 * but they are never returned as active runtime settings. The only model route is
 * Orchords `oai-1.0` through the canonical gateway. Existing first-party gateway
 * credentials and safe first-party request tuning remain attached to that route;
 * alternate hosts/models/provider types are discarded from the effective view.
 */
internal fun Settings.enforceFirstPartyModelPolicy(): Settings {
    val provider = canonicalOrchordsProvider(providers)
    val modelId = ORCHORDS_MODEL_UUID
    val effectiveAssistants = assistants
        .ifEmpty { DEFAULT_ASSISTANTS }
        .map { assistant -> assistant.copy(chatModelId = modelId) }
    val effectiveAssistantId = effectiveAssistants
        .firstOrNull { it.id == assistantId }
        ?.id
        ?: effectiveAssistants.first().id

    return copy(
        providers = listOf(provider),
        chatModelId = modelId,
        fastModelId = modelId,
        translateModeId = modelId,
        compressModelId = modelId,
        favoriteModels = favoriteModels.filter { it == modelId }.distinct(),
        assistantId = effectiveAssistantId,
        assistants = effectiveAssistants,
    )
}

internal fun canonicalOrchordsProvider(
    providers: List<ProviderSetting>,
): ProviderSetting.OpenAI {
    val template = DEFAULT_PROVIDERS.single() as ProviderSetting.OpenAI
    val existing = providers
        .filterIsInstance<ProviderSetting.OpenAI>()
        .firstOrNull { it.id == template.id }
        ?: providers
            .filterIsInstance<ProviderSetting.OpenAI>()
            .firstOrNull { it.baseUrl.trimEnd('/') == ORCHORDS_GATEWAY_BASE_URL }

    if (existing == null) return template.copy()

    return existing.copy(
        id = template.id,
        enabled = true,
        name = template.name,
        models = template.models,
        balanceOption = template.balanceOption,
        builtIn = true,
        description = template.description,
        shortDescription = template.shortDescription,
        baseUrl = template.baseUrl,
        chatCompletionsPath = template.chatCompletionsPath,
        useResponseApi = false,
    )
}
