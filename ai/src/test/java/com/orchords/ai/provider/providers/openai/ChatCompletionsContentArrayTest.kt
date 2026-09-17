package com.orchords.ai.provider.providers.openai

import com.orchords.ai.provider.stream.SseEvent
import com.orchords.ai.ui.StreamChunkHandler
import com.orchords.ai.ui.UIMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatCompletionsContentArrayTest {
    @Test
    fun `streamed content arrays retain text across events`() {
        val decoder = ChatCompletionsStreamDecoder()
        val handler = StreamChunkHandler()
        var messages = listOf(UIMessage.user("hello"))
        listOf(
            """{"choices":[{"delta":{"content":[{"type":"text","text":"Hello"},{"type":"text","text":" "}]}}]}""",
            """{"choices":[{"delta":{"content":[null,{}, {"type":"text","text":"world"}]}}]}""",
            "[DONE]",
        ).forEach { data ->
            decoder.accept(SseEvent(data = data)).chunks.forEach { chunk ->
                messages = handler.handle(messages, chunk)
            }
        }
        assertEquals("Hello world", messages.last().toText())
    }

    @Test
    fun `message fallback accepts array content without changing string deltas`() {
        val decoder = ChatCompletionsStreamDecoder()
        val handler = StreamChunkHandler()
        var messages = listOf(UIMessage.user("hello"))
        listOf(
            """{"choices":[{"message":{"content":[{"type":"text","text":"Hello"}]}}]}""",
            """{"choices":[{"delta":{"content":" world"}}]}""",
            "[DONE]",
        ).forEach { data ->
            decoder.accept(SseEvent(data = data)).chunks.forEach { chunk ->
                messages = handler.handle(messages, chunk)
            }
        }
        assertEquals("Hello world", messages.last().toText())
    }
}
