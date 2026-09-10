package com.orchords.orchordsai.data.sync

import com.orchords.ai.provider.CustomBody
import com.orchords.ai.provider.CustomHeader
import com.orchords.ai.provider.Model
import com.orchords.ai.provider.ProviderSetting
import com.orchords.asr.ASRProviderSetting
import com.orchords.orchordsai.data.ai.mcp.McpCommonOptions
import com.orchords.orchordsai.data.ai.mcp.McpOAuthState
import com.orchords.orchordsai.data.ai.mcp.McpServerConfig
import com.orchords.orchordsai.data.datastore.DisplaySetting
import com.orchords.orchordsai.data.datastore.NetworkSetting
import com.orchords.orchordsai.data.datastore.Settings
import com.orchords.orchordsai.data.datastore.WebDavConfig
import com.orchords.orchordsai.data.model.Assistant
import com.orchords.orchordsai.data.model.Avatar
import com.orchords.orchordsai.data.sync.s3.S3Config
import com.orchords.orchordsai.utils.JsonInstant
import com.orchords.search.SearchServiceOptions
import com.orchords.tts.provider.TTSProviderSetting
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class SettingsBackupProjectionTest {
    private val json = JsonInstant
    private val sentinel = "SECRET_SENTINEL_229"
    private val mcpId = Uuid.random()

    private fun credentialBearingSettings(): Settings = Settings(
        dynamicColor = false,
        displaySetting = DisplaySetting(
            userAvatar = Avatar.Image("https://example.test/avatar?token=$sentinel"),
            userNickname = "Keep nickname",
        ),
        networkSetting = NetworkSetting(
            userAgent = sentinel,
            proxyUrl = "https://example.test/proxy?token=$sentinel",
            proxyUsername = sentinel,
            proxyPassword = sentinel,
        ),
        providers = listOf(
            ProviderSetting.OpenAI(
                apiKey = sentinel,
                models = listOf(
                    Model(
                        modelId = "oai-1.0",
                        customHeaders = listOf(CustomHeader("Authorization", sentinel)),
                        customBodies = listOf(CustomBody("secret", JsonPrimitive(sentinel))),
                    )
                ),
            )
        ),
        assistants = listOf(
            Assistant(
                name = "Keep assistant",
                systemPrompt = "KEEP_LOCAL_ASSISTANT_PROMPT",
                avatar = Avatar.Image("https://example.test/assistant?token=$sentinel"),
                customHeaders = listOf(CustomHeader("Authorization", sentinel)),
                customBodies = listOf(CustomBody("secret", JsonPrimitive(sentinel))),
                mcpServers = setOf(mcpId),
                enableWebSearch = true,
                background = "https://example.test/background?token=$sentinel",
            )
        ),
        searchServices = listOf(SearchServiceOptions.BraveOptions(apiKey = sentinel)),
        searchServiceSelected = 0,
        mcpServers = listOf(
            McpServerConfig.StreamableHTTPServer(
                id = mcpId,
                commonOptions = McpCommonOptions(
                    name = "Keep MCP name",
                    headers = listOf("Authorization" to sentinel),
                    oauth = McpOAuthState(
                        enabled = true,
                        clientId = "public-client-id",
                        clientSecret = sentinel,
                        accessToken = sentinel,
                        refreshToken = sentinel,
                    ),
                ),
                url = "https://example.test/mcp?token=$sentinel",
            )
        ),
        webDavConfig = WebDavConfig(
            url = "https://example.test/webdav?token=$sentinel",
            username = sentinel,
            password = sentinel,
        ),
        s3Config = S3Config(
            endpoint = "https://example.test/$sentinel",
            accessKeyId = sentinel,
            secretAccessKey = sentinel,
            bucket = "keep-bucket",
        ),
        ttsProviders = listOf(TTSProviderSetting.OpenAI(apiKey = sentinel)),
        asrProviders = listOf(ASRProviderSetting.OpenAIRealtime(apiKey = sentinel)),
        webServerEnabled = true,
        webServerPort = 9090,
        webServerJwtEnabled = true,
        webServerAccessPassword = sentinel,
        webServerLocalhostOnly = true,
    )

    @Test
    fun `portable V1 settings never serialize confirmed credential-bearing state`() {
        val encoded = encodePortableSettingsBackup(credentialBearingSettings(), json)

        assertTrue(encoded.contains("\"$PORTABLE_SETTINGS_VERSION_FIELD\":$PORTABLE_SETTINGS_VERSION"))
        assertFalse(encoded.contains(sentinel))
        assertTrue(encoded.contains("KEEP_LOCAL_ASSISTANT_PROMPT"))
        assertTrue(encoded.contains("Keep nickname"))
    }

    @Test
    fun `portable V1 restore preserves local settings and resets external authority`() {
        val encoded = encodePortableSettingsBackup(credentialBearingSettings(), json)
        val restored = decodePortableSettingsBackup(encoded, json)
        val assistant = restored.assistants.single()

        assertFalse(restored.dynamicColor)
        assertEquals("Keep nickname", restored.displaySetting.userNickname)
        assertTrue(restored.displaySetting.userAvatar is Avatar.Dummy)
        assertEquals("KEEP_LOCAL_ASSISTANT_PROMPT", assistant.systemPrompt)
        assertTrue(assistant.avatar is Avatar.Dummy)
        assertTrue(assistant.customHeaders.isEmpty())
        assertTrue(assistant.customBodies.isEmpty())
        assertTrue(assistant.mcpServers.isEmpty())
        assertFalse(assistant.enableWebSearch)
        assertEquals(null, assistant.background)

        assertTrue(restored.networkSetting.proxyUrl.isBlank())
        assertTrue(restored.networkSetting.proxyUsername.isBlank())
        assertTrue(restored.networkSetting.proxyPassword.isBlank())
        assertTrue(restored.providers.filterIsInstance<ProviderSetting.OpenAI>().all { it.apiKey.isBlank() })
        assertTrue(restored.mcpServers.isEmpty())
        assertTrue(restored.webDavConfig.password.isBlank())
        assertTrue(restored.s3Config.accessKeyId.isBlank())
        assertTrue(restored.s3Config.secretAccessKey.isBlank())
        assertTrue(restored.asrProviders.isEmpty())
        assertFalse(restored.webServerEnabled)
        assertFalse(restored.webServerJwtEnabled)
        assertTrue(restored.webServerAccessPassword.isBlank())
        assertEquals(9090, restored.webServerPort)
        assertTrue(restored.webServerLocalhostOnly)
        assertFalse(json.encodeToString(Settings.serializer(), restored).contains(sentinel))
    }

    @Test
    fun `legacy full settings backup is sanitized before it can be persisted`() {
        val legacy = json.encodeToString(Settings.serializer(), credentialBearingSettings())
        assertTrue(legacy.contains(sentinel))

        val restored = decodePortableSettingsBackup(legacy, json)

        assertFalse(json.encodeToString(Settings.serializer(), restored).contains(sentinel))
        assertTrue(restored.mcpServers.isEmpty())
        assertTrue(restored.providers.filterIsInstance<ProviderSetting.OpenAI>().all { it.apiKey.isBlank() })
        assertTrue(restored.assistants.single().customHeaders.isEmpty())
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `every serialized Settings field is explicitly included or excluded`() {
        val descriptor = Settings.serializer().descriptor
        val serializedFields = (0 until descriptor.elementsCount)
            .map(descriptor::getElementName)
            .toSet()
        val included = PORTABLE_SETTINGS_V1_INCLUDED_FIELDS.toSet()
        val excluded = PORTABLE_SETTINGS_V1_EXCLUDED_FIELDS

        assertTrue(included.intersect(excluded).isEmpty())
        assertEquals(serializedFields, included + excluded)
    }
}
