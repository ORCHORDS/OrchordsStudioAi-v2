package com.orchords.orchordsai.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Keeps first-party ORCHORDS AutoAIIcon surfaces off the retired rabbit asset. */
class OrchordsAutoIconBrandingPolicyTest {
    private fun moduleFile(relative: String): File {
        val moduleDir = File(".").canonicalFile
        require(moduleDir.resolve("src/main").isDirectory)
        return moduleDir.resolve(relative).canonicalFile
    }

    @Test
    fun `ORCHORDS matched icon uses current launcher instead of legacy rabbit asset`() {
        val source = moduleFile("src/main/java/com/orchords/orchordsai/ui/components/ui/AIIcon.kt").readText()
        assertTrue(source.contains("path == LEGACY_ORCHORDS_ICON_ASSET"))
        assertTrue(source.contains("R.mipmap.ic_launcher"))
        assertFalse(
            "Legacy ORCHORDS asset must never be rendered through the generic asset loader",
            source.contains("AIIcon(\n        path = LEGACY_ORCHORDS_ICON_ASSET"),
        )
    }
}
