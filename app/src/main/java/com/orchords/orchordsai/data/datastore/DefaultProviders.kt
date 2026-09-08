package com.orchords.orchordsai.data.datastore

import com.orchords.ai.provider.Model
import com.orchords.ai.provider.ModelAbility
import com.orchords.ai.provider.ProviderSetting
import kotlin.uuid.Uuid

const val ORCHORDS_GATEWAY_BASE_URL = "https://api.orchords.com/v1"
const val ORCHORDS_MODEL_ID = "oai-1.0"
val ORCHORDS_MODEL_UUID: Uuid = Uuid.parse("0d3a4f7b-1a25-4f31-9d18-1c5f6b2a7e02")
private val ORCHORDS_PROVIDER_UUID: Uuid = Uuid.parse("0d3a4f7b-1a25-4f31-9d18-1c5f6b2a7e01")

/**
 * Single built-in OrchordsAI provider/model.
 *
 * The bearer credential is intentionally empty in production source. The
 * supported first-party model is Chat Completions compatible and explicitly
 * advertises function/tool calling; provider-native Search remains disabled
 * so Web Search is routed through the external search_web function tool.
 *
 * Both provider and model use stable product UUIDs so persisted selections do
 * not change across process restarts or upgrades.
 */
private val DEFAULT_ORCHORDS_MODELS = listOf(
    Model(
        id = ORCHORDS_MODEL_UUID,
        modelId = ORCHORDS_MODEL_ID,
        displayName = "Orchords oai-1.0",
        abilities = listOf(ModelAbility.TOOL),
    )
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

/** Stable id of the only first-party chat model. */
val DEFAULT_AUTO_MODEL_ID: Uuid get() = ORCHORDS_MODEL_UUID
