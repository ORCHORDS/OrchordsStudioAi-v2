package com.orchords.ai.provider.providers.openai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ResponseApiLoggingPrivacyPolicyTest {
    private fun source(): String {
        val file = File("src/main/java/com/orchords/ai/provider/providers/openai/ResponseAPI.kt")
        require(file.isFile) { "ResponseAPI.kt not found from ${File(".").canonicalPath}" }
        return file.readText()
    }

    @Test
    fun `responses api logs metadata instead of request response or stream bodies`() {
        val source = source()

        assertFalse(source.contains("generateText: \${json.encodeToString(requestBody)}"))
        assertFalse(source.contains("generateText: \$bodyStr"))
        assertFalse(source.contains("streamText: \${json.encodeToString(requestBody)}"))
        assertFalse(source.contains("onEvent: \$id/\$type \$data"))
        assertFalse(source.contains("println(jsonObject)"))
        assertTrue(source.contains("messages=\${messages.size} tools=\${params.tools.size}"))
        assertTrue(source.contains("streamEvent: type=\${type ?: \"message\"} bytes=\${data.length}"))
    }

    @Test
    fun `responses api failure logging never dumps bodies or stack traces`() {
        val source = source()

        assertFalse(source.contains("printStackTrace()"))
        assertFalse(source.contains("println("))
        assertFalse(source.contains("failed to parse from \$bodyRaw"))
        assertTrue(source.contains("streamFailure: status=\${response?.code ?: 0}"))
        assertTrue(source.contains("encode response image failed type=\${it.javaClass.simpleName}"))
    }
}
