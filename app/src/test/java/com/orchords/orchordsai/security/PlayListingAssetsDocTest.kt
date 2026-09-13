package com.orchords.orchordsai.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PlayListingAssetsDocTest {
    private val docPath = "docs/LISTING_ASSETS.md"

    private fun repoRoot(): File {
        var dir: File? = File(".").canonicalFile
        while (dir != null) {
            if (dir.resolve("settings.gradle.kts").isFile) return dir
            dir = dir.parentFile
        }
        error("Could not locate repo root from ${File(".").canonicalPath}")
    }

    private fun moduleFile(relative: String): File {
        val root = repoRoot()
        val file = root.resolve(relative).canonicalFile
        require(file.toPath().startsWith(root.toPath())) {
            "Path escapes repo root: $relative"
        }
        return file
    }

    private fun source(relative: String): String = moduleFile(relative).readText()

    @Test
    fun `listing assets doc exists and is non-empty`() {
        val file = moduleFile(docPath)
        assertTrue("Missing Play listing-asset checklist at $docPath", file.isFile)
        assertTrue(
            "Play listing-asset checklist must not be empty",
            file.readText().isNotBlank(),
        )
    }

    @Test
    fun `listing assets doc names every Play Console asset class`() {
        val text = source(docPath)
        // Each heading from §1–§7 of LISTING_ASSETS.md must be present so that future
        // re-organisations cannot accidentally drop a required Play Console slot.
        listOf(
            "## 1. App icon",
            "## 2. Feature graphic",
            "## 3. Phone screenshots",
            "## 4. Tablet screenshots",
            "## 5. Promo video",
            "## 6. Short description",
            "## 7. Localization",
        ).forEach { heading ->
            assertTrue(
                "Listing-asset checklist is missing required section: $heading",
                text.contains(heading),
            )
        }
    }

    @Test
    fun `listing assets doc references the repository source of truth`() {
        val text = source(docPath)
        // The doc must point at the in-repo resources the listing pulls from.
        listOf(
            "app/src/main/AndroidManifest.xml",
            "app/src/main/res/mipmap",
            "app/build.gradle.kts",
        ).forEach { ref ->
            assertTrue(
                "Listing-asset checklist must reference $ref",
                text.contains(ref),
            )
        }
    }

    @Test
    fun `launcher icon assets are checked into all density buckets`() {
        // Launcher icons live under app/src/main/res/mipmap-*. The test resolves from the
        // repo root so it works whether Gradle invokes the JVM with cwd = app/ or cwd = root.
        val root = repoRoot()
        listOf("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi").forEach { bucket ->
            val launcher = root.resolve("app/src/main/res/mipmap-$bucket/ic_launcher.png")
            assertTrue(
                "Missing launcher icon for density bucket $bucket",
                launcher.isFile,
            )
        }
        val adaptive = root.resolve("app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml")
        assertNotNull(
            "Adaptive launcher XML must be present at mipmap-anydpi-v26/ic_launcher.xml",
            adaptive,
        )
        assertTrue(
            "Adaptive launcher XML must exist at mipmap-anydpi-v26/ic_launcher.xml",
            adaptive.isFile,
        )
    }

    @Test
    fun `listing assets doc names a 512x512 hi-res icon as TODO`() {
        // Play Console refuses to accept the in-APK icon alone; the 512×512 hi-res
        // icon must be uploaded manually. The doc must call this out explicitly.
        val text = source(docPath)
        assertTrue(
            "Listing-asset checklist must call out the 512x512 hi-res icon TODO",
            text.contains("512×512") || text.contains("512x512"),
        )
    }

    @Test
    fun `listing assets doc names a 1024x500 feature graphic as TODO`() {
        val text = source(docPath)
        assertTrue(
            "Listing-asset checklist must call out the 1024x500 feature graphic TODO",
            text.contains("1024×500") || text.contains("1024x500"),
        )
    }

    @Test
    fun `pre-submission verification lists the CI gate commands`() {
        val text = source(docPath)
        val expected = listOf(
            "testDebugUnitTest",
            "lintDebug",
            "assembleRelease",
        )
        expected.forEach { cmd ->
            assertEquals(
                "Pre-submission verification must mention :app:$cmd exactly once",
                1,
                Regex(":$cmd").findAll(text).count(),
            )
        }
    }
}
