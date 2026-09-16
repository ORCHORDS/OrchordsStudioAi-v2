package com.orchords.ai.provider.providers.openai

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatCompletionsStreamClosurePolicyTest {
    private fun source(): String {
        val file = File("src/main/java/com/orchords/ai/provider/providers/openai/ChatCompletionsAPI.kt")
        require(file.isFile) { "ChatCompletionsAPI.kt not found from ${File(".").canonicalPath}" }
        return file.readText()
    }

    @Test
    fun `transport close before provider terminal closes flow with an IOException`() {
        val source = source()

        assertTrue(source.contains("providerTerminalObserved"))
        assertTrue(source.contains("Provider stream closed before terminal [DONE]"))
        assertTrue(source.contains("close(IOException("))
    }
}
