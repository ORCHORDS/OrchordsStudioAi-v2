package com.orchords.orchordsai.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pins the deny-by-default allowlist policy enforced by
 * `app/src/main/res/xml/backup_rules.xml` (Android 11 and lower Auto
 * Backup) and `app/src/main/res/xml/data_extraction_rules.xml`
 * (Android 12+ cloud backup + device transfer).
 *
 * The policy is allowlist-first: Android defaults to backing up
 * everything in sharedpref/file/database/external that is *not*
 * excluded; we invert that by declaring only the portable
 * preferences namespace as `<include>`. Anything that does not
 * appear in a `<include>` rule is excluded by definition — including
 * the Room database carrying conversation content, encryption keys,
 * file-backed caches, and credential stores.
 */
class AndroidBackupPolicyTest {
    private val sensitiveDomains = listOf("root", "file", "database", "external")

    /**
     * Portable preferences that may ride along with Android OS backup /
     * device transfer. The only allowed SharedPreferences namespace is
     * `orchordsai.preferences` (UI toggles, theme, layout). Provider
     * credentials and the V6 `orchordsai_secondary_secrets` encrypted
     * payload file intentionally stay outside this allowlist.
     */
    private val portableSharedPrefs = setOf("orchordsai.preferences.xml")

    private fun moduleFile(relative: String): File {
        val moduleDir = File(".").canonicalFile
        require(moduleDir.resolve("src/main/res").isDirectory) {
            "Unexpected working directory ${moduleDir.path}: unit tests must run from the app module"
        }
        val file = moduleDir.resolve(relative).canonicalFile
        require(file.toPath().startsWith(moduleDir.toPath())) { "Path escapes app module: $relative" }
        return file
    }

    private fun source(relative: String): String = moduleFile(relative).readText()

    private fun section(xml: String, name: String): String {
        return Regex("<$name[^>]*>[\\s\\S]*?</$name>")
            .find(xml)?.value ?: error("Missing <$name> in $xml")
    }

    private fun includePaths(xml: String, domain: String): List<String> {
        return Regex("<include\\s+domain=\"$domain\"\\s+path=\"([^\"]+)\"\\s*/>")
            .findAll(xml)
            .map { it.groupValues[1] }
            .toList()
    }

    private fun includePathsInSection(xml: String, sectionName: String, domain: String): List<String> {
        return Regex("<include\\s+domain=\"$domain\"\\s+path=\"([^\"]+)\"\\s*/>")
            .findAll(section(xml, sectionName))
            .map { it.groupValues[1] }
            .toList()
    }

    private fun assertSensitiveDomainsAreAbsent(xml: String) {
        sensitiveDomains.forEach { domain ->
            assertTrue(
                "<include> rules targeting domain=\"$domain\" must stay absent",
                includePaths(xml, domain).isEmpty(),
            )
        }
    }

    @Test
    fun `android 12 plus cloud and device transfer follow deny-by-default allowlist`() {
        val xml = source("src/main/res/xml/data_extraction_rules.xml")

        assertSensitiveDomainsAreAbsent(xml)
        assertEquals(
            "cloud-backup allowlist drifted from the documented portable set",
            portableSharedPrefs.sorted(),
            includePathsInSection(xml, "cloud-backup", "sharedpref").sorted(),
        )
        assertEquals(
            "device-transfer allowlist drifted from the documented portable set",
            portableSharedPrefs.sorted(),
            includePathsInSection(xml, "device-transfer", "sharedpref").sorted(),
        )
    }

    @Test
    fun `legacy auto backup follows the same allowlist policy`() {
        val xml = source("src/main/res/xml/backup_rules.xml")

        assertSensitiveDomainsAreAbsent(xml)
        assertEquals(
            "Legacy Auto Backup allowlist drifted from the documented portable set",
            portableSharedPrefs.sorted(),
            includePaths(xml, "sharedpref").sorted(),
        )
    }

    @Test
    fun `database and credential-bearing domains never appear in any allowlist`() {
        val dataExtraction = source("src/main/res/xml/data_extraction_rules.xml")
        val legacy = source("src/main/res/xml/backup_rules.xml")
        listOf(dataExtraction, legacy).forEach { xml ->
            assertSensitiveDomainsAreAbsent(xml)
            assertTrue("secondary secret store must stay out of backup allowlist", "orchordsai_secondary_secrets" !in xml.substringAfter("<data-extraction-rules>", xml))
        }
    }

    @Test
    fun `manifest keeps both versioned policy resources explicit`() {
        val manifest = source("src/main/AndroidManifest.xml")

        assertTrue(manifest.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))
        assertTrue(manifest.contains("android:fullBackupContent=\"@xml/backup_rules\""))
    }
}
