package com.orchords.orchordsai.utils

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Keeps first-party ORCHORDS icon surfaces on the current launcher artwork. */
class OrchordsAutoIconBrandingPolicyTest {
    private fun moduleFile(relative: String): File {
        val moduleDir = File(".").canonicalFile
        require(moduleDir.resolve("src/main").isDirectory)
        return moduleDir.resolve(relative).canonicalFile
    }

    @Test
    fun `ORCHORDS matched icon uses current launcher on Android`() {
        val source = moduleFile("src/main/java/com/orchords/orchordsai/ui/components/ui/AIIcon.kt").readText()
        assertTrue(source.contains("R.mipmap.ic_launcher"))
        assertTrue(source.contains("ORCHORDS_ICON_ASSET = \"orchordsai.png\""))
    }

    @Test
    fun `oai model id resolves to the Orchords icon asset`() {
        assertEquals("orchordsai.png", computeAIIconByName("oai-1.0"))
        assertEquals("orchordsai.png", computeAIIconByName("OrchordsAI"))
    }

    @Test
    fun `web Orchords icon asset is exact launcher source`() {
        val launcher = moduleFile("src/main/res/mipmap-xxxhdpi/ic_launcher.png")
        val webAsset = moduleFile("src/main/assets/icons/orchordsai.png")
        assertTrue("Orchords web icon asset must exist", webAsset.isFile)
        assertArrayEquals(launcher.readBytes(), webAsset.readBytes())
    }

    @Test
    fun `legacy rabbit drawable is removed from disk`() {
        assertFalse(
            "Legacy rabbit drawable must not ship in res/drawable/.",
            moduleFile("src/main/res/drawable/rabbit.xml").exists(),
        )
    }

    @Test
    fun `legacy orchordsai svg remains removed`() {
        assertFalse(
            "Legacy orchordsai.svg must not ship in assets/icons/.",
            moduleFile("src/main/assets/icons/orchordsai.svg").exists(),
        )
    }
}
