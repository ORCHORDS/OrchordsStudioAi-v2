package com.orchords.ai.provider.providers.google

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.orchords.ai.core.MessageRole
import com.orchords.ai.provider.stream.SseEvent
import com.orchords.ai.ui.GoogleThoughtMetadata
import com.orchords.ai.ui.StreamChunk
import com.orchords.ai.ui.ToolApprovalState
import com.orchords.ai.ui.UIMessage
import com.orchords.ai.ui.UIMessagePart
import com.orchords.ai.ui.metadataAs
import com.orchords.ai.ui.toMetadata
import com.orchords.ai.util.json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleFunctionCallIdTest {
    private val provider = GoogleProvider(OkHttpClient())

    @Test
    fun `explicit provider id round trips unchanged`() {
        val parsed = parseFunctionCall(
            """{"functionCall":{"id":"provider-call-1","name":"echo","args":{"value":1}},"thoughtSignature":"sig-1"}"""
        )

        assertEquals("provider-call-1", parsed.toolCallId)
        assertEquals("provider-call-1", parsed.metadataAs<GoogleThoughtMetadata>()?.functionCallId)
        assertEquals("sig-1", parsed.metadataAs<GoogleThoughtMetadata>()?.thoughtSignature)

        val (call, response) = serializeExecuted(parsed)
        assertEquals("provider-call-1", call["id"]?.jsonPrimitive?.content)
        assertEquals("provider-call-1", response["id"]?.jsonPrimitive?.content)
    }

    @Test
    fun `absent provider id stays absent while local identity remains stable`() {
        val parsed = parseFunctionCall(
            """{"functionCall":{"name":"echo","args":{"value":1}},"thoughtSignature":"sig-2"}"""
        )
        val localId = parsed.toolCallId

        assertTrue(localId.isNotBlank())
        assertNull(parsed.metadataAs<GoogleThoughtMetadata>()?.functionCallId)

        val restored = persistReload(parsed.copy(approvalState = ToolApprovalState.Pending))
        assertEquals(localId, restored.toolCallId)
        assertEquals(ToolApprovalState.Pending, restored.approvalState)
        assertNull(restored.metadataAs<GoogleThoughtMetadata>()?.functionCallId)

        val (call, response) = serializeExecuted(restored)
        assertFalse(call.containsKey("id"))
        assertFalse(response.containsKey("id"))
    }

    @Test
    fun `parallel same-name calls without provider ids keep distinct local identities`() {
        val first = parseFunctionCall("""{"functionCall":{"name":"echo","args":{"value":1}}}""")
        val second = parseFunctionCall("""{"functionCall":{"name":"echo","args":{"value":2}}}""")

        assertNotEquals(first.toolCallId, second.toolCallId)
        assertNull(first.metadataAs<GoogleThoughtMetadata>()?.functionCallId)
        assertNull(second.metadataAs<GoogleThoughtMetadata>()?.functionCallId)

        val contents = buildContents(
            listOf(
                UIMessage(
                    role = MessageRole.ASSISTANT,
                    parts = listOf(executed(first), executed(second)),
                )
            )
        )
        val calls = contents.flatMap { message ->
            message.jsonObject["parts"]?.jsonArray.orEmpty()
                .mapNotNull { it.jsonObject["functionCall"]?.jsonObject }
        }
        val responses = contents.flatMap { message ->
            message.jsonObject["parts"]?.jsonArray.orEmpty()
                .mapNotNull { it.jsonObject["functionResponse"]?.jsonObject }
        }

        assertEquals(2, calls.size)
        assertEquals(2, responses.size)
        assertTrue(calls.all { !it.containsKey("id") })
        assertTrue(responses.all { !it.containsKey("id") })
    }

    @Test
    fun `stream decoder preserves explicit provider id metadata`() {
        val decoder = GoogleStreamDecoder(responseId = "response-1", model = "gemini-test")
        val result = decoder.accept(
            SseEvent(
                id = null,
                event = null,
                data = """{"candidates":[{"content":{"role":"model","parts":[{"functionCall":{"id":"stream-provider-id","name":"echo","args":{"value":1}},"thoughtSignature":"stream-sig"}]}}]}""",
            )
        )

        val start = result.chunks.filterIsInstance<StreamChunk.ToolCallStart>().single()
        assertEquals("stream-provider-id", start.id)
        val metadata = json.decodeFromJsonElement(GoogleThoughtMetadata.serializer(), assertNotNull(start.metadata))
        assertEquals("stream-provider-id", metadata.functionCallId)
        assertEquals("stream-sig", metadata.thoughtSignature)
    }

    @Test
    fun `stream decoder keeps absent provider id out of metadata and uses local correlation id`() {
        val decoder = GoogleStreamDecoder(responseId = "response-2", model = "gemini-test")
        val result = decoder.accept(
            SseEvent(
                id = null,
                event = null,
                data = """{"candidates":[{"content":{"role":"model","parts":[{"functionCall":{"name":"echo","args":{"value":1}}}]}}]}""",
            )
        )

        val start = result.chunks.filterIsInstance<StreamChunk.ToolCallStart>().single()
        assertEquals("response-2:tool-1", start.id)
        val metadata = json.decodeFromJsonElement(GoogleThoughtMetadata.serializer(), assertNotNull(start.metadata))
        assertNull(metadata.functionCallId)
    }

    @Test
    fun `server tool local fallback identity never becomes provider wire id`() {
        val rawCall = json.parseToJsonElement(
            """{"toolCall":{"toolType":"googleSearch","args":{"q":"orchords"}}}"""
        ).jsonObject
        val parsed = parseMessagePart(rawCall)
        val serverTool = parsed as UIMessagePart.ServerTool

        assertTrue(serverTool.toolCallId.isNotBlank())
        val contents = buildContents(
            listOf(UIMessage(role = MessageRole.ASSISTANT, parts = listOf(serverTool)))
        )
        val replayed = contents.single().jsonObject["parts"]!!.jsonArray.single().jsonObject
        assertEquals(rawCall, replayed)
        assertFalse(replayed["toolCall"]!!.jsonObject.containsKey("id"))
    }

    private fun parseFunctionCall(raw: String): UIMessagePart.Tool =
        parseMessagePart(json.parseToJsonElement(raw).jsonObject) as UIMessagePart.Tool

    private fun parseMessagePart(raw: JsonObject): UIMessagePart {
        val method = GoogleProvider::class.java.getDeclaredMethod(
            "parseMessagePart",
            JsonObject::class.java,
            Int::class.javaPrimitiveType,
        )
        method.isAccessible = true
        return method.invoke(provider, raw, 0) as UIMessagePart
    }

    private fun buildContents(messages: List<UIMessage>): JsonArray {
        val method = GoogleProvider::class.java.getDeclaredMethod("buildContents", List::class.java)
        method.isAccessible = true
        return method.invoke(provider, messages) as JsonArray
    }

    private fun executed(tool: UIMessagePart.Tool): UIMessagePart.Tool =
        tool.copy(output = listOf(UIMessagePart.Text("ok")))

    private fun serializeExecuted(tool: UIMessagePart.Tool): Pair<JsonObject, JsonObject> {
        val contents = buildContents(
            listOf(UIMessage(role = MessageRole.ASSISTANT, parts = listOf(executed(tool))))
        )
        val call = contents[0].jsonObject["parts"]!!.jsonArray.single().jsonObject["functionCall"]!!.jsonObject
        val response = contents[1].jsonObject["parts"]!!.jsonArray.single().jsonObject["functionResponse"]!!.jsonObject
        return call to response
    }

    private fun persistReload(tool: UIMessagePart.Tool): UIMessagePart.Tool {
        val encoded = json.encodeToString(UIMessagePart.Tool.serializer(), tool)
        return json.decodeFromString(UIMessagePart.Tool.serializer(), encoded)
    }
}
