package com.orchords.ai.provider.providers.claude

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ClaudeLoggingPrivacyPolicyTest {
    private fun source(): String {
        val file = File("src/main/java/com/orchords/ai/provider/providers/claude/ClaudeProvider.kt")
        require(file.isFile) { "ClaudeProvider.kt not found from ${File(".").canonicalPath}" }
        return file.readText()
    }

    @Test
    fun `claude logs structural metadata instead of request message or stream content`() {
        val source = source()

        assertFalse(source.contains("generateText: \${json.encodeToString(requestBody)}"))
        assertFalse(source.contains("streamText: \${json.encodeToString(requestBody)}"))
        assertFalse(source.contains("requestBody[\"messages\"]!!.jsonArray.forEach"))
        assertFalse(source.contains("onEvent: type=\$type, data=\$data"))
        assertTrue(source.contains("messages=\${messages.size} tools=\${params.tools.size}"))
        assertTrue(source.contains("streamEvent: type=\${type ?: \"message\"} bytes=\${data.length}"))
    }

    @Test
    fun `claude failure and media logging never emits raw bodies paths or stack traces`() {
        val source = source()

        assertFalse(source.contains("printStackTrace()"))
        assertFalse(source.contains("Error response: \$bodyElement"))
        assertFalse(source.contains("failed to parse from \$bodyRaw"))
        assertFalse(source.contains("encode image failed: \$url"))
        assertFalse(source.contains("println(data)"))
        assertTrue(source.contains("streamFailure: status=\${response?.code ?: 0}"))
        assertTrue(source.contains("encode image failed type=\${it.javaClass.simpleName}"))
    }
}
