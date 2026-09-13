package com.orchords.orchordsai.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PlayPublishingDocTest {
    private val docPath = "docs/PLAY_PUBLISHING.md"

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
    fun `publishing doc exists and is non-empty`() {
        val file = moduleFile(docPath)
        assertTrue("Missing Play publishing doc at $docPath", file.isFile)
        assertTrue(
            "Play publishing doc must not be empty",
            file.readText().isNotBlank(),
        )
    }

    @Test
    fun `publishing doc names the three Play tracks we use`() {
        val text = source(docPath)
        listOf("internal", "beta", "production").forEach { track ->
            assertTrue(
                "Play publishing doc must mention the '$track' track",
                text.contains(track),
            )
        }
    }

    @Test
    fun `publishing doc references the Fastlane lane contract`() {
        val text = source(docPath)
        // The Fastfile block above is what the lane will actually run. The doc must keep
        // these names so that a future Fastfile update cannot drift without the doc being
        // updated in lock-step.
        listOf(
            "bundle",
            "internal",
            "promote",
            "upload_to_play_store",
        ).forEach { symbol ->
            assertTrue(
                "Play publishing doc must reference fastlane symbol: $symbol",
                text.contains(symbol),
            )
        }
    }

    @Test
    fun `publishing doc lists the gradle bundle task as the source of truth`() {
        val text = source(docPath)
        assertTrue(
            "Publishing doc must call out :app:bundleRelease as the entry point",
            text.contains("bundleRelease"),
        )
        assertTrue(
            "Publishing doc must reference the path where the AAB is written",
            text.contains("app/build/outputs/bundle/release/app-release.aab"),
        )
    }

    @Test
    fun `publishing doc cross-references the other Play readiness docs`() {
        val text = source(docPath)
        listOf(
            "docs/LISTING_ASSETS.md",
            "docs/DATA_SAFETY.md",
            "docs/PERMISSIONS.md",
            "docs/CONTENT_RATING.md",
            "docs/PRIVACY.md",
        ).forEach { ref ->
            assertTrue(
                "Publishing doc must reference $ref so the path is internally consistent",
                text.contains(ref),
            )
        }
    }

    @Test
    fun `publishing doc does not embed raw signing secrets`() {
        val text = source(docPath)
        // The lane references the json_key_file by path; the actual key JSON must NEVER be
        // checked in. These two substrings would indicate a leaked credential.
        assertFalse(
            "Publishing doc must not embed a private_key_id literal",
            Regex("private_key_id\"\\s*:\\s*\"[A-Za-z0-9_-]{20,}\"").containsMatchIn(text),
        )
        assertFalse(
            "Publishing doc must not embed a private_key (BEGIN PRIVATE KEY) block",
            text.contains("BEGIN PRIVATE KEY"),
        )
    }

    @Test
    fun `pre-flight checklist enumerates the six required gates`() {
        val text = source(docPath)
        // Six checkboxes, each tied to a concrete precondition. The phrasing is
        // pinned here so a future edit cannot quietly drop one.
        listOf(
            "docs/LISTING_ASSETS.md",
            "Main Verification",
            "Daily Build",
            "Security Analysis",
            "testDebugUnitTest",
            "releaseVersionName",
            "docs/PRIVACY.md",
        ).forEach { item ->
            assertTrue(
                "Pre-flight checklist must mention: $item",
                text.contains(item),
            )
        }
        // Confirm there are exactly six checkboxes; tightening the list later must
        // intentionally bump this count. Use literal line starts ("\n- [ ]") because the
        // doc is checked into a Windows-canonical repo and the regex form above was
        // surprisingly brittle under kotlinc.
        val checkboxLines = text.split('\n').count { it.startsWith("- [ ]") }
        assertEquals(
            "Pre-flight checklist must have exactly six checkbox items",
            6,
            checkboxLines,
        )
    }
}
