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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.InvocationTargetException

class ChatCompletionsToolResultNameTest {
    private lateinit var api: ChatCompletionsAPI

    private val model = Model(
        modelId = "test-tool-model",
        abilities = listOf(ModelAbility.TOOL),
    )

    @Before
    fun setUp() {
        api = ChatCompletionsAPI(OkHttpClient(), KeyRoulette.default())
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
    fun `arbitrary compatible endpoint AUTO omits tool result name per first party contract`() {
        // Regression for #353: AUTO on a non-Orchards host omits the optional name
        // member. The canonical gateway has no name field requirement; any other
        // OpenAI-compatible endpoint needing the field must opt in with INCLUDE.
        val body = buildRequest(
            providerSetting = ProviderSetting.OpenAI(
                baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
                toolResultNameMode = ToolResultNameMode.AUTO,
            ),
            message = executedToolMessage("call-gemini", "provider_safe_name"),
        )

        val result = toolResult(body)
        assertEquals("call-gemini", result["tool_call_id"]?.jsonPrimitive?.content)
        assertFalse(result.containsKey("name"))
    }

    @Test
    fun `arbitrary compatible endpoint can still include tool result name via explicit INCLUDE`() {
        // Regression for #353: the INCLUDE override is preserved for bridges that
        // explicitly require the provider-visible tool name. AUTO never implies it.
        val body = buildRequest(
            providerSetting = ProviderSetting.OpenAI(
                baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
                toolResultNameMode = ToolResultNameMode.INCLUDE,
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
    fun `Orchords gateway AUTO mode omits tool result name for first party contract`() {
        // Regression for #353: under AUTO and the canonical Orchords gateway host,
        // role=tool messages must omit the optional name member. The production
        // OAI-1.0 route only binds results through tool_call_id and never relies
        // on the gateway receiving a name field that originated from internal
        // display text or aliases.
        val body = buildRequest(
            providerSetting = ProviderSetting.OpenAI(
                baseUrl = "https://api.orchords.com/v1",
                toolResultNameMode = ToolResultNameMode.AUTO,
            ),
            message = executedToolMessage("call-orchords", "orchords_search"),
        )

        val result = toolResult(body)
        assertEquals("call-orchords", result["tool_call_id"]?.jsonPrimitive?.content)
        assertFalse(
            "OAI-1.0 role=tool result must not serialize a name field for the canonical gateway",
            result.containsKey("name"),
        )
    }

    @Test
    fun `unknown arbitrary OpenAI compatible endpoint AUTO omits tool result name`() {
        // Regression for #353: with AUTO on a host that is not the canonical
        // Orchords gateway, the resolver must omit the optional name member.
        // Cross-provider compatibility matrices are explicitly out of scope for
        // the first-party oai-1.0 contract.
        val body = buildRequest(
            providerSetting = ProviderSetting.OpenAI(
                baseUrl = "https://arbitrary.example/v1",
                toolResultNameMode = ToolResultNameMode.AUTO,
            ),
            message = executedToolMessage("call-unknown", "tool"),
        )

        val result = toolResult(body)
        assertEquals("call-unknown", result["tool_call_id"]?.jsonPrimitive?.content)
        assertFalse(result.containsKey("name"))
    }

    @Test
    fun `parallel MCP tool calls remain unambiguous on the Orchords gateway`() {
        // Regression for #353: locally we may hold namespaced MCP tool identities
        // (with `/` and `.`), but on the canonical gateway the wire shape stays
        // deterministic per call: tool_call_id binds the result, no result-name
        // derivation from display text, and unrelated ids remain unambiguous.
        val canonical = "mcp__workspace/server.read-file"
        val messageA = executedToolMessage("call_a", canonical)
        val messageB = executedToolMessage("call_b", "orchords_search")
        val body = buildRequest(
            providerSetting = ProviderSetting.OpenAI(
                baseUrl = "https://api.orchords.com/v1",
                toolResultNameMode = ToolResultNameMode.AUTO,
            ),
            message = mergeToolMessages(messageA, messageB),
        )
        val results = body["messages"]?.jsonArray
            ?.map { it.jsonObject }
            ?.filter { it["role"]?.jsonPrimitive?.content == "tool" }
            ?: error("tool result messages missing")

        val callIds = results.map { it["tool_call_id"]?.jsonPrimitive?.content }
        assertEquals(listOf("call_a", "call_b"), callIds)
        assertTrue(
            "no tool result should attach a name on the first-party gateway",
            results.none { it.containsKey("name") },
        )
    }

    private fun mergeToolMessages(a: UIMessage, b: UIMessage): UIMessage = UIMessage(
        role = MessageRole.ASSISTANT,
        parts = (a.parts + b.parts),
    )

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