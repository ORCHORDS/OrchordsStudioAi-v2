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
    fun `ORCHORDS matched icon uses current launcher instead of legacy assets`() {
        val source = moduleFile("src/main/java/com/orchords/orchordsai/ui/components/ui/AIIcon.kt").readText()
        assertTrue(source.contains("R.mipmap.ic_launcher"))
        assertFalse(
            "Legacy ORCHORDS asset must never be rendered through the generic asset loader",
            source.contains("AIIcon(\n        path = LEGACY_ORCHORDS_ICON_ASSET"),
        )
    }

    @Test
    fun `legacy rabbit drawable is removed from disk`() {
        assertFalse(
            "Legacy rabbit drawable must not ship in res/drawable/. " +
                "The ORCHORDS icon is rendered as R.mipmap.ic_launcher.",
            moduleFile("src/main/res/drawable/rabbit.xml").exists(),
        )
        // Note: assets/emoji/categories.with.modifiers.min.json mentions the
        // literal "rabbit" as an emoji metadata row; it is unrelated to the
        // app launcher drawable and is intentionally not asserted here.
    }

    @Test
    fun `legacy orchordsai svg asset is removed from disk`() {
        assertFalse(
            "Legacy orchordsai.svg must not ship in assets/icons/. The ORCHORDS icon is " +
                "rendered as R.mipmap.ic_launcher via the LEGACY_ORCHORDS_ICON_ASSET short-circuit.",
            moduleFile("src/main/assets/icons/orchordsai.svg").exists(),
        )
        // The matcher still returns the literal "orchordsai.svg" so the redirect in
        // AIIcon.kt can fire, but the asset file on disk has been removed to keep
        // app size and brand surface lean. The matcher URL is a contract for the
        // redirect, not a fetch path.
    }
}

