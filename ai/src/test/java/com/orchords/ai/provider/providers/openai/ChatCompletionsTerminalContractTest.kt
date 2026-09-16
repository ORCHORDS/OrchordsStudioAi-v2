package com.orchords.ai.provider.providers.openai

import com.orchords.ai.provider.Model
import com.orchords.ai.provider.stream.SseEvent
import com.orchords.ai.ui.GenerationTerminationCategory
import com.orchords.ai.ui.StreamChunkHandler
import com.orchords.ai.ui.UIMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatCompletionsTerminalContractTest {
    private val model = Model(modelId = "test-model")

    @Test
    fun `done marker without finish reason is still an observed provider terminal`() {
        val decoder = ChatCompletionsStreamDecoder()
        val result = decoder.accept(SseEvent(data = "[DONE]"))
        var messages = listOf(UIMessage.user("hello"))
        val handler = StreamChunkHandler(model)

        result.chunks.forEach { chunk ->
            messages = handler.handle(messages, chunk)
        }

        assertTrue(result.completed)
        assertTrue(messages.last().termination?.providerTerminalObserved == true)
        assertEquals(
            GenerationTerminationCategory.EMPTY_RESPONSE,
            messages.last().termination?.category,
        )
    }

    @Test
    fun `transport close without done remains incomplete`() {
        val decoder = ChatCompletionsStreamDecoder()
        var messages = listOf(UIMessage.user("hello"))
        val handler = StreamChunkHandler(model)

        decoder.onClosed().forEach { chunk ->
            messages = handler.handle(messages, chunk)
        }

        assertFalse(messages.last().termination?.providerTerminalObserved ?: true)
        assertEquals(
            GenerationTerminationCategory.STREAM_INCOMPLETE,
            messages.last().termination?.category,
        )
    }
}
