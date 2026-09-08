package com.orchords.orchordsai.data.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GenerationLoggingPrivacyPolicyTest {
    private fun generationSource(): String {
        val source = File("src/main/java/com/orchords/orchordsai/data/ai/GenerationHandler.kt")
        require(source.isFile) { "GenerationHandler.kt not found from ${File(".").canonicalPath}" }
        return source.readText()
    }

    @Test
    fun `generation logs never serialize assistant configuration or tool arguments`() {
        val source = generationSource()

        assertFalse(source.contains("build tools(\$assistant)"))
        assertFalse(source.contains("with args: \$args"))
        assertTrue(source.contains("build tools count=\${tools.size} memoryEnabled=\${assistant.enableMemory}"))
        assertTrue(source.contains("executing tool \${toolDef.name}"))
    }

    @Test
    fun `generation logging never dumps exception objects or stack traces`() {
        val source = generationSource()

        assertFalse(source.contains("it.printStackTrace()"))
        assertTrue(source.contains("tool execution failed type=\${it.javaClass.simpleName}"))
        assertTrue(source.contains("Provider connection failed type=\${error.javaClass.simpleName}"))
        assertFalse(
            "Network retry logging must not pass the exception object to Log.w",
            source.contains("(\$nextRetryCount/\$MAX_PROVIDER_NETWORK_RETRIES)\",\n            error,"),
        )
    }
}
