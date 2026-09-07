package com.orchords.ai.provider.providers.openai

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.orchords.ai.ui.OpenAIRefusalMetadata
import com.orchords.ai.ui.UIMessage
import com.orchords.ai.ui.UIMessagePart
import com.orchords.ai.ui.metadataAs
import com.orchords.ai.util.KeyRoulette
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ChatCompletionsRefusalParseTest {
    private val api = ChatCompletionsAPI(OkHttpClient(), KeyRoulette.default())

    private fun parse(message: JsonObject): UIMessage {
        val method = ChatCompletionsAPI::class.java.getDeclaredMethod(
            "parseMessage",
            JsonObject::class.java,
        )
        method.isAccessible = true
        return method.invoke(api, message) as UIMessage
    }

    @Test
    fun `refusal-only response remains visible instead of becoming blank`() {
        val parsed = parse(
            buildJsonObject {
                put("role", "assistant")
                put("refusal", "I can't help with that.")
            }
        )

        val text = parsed.parts.filterIsInstance<UIMessagePart.Text>().single()
        assertEquals("I can't help with that.", text.text)
        assertNotNull(text.metadataAs<OpenAIRefusalMetadata>())
    }

    @Test
    fun `ordinary content and refusal remain distinct provider output parts`() {
        val parsed = parse(
            buildJsonObject {
                put("role", "assistant")
                put("content", "Ordinary content")
                put("refusal", "Refusal content")
            }
        )

        val parts = parsed.parts.filterIsInstance<UIMessagePart.Text>()
        assertEquals(2, parts.size)
        assertEquals("Ordinary content", parts[0].text)
        assertNull(parts[0].metadataAs<OpenAIRefusalMetadata>())
        assertEquals("Refusal content", parts[1].text)
        assertNotNull(parts[1].metadataAs<OpenAIRefusalMetadata>())
    }
}
