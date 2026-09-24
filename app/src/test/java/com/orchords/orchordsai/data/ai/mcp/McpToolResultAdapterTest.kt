package com.orchords.orchordsai.data.ai.mcp

import com.orchords.ai.ui.UIMessagePart
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class McpToolResultAdapterTest {
    private val codec = Json { ignoreUnknownKeys = true }

    private suspend fun render(wire: String): List<UIMessagePart> =
        codec.decodeFromString<CallToolResult>(wire).toConversationParts(
            renderImage = { UIMessagePart.Image("fixture://managed-image") },
            renderAudio = { UIMessagePart.Audio("fixture://managed-audio") },
            renderEmbeddedResource = { embedded ->
                UIMessagePart.McpResource(
                    kind = com.orchords.ai.ui.McpResourceKind.EMBEDDED_TEXT,
                    uri = embedded.resource.uri,
                    text = "fixture-embedded",
                )
            },
        )

    @Test
    fun `structured-only result reaches the conversation`() = runTest {
        val parts = render("""{"content":[],"structuredContent":{"count":2}}""")
        val structured = parts.single() as UIMessagePart.McpStructured
        assertEquals("2", structured.content.jsonObject["count"]?.jsonPrimitive?.content)
    }

    @Test
    fun `tool error retains its message and error flag`() = runTest {
        val parts = render("""{"content":[{"type":"text","text":"Cannot complete"}],"isError":true}""")
        assertTrue((parts.first() as UIMessagePart.McpResultStatus).isError)
        assertEquals("Cannot complete", (parts.last() as UIMessagePart.Text).text)
    }

    @Test
    fun `successful empty result is executed under the existing caller contract`() = runTest {
        val parts = render("""{"content":[]}""")
        assertFalse((parts.single() as UIMessagePart.McpResultStatus).isError)
        val tool = UIMessagePart.Tool(toolCallId = "call", toolName = "empty", input = "{}", output = parts)
        assertTrue(tool.isExecuted)
    }

    @Test
    fun `protocol host metadata is excluded while dataset keys are preserved`() = runTest {
        val parts = render("""{
            "content":[{"type":"resource","resource":{"uri":"notes://example","text":"public","_meta":{"private":"RESOURCE_HOST_SENTINEL"}},"_meta":{"private":"BLOCK_HOST_SENTINEL"}}],
            "structuredContent":{"_meta":"USER_DATA_FIELD"},
            "_meta":{"private":"RESULT_HOST_SENTINEL"}
        }""")
        val serialized = codec.encodeToString(parts)
        assertFalse(serialized.contains("HOST_SENTINEL"))
        assertTrue(serialized.contains("notes://example"))
        assertTrue(serialized.contains("USER_DATA_FIELD"))
        assertTrue(parts.first() is UIMessagePart.McpResource)
        assertTrue(parts.last() is UIMessagePart.McpStructured)
    }

    @Test
    fun `audio content uses typed managed-file projection`() = runTest {
        val parts = render("""{"content":[{"type":"audio","data":"AA==","mimeType":"audio/wav"}]}""")
        assertEquals(UIMessagePart.Audio("fixture://managed-audio"), parts.single())
    }

    @Test
    fun `media conversion failure is bounded and does not leak the payload`() = runTest {
        val sentinel = "PRIVATE_BASE64_SENTINEL"
        val result = codec.decodeFromString<CallToolResult>(
            """{"content":[{"type":"audio","data":"$sentinel","mimeType":"audio/wav"}]}"""
        )
        val parts = result.toConversationParts(
            renderImage = { UIMessagePart.Image("fixture://managed-image") },
            renderAudio = { error("decode failed: $sentinel") },
            renderEmbeddedResource = { error("unexpected embedded resource") },
        )
        val text = (parts.single() as UIMessagePart.Text).text
        assertFalse(text.contains(sentinel))
        assertTrue(text.contains("\"status\":\"omitted\""))
        assertTrue(text.contains("\"reason\":\"invalid_or_too_large\""))
    }

    @Test
    fun `resource link remains typed and is not resolved by the adapter`() = runTest {
        val parts = render("""{"content":[{"type":"resource_link","name":"manual","uri":"https://example.invalid/manual","mimeType":"text/plain","size":12}]}""")
        val resource = parts.single() as UIMessagePart.McpResource
        assertEquals(com.orchords.ai.ui.McpResourceKind.LINK, resource.kind)
        assertEquals("https://example.invalid/manual", resource.uri)
        assertEquals(null, resource.localUrl)
    }

    @Test
    fun `embedded resource is delegated to bounded host storage`() = runTest {
        val parts = render("""{"content":[{"type":"resource","resource":{"uri":"notes://example","mimeType":"text/plain","text":"hello"}}]}""")
        val resource = parts.single() as UIMessagePart.McpResource
        assertEquals(com.orchords.ai.ui.McpResourceKind.EMBEDDED_TEXT, resource.kind)
        assertEquals("fixture-embedded", resource.text)
    }

    @Test
    fun `pinned SDK does not pretend to accept array-valued structured output`() {
        val error = runCatching {
            codec.decodeFromString<CallToolResult>("""{"content":[],"structuredContent":[1,2]}""")
        }.exceptionOrNull()
        assertTrue(error is SerializationException)
    }
}
