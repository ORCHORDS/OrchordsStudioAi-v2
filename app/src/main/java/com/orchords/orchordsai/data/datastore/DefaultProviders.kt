package com.orchords.orchordsai.data.datastore

import com.orchords.ai.provider.Model
import com.orchords.ai.provider.ModelAbility
import com.orchords.ai.provider.ProviderSetting
import kotlin.uuid.Uuid

const val ORCHORDS_GATEWAY_BASE_URL = "https://api.orchords.com/v1"
const val ORCHORDS_SEARCH_BASE_URL = "$ORCHORDS_GATEWAY_BASE_URL/search"

const val ORCHORDS_MODEL_ID = "oai-1.0"
val ORCHORDS_MODEL_UUID: Uuid =
    Uuid.parse("0d3a4f7b-1a25-4f31-9d18-1c5f6b2a7e02")

const val ORCHORDS_OAI_1_2_MODEL_ID = "oai-1.2"
val ORCHORDS_OAI_1_2_MODEL_UUID: Uuid =
    Uuid.parse("0d3a4f7b-1a25-4f31-9d18-1c5f6b2a7e03")

internal val ORCHORDS_FIRST_PARTY_MODEL_UUIDS: Set<Uuid> = setOf(
    ORCHORDS_MODEL_UUID,
    ORCHORDS_OAI_1_2_MODEL_UUID,
)

private val ORCHORDS_PROVIDER_UUID: Uuid =
    Uuid.parse("0d3a4f7b-1a25-4f31-9d18-1c5f6b2a7e01")

/**
 * Built-in OrchordsAI provider and first-party model registry.
 *
 * oai-1.0 remains the compatibility/default model so existing users are not
 * silently switched during upgrade. oai-1.2 is an additive reasoning-capable
 * route exposed through the same canonical gateway/provider credential.
 *
 * Both models use stable product UUIDs so persisted selections remain stable.
 */
internal val DEFAULT_ORCHORDS_MODELS = listOf(
    Model(
        id = ORCHORDS_MODEL_UUID,
        modelId = ORCHORDS_MODEL_ID,
        displayName = "Orchords oai-1.0",
        abilities = listOf(ModelAbility.TOOL),
    ),
    Model(
        id = ORCHORDS_OAI_1_2_MODEL_UUID,
        modelId = ORCHORDS_OAI_1_2_MODEL_ID,
        displayName = "Orchords oai-1.2",
        abilities = listOf(ModelAbility.TOOL, ModelAbility.REASONING),
    ),
)

val DEFAULT_PROVIDERS: List<ProviderSetting> = listOf(
    ProviderSetting.OpenAI(
        id = ORCHORDS_PROVIDER_UUID,
        name = "OrchordsAI",
        baseUrl = ORCHORDS_GATEWAY_BASE_URL,
        apiKey = "",
        enabled = true,
        builtIn = true,
        models = DEFAULT_ORCHORDS_MODELS,
    ),
)

/** Compatibility default: upgrades never silently move users off oai-1.0. */
val DEFAULT_AUTO_MODEL_ID: Uuid get() = ORCHORDS_MODEL_UUID
