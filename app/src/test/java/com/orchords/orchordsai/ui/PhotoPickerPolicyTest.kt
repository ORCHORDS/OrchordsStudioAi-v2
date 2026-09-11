package com.orchords.orchordsai.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoPickerPolicyTest {
    private val root = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .first { File(it, "app/src/main/java").isDirectory }

    private fun source(path: String): String = File(root, "app/src/main/java/$path").readText()

    @Test
    fun `all image-only surfaces use AndroidX Photo Picker ImageOnly`() {
        val chat = source("com/orchords/orchordsai/ui/components/ai/ChatAttachmentPicker.kt")
        val chatImageBlock = chat.substringAfter("val imagePickerLauncher =")
            .substringBefore("val videoPickerLauncher =")
        assertTrue(chatImageBlock.contains("ActivityResultContracts.PickMultipleVisualMedia()"))
        assertTrue(chat.contains("PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)"))
        assertFalse(chatImageBlock.contains("GetMultipleContents()"))
        assertFalse(chat.contains("imagePickerLauncher.launch(\"image/*\")"))

        val avatar = source("com/orchords/orchordsai/ui/components/ui/UIAvatar.kt")
        assertTrue(avatar.contains("contract = ActivityResultContracts.PickVisualMedia()"))
        assertTrue(avatar.contains("PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)"))
        assertFalse(avatar.contains("contract = ActivityResultContracts.GetContent()"))
        assertFalse(avatar.contains("imagePickerLauncher.launch(\"image/*\")"))

        val background = source("com/orchords/orchordsai/ui/pages/assistant/detail/BackgroundPicker.kt")
        assertTrue(background.contains("contract = ActivityResultContracts.PickVisualMedia()"))
        assertTrue(background.contains("PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)"))
        assertFalse(background.contains("contract = ActivityResultContracts.GetContent()"))
        assertFalse(background.contains("imagePickerLauncher.launch(\"image/*\")"))

        val imageGen = source("com/orchords/orchordsai/ui/pages/imggen/ImgGenPage.kt")
        assertTrue(imageGen.contains("ActivityResultContracts.PickMultipleVisualMedia()"))
        assertTrue(imageGen.contains("PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)"))
        assertFalse(imageGen.contains("ActivityResultContracts.GetMultipleContents()"))
        assertFalse(imageGen.contains("imagePickerLauncher.launch(\"image/*\")"))
    }

    @Test
    fun `chat non-image picker behavior remains on existing contracts`() {
        val chat = source("com/orchords/orchordsai/ui/components/ai/ChatAttachmentPicker.kt")
        val afterImage = chat.substringAfter("val videoPickerLauncher =")
        assertTrue(afterImage.contains("ActivityResultContracts.GetMultipleContents()"))
        assertTrue(afterImage.contains("videoPickerLauncher.launch(\"video/*\")"))
        assertTrue(afterImage.contains("audioPickerLauncher.launch(\"audio/*\")"))
        assertTrue(afterImage.contains("ActivityResultContracts.OpenMultipleDocuments()"))
        assertTrue(afterImage.contains("filePickerLauncher.launch(arrayOf(\"*/*\"))"))
    }
}
