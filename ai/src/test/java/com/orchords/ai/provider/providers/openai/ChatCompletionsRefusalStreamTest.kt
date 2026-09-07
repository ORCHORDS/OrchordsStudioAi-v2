package com.orchords.ai.provider.providers.openai

import com.orchords.ai.core.MessageRole
import com.orchords.ai.provider.stream.SseEvent
import com.orchords.ai.ui.OpenAIRefusalMetadata
import com.orchords.ai.ui.StreamChunk
import com.orchords.ai.ui.StreamChunkHandler
import com.orchords.ai.ui.UIMessage
import com.orchords.ai.ui.UIMessagePart
import com.orchords.ai.ui.metadataAs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ChatCompletionsRefusalStreamTest {
    @Test
    fun `streamed refusal is visible and retains refusal metadata`() {
        val decoder = ChatCompletionsStreamDecoder()
        val first = decoder.accept(
            SseEvent(
                data = """{"id":"chatcmpl-refusal","model":"gpt-test","choices":[{"index":0,"delta":{"role":"assistant","refusal":"I can't"},"finish_reason":null}]}""",
            )
        )
        val second = decoder.accept(
            SseEvent(
                data = """{"id":"chatcmpl-refusal","model":"gpt-test","choices":[{"index":0,"delta":{"refusal":" help with that."},"finish_reason":"stop"}]}""",
            )
        )
        val done = decoder.accept(SseEvent(data = "[DONE]"))

        val chunks = first.chunks + second.chunks + done.chunks
        val handler = StreamChunkHandler()
        val messages = chunks.fold(listOf(UIMessage.user("request"))) { current, chunk ->
            handler.handle(current, chunk)
        }
        val text = messages.last().parts.filterIsInstance<UIMessagePart.Text>().single()

        assertEquals("I can't help with that.", text.text)
        assertNotNull(text.metadataAs<OpenAIRefusalMetadata>())
        assertEquals(true, text.metadataAs<OpenAIRefusalMetadata>()?.refusal)
    }

    @Test
    fun `ordinary content and refusal use distinct streaming text parts`() {
        val decoder = ChatCompletionsStreamDecoder()
        val content = decoder.accept(
            SseEvent(
                data = """{"id":"chatcmpl-mixed","choices":[{"index":0,"delta":{"role":"assistant","content":"Visible content"},"finish_reason":null}]}""",
            )
        )
        val refusal = decoder.accept(
            SseEvent(
                data = """{"id":"chatcmpl-mixed","choices":[{"index":0,"delta":{"refusal":"Refusal text"},"finish_reason":"stop"}]}""",
            )
        )

        val contentStart = content.chunks.filterIsInstance<StreamChunk.TextStart>().single()
        val refusalStart = refusal.chunks.filterIsInstance<StreamChunk.TextStart>().single()
        assertNotEquals(contentStart.id, refusalStart.id)
        assertNull(contentStart.metadata)
        assertNotNull(refusalStart.metadata)

        val handler = StreamChunkHandler()
        val messages = (content.chunks + refusal.chunks).fold(
            listOf(UIMessage(role = MessageRole.USER, parts = listOf(UIMessagePart.Text("request"))))
        ) { current, chunk -> handler.handle(current, chunk) }
        val parts = messages.last().parts.filterIsInstance<UIMessagePart.Text>()
        assertEquals(2, parts.size)
        assertEquals("Visible content", parts[0].text)
        assertNull(parts[0].metadataAs<OpenAIRefusalMetadata>())
        assertEquals("Refusal text", parts[1].text)
        assertNotNull(parts[1].metadataAs<OpenAIRefusalMetadata>())
    }
}
