package com.orchords.orchordsai.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateCheckerReleasePolicyTest {
    @Test
    fun `updater reads the latest release from the current repository`() {
        assertEquals(
            "https://api.github.com/repos/ORCHORDS/OrchordsStudioAi/releases/latest",
            LATEST_RELEASE_API_URL,
        )
    }

    @Test
    fun `rolling latest tag resolves semantic version from release name`() {
        assertEquals(
            "0.1.0",
            resolveReleaseVersion(
                tagName = "latest",
                releaseName = "ORCHORDS Studio AI v0.1.0 — Latest APK",
            ),
        )
    }

    @Test
    fun `semantic release tag remains authoritative when present`() {
        assertEquals(
            "2.3.4",
            resolveReleaseVersion(
                tagName = "v2.3.4",
                releaseName = "ORCHORDS Studio AI v9.9.9 — archived title",
            ),
        )
    }
}
