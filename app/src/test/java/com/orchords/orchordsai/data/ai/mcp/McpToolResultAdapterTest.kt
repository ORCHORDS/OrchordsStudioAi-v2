package com.orchords.orchordsai.data.ai.mcp

import com.orchords.ai.ui.UIMessagePart
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class McpToolResultAdapterTest {
    private val codec = Json { ignoreUnknownKeys = true }

    private suspend fun render(wire: String): List<UIMessagePart> =
        codec.decodeFromString<CallToolResult>(wire).toConversationParts(
            renderImage = { UIMessagePart.Image("fixture://managed-image") },
        )

    @Test
    fun `structured-only result reaches the conversation`() = runTest {
        val parts = render("""{"content":[],"structuredContent":{"count":2}}""")
        assertEquals("""{"structuredContent":{"count":2}}""", (parts.single() as UIMessagePart.Text).text)
    }

    @Test
    fun `tool error retains its message and error flag`() = runTest {
        val parts = render("""{"content":[{"type":"text","text":"Cannot complete"}],"isError":true}""")
        assertEquals("""{"isError":true,"status":"tool_error"}""", (parts.first() as UIMessagePart.Text).text)
        assertEquals("Cannot complete", (parts.last() as UIMessagePart.Text).text)
    }

    @Test
    fun `successful empty result is executed under the existing caller contract`() = runTest {
        val parts = render("""{"content":[]}""")
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
    }

    @Test
    fun `pinned SDK does not pretend to accept array-valued structured output`() {
        val error = runCatching {
            codec.decodeFromString<CallToolResult>("""{"content":[],"structuredContent":[1,2]}""")
        }.exceptionOrNull()
        assertTrue(error is SerializationException)
    }
}
