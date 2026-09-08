package com.orchords.ai.provider.providers.openai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ChatCompletionsLoggingPrivacyPolicyTest {
    private fun source(): String {
        val file = File("src/main/java/com/orchords/ai/provider/providers/openai/ChatCompletionsAPI.kt")
        require(file.isFile) { "ChatCompletionsAPI.kt not found from ${File(".").canonicalPath}" }
        return file.readText()
    }

    @Test
    fun `chat completions logs structural metadata instead of request or stream content`() {
        val source = source()

        assertFalse(source.contains("generateText: \${json.encodeToString(requestBody)}"))
        assertFalse(source.contains("streamText: \${json.encodeToString(requestBody)}"))
        assertFalse(source.contains("onEvent: \$data"))
        assertTrue(source.contains("messages=\${messages.size} tools=\${params.tools.size}"))
        assertTrue(source.contains("streamEvent: type=\${type ?: \"message\"} bytes=\${data.length}"))
    }

    @Test
    fun `chat completions failure logging never dumps bodies stack traces or private paths`() {
        val source = source()

        assertFalse(source.contains("printStackTrace()"))
        assertFalse(source.contains("println("))
        assertFalse(source.contains("failed to parse from \$bodyRaw"))
        assertFalse(source.contains("\${part.url}"))
        assertTrue(source.contains("streamFailure: status=\${response?.code ?: 0}"))
        assertTrue(source.contains("encode tool result image failed type=\${it.javaClass.simpleName}"))
    }
}
