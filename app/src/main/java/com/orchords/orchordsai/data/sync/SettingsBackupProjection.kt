package com.orchords.orchordsai.data.sync

import com.orchords.orchordsai.data.datastore.Settings
import com.orchords.orchordsai.data.datastore.migration.SettingsJsonMigrator
import com.orchords.orchordsai.data.model.Assistant
import com.orchords.orchordsai.data.model.Avatar
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal const val PORTABLE_SETTINGS_VERSION = 1
internal const val PORTABLE_SETTINGS_VERSION_FIELD = "_orchordsBackupSettingsVersion"

/**
 * Explicit V1 allowlist for ordinary portable settings backups.
 *
 * Connection/authentication objects are deliberately absent until #87 provides
 * opaque SecretStore references. New Settings fields are omitted at runtime by
 * default and must be classified by the schema regression before they can enter
 * this allowlist.
 */
internal val PORTABLE_SETTINGS_V1_INCLUDED_FIELDS = listOf(
    "dynamicColor",
    "themeId",
    "customThemes",
    "developerMode",
    "displaySetting",
    "favoriteModels",
    "chatModelId",
    "fastModelId",
    "fastModelReasoningLevel",
    "imageGenerationModelId",
    "titlePrompt",
    "translateModeId",
    "translatePrompt",
    "translateThinkingBudget",
    "enableSuggestion",
    "suggestionPrompt",
    "ocrModelId",
    "ocrPrompt",
    "compressModelId",
    "compressPrompt",
    "assistantId",
    "assistants",
    "assistantTags",
    "searchCommonOptions",
    "defaultTTSPlaybackSpeed",
    "modeInjections",
    "lorebooks",
    "quickMessages",
    "webServerPort",
    "webServerLocalhostOnly",
    "backupReminderConfig",
    "launchCount",
)

internal val PORTABLE_SETTINGS_V1_EXCLUDED_FIELDS = setOf(
    "networkSetting",
    "providers",
    "searchServices",
    "searchServiceSelected",
    "mcpServers",
    "webDavConfig",
    "s3Config",
    "ttsProviders",
    "selectedTTSProviderId",
    "asrProviders",
    "selectedASRProviderId",
    "webServerEnabled",
    "webServerJwtEnabled",
    "webServerAccessPassword",
    "onboardingState",
)

internal fun encodePortableSettingsBackup(settings: Settings, json: Json): String {
    val sanitized = settings.toPortableSettingsState()
    val encoded = json.encodeToJsonElement(Settings.serializer(), sanitized).jsonObject
    val projected = buildJsonObject {
        put(PORTABLE_SETTINGS_VERSION_FIELD, JsonPrimitive(PORTABLE_SETTINGS_VERSION))
        PORTABLE_SETTINGS_V1_INCLUDED_FIELDS.forEach { field ->
            put(
                field,
                requireNotNull(encoded[field]) {
                    "Portable settings field '$field' is missing from the Settings serializer"
                },
            )
        }
    }
    return json.encodeToString(JsonObject.serializer(), projected)
}

/**
 * Decode both V1 projections and legacy full-Settings backups through the same
 * fail-closed secret boundary. Legacy inline credentials are deliberately not
 * re-persisted while #87 SecretStore migration is unavailable.
 */
internal fun decodePortableSettingsBackup(settingsJson: String, json: Json): Settings {
    val root = json.parseToJsonElement(settingsJson).jsonObject
    val version = root[PORTABLE_SETTINGS_VERSION_FIELD]?.jsonPrimitive?.intOrNull
    require(version == null || version == PORTABLE_SETTINGS_VERSION) {
        "Unsupported portable settings backup version"
    }

    val migratedJson = SettingsJsonMigrator.migrate(settingsJson)
    val decoded = json.decodeFromString(Settings.serializer(), migratedJson)
    return decoded.toPortableSettingsState()
}

private fun Settings.toPortableSettingsState(): Settings {
    val defaults = Settings()
    return copy(
        displaySetting = displaySetting.copy(
            userAvatar = displaySetting.userAvatar.toPortableAvatar(),
        ),
        networkSetting = defaults.networkSetting,
        providers = defaults.providers,
        assistants = assistants.map(Assistant::toPortableAssistant),
        searchServices = defaults.searchServices,
        searchServiceSelected = defaults.searchServiceSelected,
        mcpServers = emptyList(),
        webDavConfig = defaults.webDavConfig,
        s3Config = defaults.s3Config,
        ttsProviders = defaults.ttsProviders,
        selectedTTSProviderId = defaults.selectedTTSProviderId,
        asrProviders = emptyList(),
        selectedASRProviderId = null,
        webServerEnabled = false,
        webServerJwtEnabled = false,
        webServerAccessPassword = "",
        onboardingState = defaults.onboardingState,
    )
}

private fun Assistant.toPortableAssistant(): Assistant = copy(
    avatar = avatar.toPortableAvatar(),
    customHeaders = emptyList(),
    customBodies = emptyList(),
    mcpServers = emptySet(),
    enableWebSearch = false,
    background = null,
)

private fun Avatar.toPortableAvatar(): Avatar = when (this) {
    is Avatar.Image -> Avatar.Dummy
    else -> this
}
