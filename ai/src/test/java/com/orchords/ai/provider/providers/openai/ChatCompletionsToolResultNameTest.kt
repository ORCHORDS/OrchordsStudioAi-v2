package com.orchords.ai.provider.providers.openai

import com.orchords.ai.core.MessageRole
import com.orchords.ai.core.Tool
import com.orchords.ai.provider.Model
import com.orchords.ai.provider.ModelAbility
import com.orchords.ai.provider.ProviderSetting
import com.orchords.ai.provider.TextGenerationParams
import com.orchords.ai.provider.ToolResultNameMode
import com.orchords.ai.provider.buildToolAliasRequestView
import com.orchords.ai.ui.UIMessage
import com.orchords.ai.ui.UIMessagePart
import com.orchords.ai.util.KeyRoulette
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.InvocationTargetException

class ChatCompletionsToolResultNameTest {
    private lateinit var api: ChatCompletionsAPI
    private lateinit var server: MockWebServer

    private val model = Model(
        modelId = "test-tool-model",
        abilities = listOf(ModelAbility.TOOL),
    )

    @Before
    fun setUp() {
        api = ChatCompletionsAPI(OkHttpClient(), KeyRoulette.default())
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `native OpenAI AUTO omits tool result name`() {
        val body = buildRequest(
            providerSetting = ProviderSetting.OpenAI(
                baseUrl = "https://api.openai.com/v1",
                toolResultNameMode = ToolResultNameMode.AUTO,
            ),
            message = executedToolMessage("call-native", "safe_tool"),
        )

        val result = toolResult(body)
        assertEquals("call-native", result["tool_call_id"]?.jsonPrimitive?.content)
        assertFalse(result.containsKey("name"))
    }

    @Test
    fun `Gemini OpenAI compatible AUTO includes provider visible tool name`() {
        val body = buildRequest(
            providerSetting = ProviderSetting.OpenAI(
                baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
                toolResultNameMode = ToolResultNameMode.AUTO,
            ),
            message = executedToolMessage("call-gemini", "provider_safe_name"),
        )

        val result = toolResult(body)
        assertEquals("call-gemini", result["tool_call_id"]?.jsonPrimitive?.content)
        assertEquals("provider_safe_name", result["name"]?.jsonPrimitive?.content)
    }

    @Test
    fun `strict custom endpoint can explicitly omit tool result name`() {
        val body = buildRequest(
            providerSetting = ProviderSetting.OpenAI(
                baseUrl = "https://strict.example/v1",
                toolResultNameMode = ToolResultNameMode.OMIT,
            ),
            message = executedToolMessage("call-strict", "tool"),
        )

        val result = toolResult(body)
        assertEquals("call-strict", result["tool_call_id"]?.jsonPrimitive?.content)
        assertFalse(result.containsKey("name"))
    }

    @Test
    fun `required route uses request scoped provider alias instead of canonical MCP name`() {
        val canonicalName = "mcp__workspace/server.read-file"
        val canonicalMessage = executedToolMessage("call-mcp", canonicalName)
        val canonicalTool = Tool(
            name = canonicalName,
            description = "Read a file",
            execute = { emptyList() },
        )
        val setting = ProviderSetting.OpenAI(
            baseUrl = "https://compat.example/v1",
            toolResultNameMode = ToolResultNameMode.INCLUDE,
        )
        val params = TextGenerationParams(model = model, tools = listOf(canonicalTool))
        val requestView = buildToolAliasRequestView(setting, listOf(canonicalMessage), params)
        val alias = requestView.aliases.getValue(canonicalName)

        val body = buildRequest(setting, requestView.messages.single(), requestView.params)
        val result = toolResult(body)

        assertEquals("call-mcp", result["tool_call_id"]?.jsonPrimitive?.content)
        assertEquals(alias, result["name"]?.jsonPrimitive?.content)
        assertFalse(alias.contains('/'))
        assertFalse(alias.contains('.'))
        assertFalse(alias.contains(canonicalName))
    }

    @Test
    fun `provider switch rebuilds same canonical result for destination capability`() {
        val message = executedToolMessage("call-switch", "stable_tool")

        val included = toolResult(
            buildRequest(
                ProviderSetting.OpenAI(
                    baseUrl = "https://compat.example/v1",
                    toolResultNameMode = ToolResultNameMode.INCLUDE,
                ),
                message,
            )
        )
        val omitted = toolResult(
            buildRequest(
                ProviderSetting.OpenAI(
                    baseUrl = "https://strict.example/v1",
                    toolResultNameMode = ToolResultNameMode.OMIT,
                ),
                message,
            )
        )

        assertEquals("call-switch", included["tool_call_id"]?.jsonPrimitive?.content)
        assertEquals("call-switch", omitted["tool_call_id"]?.jsonPrimitive?.content)
        assertEquals("stable_tool", included["name"]?.jsonPrimitive?.content)
        assertFalse(omitted.containsKey("name"))
        assertEquals("stable_tool", message.parts.filterIsInstance<UIMessagePart.Tool>().single().toolName)
    }

    @Test
    fun `blank tool call id fails closed before request serialization`() {
        val error = assertThrows(InvocationTargetException::class.java) {
            buildRequest(
                ProviderSetting.OpenAI(toolResultNameMode = ToolResultNameMode.INCLUDE),
                executedToolMessage("", "tool"),
            )
        }

        assertTrue(error.cause is IllegalArgumentException)
        assertTrue(error.cause?.message.orEmpty().contains("tool_call_id"))
    }

    @Test
    fun `compatibility rejection is never retried automatically`() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(400)
                .addHeader("Content-Type", "application/json")
                .body("{\"error\":{\"message\":\"tool result name rejected\"}}")
                .build()
        )
        val baseUrl = server.url("/v1").toString().removeSuffix("/")
        val setting = ProviderSetting.OpenAI(
            baseUrl = baseUrl,
            apiKey = "test-key",
            toolResultNameMode = ToolResultNameMode.INCLUDE,
        )

        val result = runCatching {
            api.generateText(
                providerSetting = setting,
                messages = listOf(executedToolMessage("call-once", "tool")),
                params = TextGenerationParams(model = model),
            )
        }

        assertTrue("HTTP 400 must surface as a failure", result.isFailure)
        assertTrue(
            "The failure must preserve the rejected HTTP status",
            result.exceptionOrNull()?.message.orEmpty().contains("400"),
        )
        assertEquals("Compatibility rejection must not trigger a second request", 1, server.requestCount)
    }

    private fun executedToolMessage(callId: String, toolName: String): UIMessage = UIMessage(
        role = MessageRole.ASSISTANT,
        parts = listOf(
            UIMessagePart.Tool(
                toolCallId = callId,
                toolName = toolName,
                input = "{}",
                output = listOf(UIMessagePart.Text("ok")),
            )
        ),
    )

    private fun toolResult(body: JsonObject): JsonObject = body["messages"]
        ?.jsonArray
        ?.map { it.jsonObject }
        ?.single { it["role"]?.jsonPrimitive?.content == "tool" }
        ?: error("tool result message not found")

    private fun buildRequest(
        providerSetting: ProviderSetting.OpenAI,
        message: UIMessage,
        params: TextGenerationParams = TextGenerationParams(model = model),
    ): JsonObject {
        val method = ChatCompletionsAPI::class.java.getDeclaredMethod(
            "buildChatCompletionRequest",
            List::class.java,
            TextGenerationParams::class.java,
            ProviderSetting.OpenAI::class.java,
            Boolean::class.javaPrimitiveType,
        )
        method.isAccessible = true
        return method.invoke(api, listOf(message), params, providerSetting, false) as JsonObject
    }
}