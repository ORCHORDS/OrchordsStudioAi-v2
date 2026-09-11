package com.orchords.orchordsai.ui.pages.imggen

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratedMediaFilePolicyTest {
    @Test
    fun `generated filenames stay opaque and inside exact root`() {
        val root = Files.createTempDirectory("orchords-media").toFile()
        try {
            val first = allocateGeneratedMediaFile(
                root = root,
                kind = GeneratedMediaFileKind.FINAL,
                timestamp = 1234L,
                index = 2,
                nonce = "local-abc",
            )
            val second = allocateGeneratedMediaFile(
                root = root,
                kind = GeneratedMediaFileKind.PREVIEW,
                timestamp = 1234L,
                index = 2,
                nonce = "local-def",
            )

            assertEquals(root.canonicalFile, first.parentFile.canonicalFile)
            assertEquals(root.canonicalFile, second.parentFile.canonicalFile)
            assertEquals("imggen_1234_local-abc_2.png", first.name)
            assertEquals("imggen-preview_1234_local-def_2.png", second.name)
            assertNotEquals(first.name, second.name)
            assertFalse(first.name.contains("model"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `path shaped nonce is rejected before target construction`() {
        val root = Files.createTempDirectory("orchords-media").toFile()
        try {
            listOf("../../escape", "a/b", "a\\b", "..", "bad nonce").forEach { nonce ->
                assertThrows(IllegalArgumentException::class.java) {
                    allocateGeneratedMediaFile(
                        root = root,
                        kind = GeneratedMediaFileKind.FINAL,
                        timestamp = 1L,
                        index = 0,
                        nonce = nonce,
                    )
                }
            }
            assertTrue(root.listFiles().orEmpty().isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }
}
