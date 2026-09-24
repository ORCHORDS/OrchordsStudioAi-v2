package com.orchords.orchordsai.data.datastore

import com.orchords.ai.provider.ProviderSetting
import kotlin.uuid.Uuid

/**
 * Enforce the product-wide first-party model policy.
 *
 * Legacy provider/model records may still be decoded for migration
 * compatibility, but active runtime settings are restricted to the canonical
 * Orchords gateway and the stable first-party model allowlist (oai-1.0 and
 * oai-1.2). Existing valid first-party selections are preserved; unknown or
 * retired selections fall back to oai-1.0.
 */
internal fun Settings.enforceFirstPartyModelPolicy(): Settings {
    val provider = canonicalOrchordsProvider(providers)
    val effectiveChatModelId = canonicalFirstPartyModelId(chatModelId)
    val effectiveFastModelId = canonicalFirstPartyModelId(fastModelId)
    val effectiveTranslateModelId = canonicalFirstPartyModelId(translateModeId)
    val effectiveCompressModelId = canonicalFirstPartyModelId(compressModelId)

    val effectiveAssistants = assistants
        .ifEmpty { DEFAULT_ASSISTANTS }
        .map { assistant ->
            assistant.copy(
                chatModelId = canonicalFirstPartyModelId(assistant.chatModelId),
            )
        }
    val effectiveAssistantId = effectiveAssistants
        .firstOrNull { it.id == assistantId }
        ?.id
        ?: effectiveAssistants.first().id

    return copy(
        providers = listOf(provider),
        chatModelId = effectiveChatModelId,
        fastModelId = effectiveFastModelId,
        translateModeId = effectiveTranslateModelId,
        compressModelId = effectiveCompressModelId,
        favoriteModels = favoriteModels
            .filter { it in ORCHORDS_FIRST_PARTY_MODEL_UUIDS }
            .distinct(),
        assistantId = effectiveAssistantId,
        assistants = effectiveAssistants,
    )
}

internal fun canonicalFirstPartyModelId(modelId: Uuid?): Uuid =
    modelId?.takeIf { it in ORCHORDS_FIRST_PARTY_MODEL_UUIDS }
        ?: ORCHORDS_MODEL_UUID

internal fun canonicalOrchordsProvider(
    providers: List<ProviderSetting>,
): ProviderSetting.OpenAI {
    val template = DEFAULT_PROVIDERS.single() as ProviderSetting.OpenAI
    val existing = providers
        .filterIsInstance<ProviderSetting.OpenAI>()
        .firstOrNull { it.id == template.id }
        ?: providers
            .filterIsInstance<ProviderSetting.OpenAI>()
            .firstOrNull {
                it.baseUrl.trimEnd('/') == ORCHORDS_GATEWAY_BASE_URL
            }

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
