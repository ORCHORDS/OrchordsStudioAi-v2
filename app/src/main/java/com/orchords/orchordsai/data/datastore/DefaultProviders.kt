package com.orchords.orchordsai.data.datastore

import com.orchords.ai.provider.Model
import com.orchords.ai.provider.ProviderSetting
import kotlin.uuid.Uuid

/**
 * Single built-in OrchordsAI provider/model. The app does not ship any
 * third-party providers; users add their own API key in Settings → Providers.
 *
 * The `apiKey` field is intentionally empty in production source. Tests read
 * the key from `local.properties` or the `ORCHORDS_API_KEY` environment
 * variable; production builds must never embed the secret.
 */
private val DEFAULT_ORCHORDS_MODELS = listOf(
    Model(
        modelId = "oai-1.0",
        displayName = "Orchords oai-1.0",
    )
)

val DEFAULT_PROVIDERS: List<ProviderSetting> = listOf(
    ProviderSetting.OpenAI(
        id = Uuid.parse("0d3a4f7b-1a25-4f31-9d18-1c5f6b2a7e01"),
        name = "OrchordsAI",
        baseUrl = "https://api.orchords.com/v1",
        apiKey = "",
        enabled = true,
        builtIn = true,
        models = DEFAULT_ORCHORDS_MODELS,
    ),
)

/**
 * Stable id of the only model exposed by [DEFAULT_PROVIDERS]. The previous
 * value was an arbitrary UUID; this now resolves to the Orchords oai-1.0
 * model, preserving the Uuid type for callers.
 */
val DEFAULT_AUTO_MODEL_ID: Uuid get() = DEFAULT_ORCHORDS_MODELS.first().id
