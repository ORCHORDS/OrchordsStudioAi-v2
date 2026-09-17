package com.orchords.ai.provider.providers.openai

import com.orchords.ai.core.MessageRole
import com.orchords.ai.provider.Model
import com.orchords.ai.provider.stream.SseEvent
import com.orchords.ai.ui.GenerationTerminationCategory
import com.orchords.ai.ui.StreamChunk
import com.orchords.ai.ui.StreamChunkHandler
import com.orchords.ai.ui.UIMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatCompletionsStreamRuntimeRegressionTest {
    private val model = Model(modelId = "runtime-test-model")

    private fun streamChunks(data: List<String>, transportClosed: Boolean = true): List<StreamChunk> {
        val decoder = ChatCompletionsStreamDecoder()
        val emitted = data.flatMap { decoder.accept(SseEvent(data = it)).chunks }.toMutableList()
        if (transportClosed) emitted += decoder.onClosed()
        return emitted
    }

    private fun handleToMessages(
        handler: StreamChunkHandler,
        chunks: List<StreamChunk>,
        initial: List<UIMessage> = listOf(UIMessage.user("hi")),
    ): List<UIMessage> {
        var messages = initial
        for (chunk in chunks) {
            messages = handler.handle(messages, chunk)
        }
        return messages
    }

    @Test
    fun `transport close before done is observable and produces incomplete terminal`() {
        // Mirrors issue #22: stream produces only partial content and never emits [DONE].
        val chunks = streamChunks(
            listOf(
                """{"choices":[{"delta":{"content":"Partial answer for KL"}}]}""",
                """{"choices":[{"delta":{"content":" weather right now"}}]}""",
                // no [DONE] - transport closes here
            ),
        )
        assertTrue("expected at least one streamed chunk", chunks.isNotEmpty())
        val messages = handleToMessages(StreamChunkHandler(model), chunks)
        val assistant = messages.last()
        assertEquals("assistant terminal required", MessageRole.ASSISTANT, assistant.role)
        val termination = assistant.termination
        assertNotEquals(
            "partial stream must NOT look completed",
            GenerationTerminationCategory.COMPLETED,
            termination?.category,
        )
        assertTrue(
            "partial stream must mark provider terminal not observed",
            termination?.providerTerminalObserved == false,
        )
        assertEquals(GenerationTerminationCategory.STREAM_INCOMPLETE, termination?.category)
        assertTrue("partial content must be preserved", assistant.toText().contains("Partial"))
    }

    @Test
    fun `done marker without finish reason is still an observed provider terminal`() {
        val chunks = streamChunks(
            listOf(
                """{"choices":[{"delta":{"content":"Done payload"}}]}""",
                "[DONE]",
            ),
            transportClosed = false,
        )
        val messages = handleToMessages(StreamChunkHandler(model), chunks)
        val termination = messages.last().termination
        assertTrue(
            "explicit [DONE] must count as a provider terminal observation",
            termination?.providerTerminalObserved == true,
        )
    }

    @Test
    fun `empty terminal response is classified explicitly not as completed`() {
        val chunks = streamChunks(
            listOf(
                """{"choices":[{"delta":{}}]}""",
                "[DONE]",
            ),
            transportClosed = false,
        )
        val messages = handleToMessages(StreamChunkHandler(model), chunks)
        val termination = messages.last().termination
        assertTrue(
            "[DONE] without any content must still be classified explicitly",
            termination?.providerTerminalObserved == true,
        )
        assertFalse(
            "empty provider terminal must NOT look like a successful visible assistant turn",
            messages.last().toText().isNotEmpty(),
        )
        assertEquals(GenerationTerminationCategory.EMPTY_RESPONSE, termination?.category)
    }
}
