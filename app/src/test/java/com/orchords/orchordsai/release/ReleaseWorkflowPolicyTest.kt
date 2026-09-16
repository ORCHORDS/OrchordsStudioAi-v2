package com.orchords.orchordsai.release

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReleaseWorkflowPolicyTest {
    private fun repoRoot(): File = File("..").canonicalFile

    private fun workflowSource(): String {
        val workflow = repoRoot().resolve(".github/workflows/release.yml")
        require(workflow.isFile) { "release.yml not found from ${File(".").canonicalPath}" }
        return workflow.readText()
    }

    private fun appBuildSource(): String {
        val buildFile = repoRoot().resolve("app/build.gradle.kts")
        require(buildFile.isFile) { "app/build.gradle.kts not found from ${File(".").canonicalPath}" }
        return buildFile.readText()
    }

    private fun gradleProperties(): String {
        val properties = repoRoot().resolve("gradle.properties")
        require(properties.isFile) { "gradle.properties not found from ${File(".").canonicalPath}" }
        return properties.readText()
    }

    @Test
    fun `release publishes a versioned GitHub latest release`() {
        val source = workflowSource()
        assertTrue(source.contains("tag_name: \${{ steps.version.outputs.tag }}"))
        assertTrue(source.contains("prerelease: false"))
        assertTrue(source.contains("make_latest: true"))
        assertTrue(source.contains("releases/tags/\$RELEASE_TAG"))
        assertTrue(source.contains("Verify latest Release while retaining history"))
    }

    @Test
    fun `release always publishes complete apk matrix and adds aab when signing exists`() {
        val source = workflowSource()
        listOf(
            "orchords-studio-ai-universal.apk",
            "orchords-studio-ai-arm64-v8a.apk",
            "orchords-studio-ai-x86_64.apk",
        ).forEach { name ->
            assertTrue("Missing release asset contract for $name", source.contains(name))
        }
        assertTrue(source.contains("orchords-studio-ai.aab"))
        assertTrue(source.contains("Signing secrets unavailable; publishing verified debug APKs"))
        assertTrue(source.contains("signed=true"))
        assertTrue(source.contains("signed=false"))
        assertTrue(source.contains("Expected exactly 3 APK outputs"))
        assertTrue(source.contains("SHA256SUMS"))
    }

    @Test
    fun `canonical app version lives in gradle properties and is validated by build`() {
        val workflow = workflowSource()
        val build = appBuildSource()
        val properties = gradleProperties()

        assertTrue(properties.contains("releaseVersionName=0.1.4"))
        assertTrue(properties.contains("releaseVersionCode=1000004"))
        assertTrue(workflow.contains("releaseVersionName"))
        assertTrue(workflow.contains("releaseVersionCode"))
        assertTrue(workflow.contains("2100000000"))
        assertTrue(build.contains("providers.gradleProperty(\"releaseVersionName\")"))
        assertTrue(build.contains("providers.gradleProperty(\"releaseVersionCode\")"))
    }

    @Test
    fun `release verifies version embedded in every apk before publishing`() {
        val source = workflowSource()
        assertTrue(source.contains("cmdline-tools/latest/bin/apkanalyzer"))
        assertTrue(source.contains("manifest version-name"))
        assertTrue(source.contains("manifest version-code"))
        assertTrue(source.contains("APK versionName mismatch"))
        assertTrue(source.contains("APK versionCode mismatch"))
        assertTrue(source.contains("manifest application-id"))
        assertTrue(source.contains("sha256sum --check SHA256SUMS"))
        assertTrue(source.contains("asset.get('state') == 'uploaded'"))
    }

    @Test
    fun `release uses current main and refuses version tag reuse for different source`() {
        val source = workflowSource()
        assertTrue(source.contains("ref: main"))
        assertTrue(source.contains("Refuse to reuse a version tag for different source"))
        assertTrue(source.contains("Bump releaseVersionName/releaseVersionCode before publishing new source"))
    }

    @Test
    fun `release retains older releases instead of deleting them`() {
        val source = workflowSource()
        val publish = source.indexOf("Publish GitHub Release")
        val verify = source.indexOf("Verify published Release and assets")
        assertTrue(publish >= 0)
        assertTrue(verify > publish)
        assertFalse(source.contains("Remove every older GitHub Release"))
        assertTrue(source.contains("releases/latest"))
    }

    @Test
    fun `release packaging keeps abi splits enabled`() {
        val source = workflowSource()
        assertFalse(source.contains("-PciVerify"))
        assertTrue(source.contains(":app:buildAll"))
        assertTrue(source.contains(":app:assembleDebug"))
    }

    @Test
    fun `release path does not use unsupported attestations`() {
        val source = workflowSource()
        assertFalse(source.contains("actions/attest@"))
        assertFalse(source.contains("attestations: write"))
        assertTrue(source.contains("SHA-256"))
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
