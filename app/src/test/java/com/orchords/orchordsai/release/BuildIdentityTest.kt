package com.orchords.orchordsai.release

import org.junit.Assert.assertEquals
import org.junit.Test

class BuildIdentityTest {
    @Test
    fun `display shows installed version build channel and short sha`() {
        val identity = BuildIdentity(
            versionName = "0.1.353",
            versionCode = 1_000_353,
            buildSha = "abcdef1234567890abcdef1234567890abcdef12",
            channel = "daily-debug",
        )

        assertEquals("abcdef12", identity.shortSha)
        assertEquals(
            "0.1.353 (1000353) • daily-debug • abcdef12",
            identity.displayText(),
        )
    }

    @Test
    fun `diagnostics include package full sha and installed version`() {
        val identity = BuildIdentity(
            versionName = "0.1.353",
            versionCode = 1_000_353,
            buildSha = "abcdef1234567890abcdef1234567890abcdef12",
            channel = "daily-release",
        )

        assertEquals(
            "Package: com.orchords.orchordsai\n" +
                "Version: 0.1.353 (1000353)\n" +
                "Channel: daily-release\n" +
                "Source SHA: abcdef1234567890abcdef1234567890abcdef12",
            identity.diagnosticText("com.orchords.orchordsai"),
        )
    }

    @Test
    fun `local build marker never pretends to have a git sha`() {
        val identity = BuildIdentity(
            versionName = "0.1.0",
            versionCode = 1000,
            buildSha = "local",
            channel = "local-debug",
        )

        assertEquals("local", identity.shortSha)
        assertEquals("0.1.0 (1000) • local-debug • local", identity.displayText())
    }
}
