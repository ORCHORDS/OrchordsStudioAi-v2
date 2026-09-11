package com.orchords.orchordsai.ui.pages.imggen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratedMediaPathSourcePolicyTest {
    private val root = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .first { File(it, "app/src/main/java").isDirectory }

    private fun source(path: String): String = File(root, path).readText()

    @Test
    fun `image generation never uses model display name as filename identity`() {
        val source = source("app/src/main/java/com/orchords/orchordsai/ui/pages/imggen/ImgGenVM.kt")
        val preview = source.substringAfter("private fun saveImagePreview").substringBefore("private suspend fun saveImageToStorage")
        val final = source.substringAfter("private suspend fun saveImageToStorage").substringBefore("fun deleteImage")

        assertTrue(preview.contains("allocateGeneratedMediaFile"))
        assertTrue(final.contains("allocateGeneratedMediaFile"))
        assertFalse(preview.contains("modelName"))
        assertFalse(final.contains("${'$'}{modelName}"))
    }
}
