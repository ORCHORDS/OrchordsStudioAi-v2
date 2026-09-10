package com.orchords.orchordsai.release

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReleaseWorkflowPolicyTest {
    private fun repoRoot(): File = File("..").canonicalFile

    private fun workflowSource(): String {
        val workflow = repoRoot().resolve(".github/workflows/daily-build.yml")
        require(workflow.isFile) { "daily-build.yml not found from ${File(".").canonicalPath}" }
        return workflow.readText()
    }

    private fun appBuildSource(): String {
        val buildFile = repoRoot().resolve("app/build.gradle.kts")
        require(buildFile.isFile) { "app/build.gradle.kts not found from ${File(".").canonicalPath}" }
        return buildFile.readText()
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
    fun `published apk version advances with each new Daily Build run`() {
        val workflow = workflowSource()
        val build = appBuildSource()

        assertTrue(workflow.contains("RUN_NUMBER: \${{ github.run_number }}"))
        assertTrue(workflow.contains("version_name=\"0.1.\${RUN_NUMBER}\""))
        assertTrue(workflow.contains("version_code=\$((1000000 + RUN_NUMBER))"))
        assertTrue(workflow.contains("-PreleaseVersionName=\"\$APP_VERSION_NAME\""))
        assertTrue(workflow.contains("-PreleaseVersionCode=\"\$APP_VERSION_CODE\""))

        assertTrue(build.contains("providers.gradleProperty(\"releaseVersionName\")"))
        assertTrue(build.contains("providers.gradleProperty(\"releaseVersionCode\")"))
        assertTrue(build.contains("versionCode = releaseVersionCode ?: 1000"))
        assertTrue(build.contains("versionName = releaseVersionName ?: \"0.1.0\""))
    }

    @Test
    fun `release verifies version embedded in every apk before publishing`() {
        val source = workflowSource()
        assertTrue(source.contains("cmdline-tools/latest/bin/apkanalyzer"))
        assertTrue(source.contains("manifest version-name"))
        assertTrue(source.contains("manifest version-code"))
        assertTrue(source.contains("APK versionName mismatch"))
        assertTrue(source.contains("APK versionCode mismatch"))
        assertTrue(source.contains("Release notes version mismatch"))
        assertTrue(source.contains("Release notes versionCode mismatch"))
        assertTrue(source.contains("asset.get('state') == 'uploaded'"))
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
