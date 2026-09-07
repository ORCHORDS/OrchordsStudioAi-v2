package com.orchords.orchordsai.utils

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Prevents the legacy rabbit status-bar icon from returning after ORCHORDS rebranding. */
class NotificationBrandingPolicyTest {
    private val densities = listOf("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")

    private fun moduleFile(relative: String): File {
        val moduleDir = File(".").canonicalFile
        require(moduleDir.resolve("src/main/res").isDirectory) {
            "Unexpected working directory ${moduleDir.path}: unit tests must run from the app module"
        }
        val file = moduleDir.resolve(relative).canonicalFile
        require(file.toPath().startsWith(moduleDir.toPath())) { "Path escapes app module: $relative" }
        return file
    }

    @Test
    fun `notification icon is the current ORCHORDS monochrome mark at every density`() {
        densities.forEach { density ->
            val notification = moduleFile("src/main/res/drawable-$density/ic_stat_orchordsai.png")
            val monochrome = moduleFile("src/main/res/mipmap-$density/ic_launcher_monochrome.png")
            assertTrue("Missing notification icon for $density", notification.isFile)
            assertTrue("Missing current monochrome launcher mark for $density", monochrome.isFile)
            assertArrayEquals(
                "Notification icon for $density must stay synchronized with the current ORCHORDS monochrome brand mark",
                monochrome.readBytes(),
                notification.readBytes(),
            )
        }
    }
}
