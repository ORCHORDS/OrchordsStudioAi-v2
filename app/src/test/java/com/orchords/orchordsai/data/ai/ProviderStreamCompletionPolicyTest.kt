package com.orchords.orchordsai.data.ai

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderStreamCompletionPolicyTest {
    private val root = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .first { File(it, "app/src/main/java").isDirectory }

    private fun generationHandlerSource(): String =
        File(root, "app/src/main/java/com/orchords/orchordsai/data/ai/GenerationHandler.kt").readText()

    @Test
    fun `terminal-only or incomplete provider streams cannot be committed as successful generations`() {
        val source = generationHandlerSource()

        assertTrue(source.contains("GenerationTerminationCategory.STREAM_INCOMPLETE"))
        assertTrue(source.contains("GenerationTerminationCategory.EMPTY_RESPONSE"))
        assertTrue(source.contains("hasReceivedProviderOutput"))
        assertTrue(source.contains("Provider stream completed without usable assistant output"))
    }
}
