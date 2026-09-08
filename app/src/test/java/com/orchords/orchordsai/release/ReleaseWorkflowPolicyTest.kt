package com.orchords.orchordsai.release

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReleaseWorkflowPolicyTest {
    private fun workflowSource(): String {
        val repoRoot = File("..").canonicalFile
        val workflow = repoRoot.resolve(".github/workflows/daily-build.yml")
        require(workflow.isFile) { "daily-build.yml not found from ${File(".").canonicalPath}" }
        return workflow.readText()
    }

    @Test
    fun `daily build publishes a real GitHub latest release`() {
        val source = workflowSource()
        assertTrue(source.contains("tag_name: latest"))
        assertTrue(source.contains("prerelease: false"))
        assertTrue(source.contains("make_latest: true"))
        assertTrue(source.contains("releases/latest"))
    }

    @Test
    fun `latest release contains the complete configured apk matrix`() {
        val source = workflowSource()
        listOf(
            "orchords-studio-ai-universal.apk",
            "orchords-studio-ai-arm64-v8a.apk",
            "orchords-studio-ai-x86_64.apk",
        ).forEach { name ->
            assertTrue("Missing release asset contract for $name", source.contains(name))
        }
        assertTrue(source.contains("Expected exactly 3 APK outputs"))
        assertTrue(source.contains("SHA256SUMS"))
    }

    @Test
    fun `release packaging keeps abi splits enabled`() {
        val source = workflowSource()
        assertFalse(source.contains("./gradlew -PciVerify"))
        assertTrue(source.contains("\"\$GRADLE_TASK\""))
    }

    @Test
    fun `private repository release path does not use unsupported attestations`() {
        val source = workflowSource()
        assertFalse(source.contains("actions/attest@"))
        assertFalse(source.contains("attestations: write"))
        assertTrue(source.contains("SHA-256 checksums"))
    }

    @Test
    fun `release action stays pinned to maintained v3 implementation`() {
        val source = workflowSource()
        assertTrue(
            source.contains(
                "softprops/action-gh-release@efb35369e0ad2afab669f228072c1b0d510eae64 # v3.0.3"
            )
        )
    }
}
