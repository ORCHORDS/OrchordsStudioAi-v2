package com.orchords.workspace

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RootfsCutoverTest {
    @Test
    fun `patches staging before replacing previous rootfs`() {
        val root = createTempDirectory("rootfs-cutover").toFile()
        try {
            val linux = root.childWithMarker("linux", "old")
            val staging = root.childWithMarker("staging", "new")

            finalizeRootfsInstall(staging, linux) { staged ->
                File(staged, "patched").writeText("yes")
            }

            assertEquals("new", File(linux, "marker").readText())
            assertTrue(File(linux, "patched").isFile)
            assertFalse(File(root, "linux.previous").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `patch failure leaves previous rootfs untouched`() {
        val root = createTempDirectory("rootfs-cutover").toFile()
        try {
            val linux = root.childWithMarker("linux", "old")
            val staging = root.childWithMarker("staging", "new")

            assertThrows(IllegalStateException::class.java) {
                finalizeRootfsInstall(staging, linux, patch = { error("patch failed") })
            }

            assertEquals("old", File(linux, "marker").readText())
            assertTrue(staging.exists())
            assertFalse(File(root, "linux.previous").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `failed final rename restores previous rootfs`() {
        val root = createTempDirectory("rootfs-cutover").toFile()
        try {
            val linux = root.childWithMarker("linux", "old")
            val staging = root.childWithMarker("staging", "new")
            val moves = mutableListOf<String>()

            assertThrows(IllegalArgumentException::class.java) {
                finalizeRootfsInstall(
                    stagingDir = staging,
                    linuxDir = linux,
                    patch = {},
                    move = { source, target ->
                        moves += "${source.name}->${target.name}"
                        when (moves.size) {
                            1 -> source.renameTo(target)
                            2 -> false
                            3 -> source.renameTo(target)
                            else -> false
                        }
                    },
                )
            }

            assertEquals("old", File(linux, "marker").readText())
            assertEquals(
                listOf(
                    "linux->linux.previous",
                    "staging->linux",
                    "linux.previous->linux",
                ),
                moves,
            )
        } finally {
            root.deleteRecursively()
        }
    }

    private fun File.childWithMarker(name: String, marker: String): File =
        File(this, name).apply {
            mkdirs()
            File(this, "marker").writeText(marker)
        }
}

