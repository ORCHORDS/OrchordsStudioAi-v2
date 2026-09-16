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

    private fun cleanupWorkflowSource(): String {
        val workflow = repoRoot().resolve(".github/workflows/release-cleanup.yml")
        require(workflow.isFile) { "release-cleanup.yml not found from ${File(".").canonicalPath}" }
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
        assertTrue(source.contains("Verify published Release and assets"))
    }

    @Test
    fun `release requires stable play signing and never falls back to ephemeral debug`() {
        val source = workflowSource()

        assertTrue(source.contains("environment: play-internal"))
        assertTrue(source.contains("KEY_BASE64"))
        assertTrue(source.contains("SIGNING_CONFIG"))
        assertTrue(source.contains("Stable signing material is required. Refusing ephemeral debug fallback."))
        assertFalse(source.contains("Signing secrets unavailable; publishing verified debug APKs"))
        assertFalse(source.contains("Publish GitHub Release (debug fallback)"))
        assertFalse(source.contains(":app:assembleDebug"))
    }

    @Test
    fun `release always publishes complete signed artifact matrix and signer evidence`() {
        val source = workflowSource()
        listOf(
            "orchords-studio-ai-universal.apk",
            "orchords-studio-ai-arm64-v8a.apk",
            "orchords-studio-ai-x86_64.apk",
            "orchords-studio-ai.aab",
            "mapping.txt",
            "SHA256SUMS",
            "SIGNING-CERT-SHA256",
        ).forEach { name ->
            assertTrue("Missing release asset contract for $name", source.contains(name))
        }
        assertTrue(source.contains("Expected exactly 3 APK outputs"))
        assertTrue(source.contains("apksigner"))
        assertTrue(source.contains("Signer #1 certificate SHA-256 digest"))
        assertTrue(source.contains("jarsigner -verify"))
        assertTrue(source.contains("sha256sum --check SHA256SUMS"))
    }

    @Test
    fun `canonical app version is v0 1 6 and build validates it`() {
        val workflow = workflowSource()
        val build = appBuildSource()
        val properties = gradleProperties()

        assertTrue(properties.contains("releaseVersionName=0.1.6"))
        assertTrue(properties.contains("releaseVersionCode=1000006"))
        assertTrue(workflow.contains("releaseVersionName"))
        assertTrue(workflow.contains("releaseVersionCode"))
        assertTrue(workflow.contains("2100000000"))
        assertTrue(build.contains("providers.gradleProperty(\"releaseVersionName\")"))
        assertTrue(build.contains("providers.gradleProperty(\"releaseVersionCode\")"))
    }

    @Test
    fun `release verifies package version signer and aab before publishing`() {
        val source = workflowSource()

        assertTrue(source.contains("manifest application-id"))
        assertTrue(source.contains("com.orchords.orchordsai"))
        assertTrue(source.contains("manifest version-name"))
        assertTrue(source.contains("manifest version-code"))
        assertTrue(source.contains("APK versionName mismatch"))
        assertTrue(source.contains("APK versionCode mismatch"))
        assertTrue(source.contains("APK signer mismatch across ABI outputs"))
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
    fun `successful release cleanup removes every older GitHub Release`() {
        val source = cleanupWorkflowSource()
        assertTrue(source.contains("workflow_run"))
        assertTrue(source.contains("workflows: [Release]"))
        assertTrue(source.contains("github.event.workflow_run.conclusion == 'success'"))
        assertTrue(source.contains("releases/latest"))
        assertTrue(source.contains("-X DELETE"))
        assertTrue(source.contains("Expected exactly one GitHub Release"))
    }

    @Test
    fun `release keeps signed abi splits and full build`() {
        val source = workflowSource()
        assertFalse(source.contains("-PciVerify"))
        assertTrue(source.contains(":app:buildAll"))
        assertTrue(source.contains("app-universal-release.apk"))
        assertTrue(source.contains("app-arm64-v8a-release.apk"))
        assertTrue(source.contains("app-x86_64-release.apk"))
    }

    @Test
    fun `release notes carry v0 1 5 history and v0 1 6 fixes`() {
        val source = workflowSource()
        assertTrue(source.contains("## Fixed in v0.1.5"))
        assertTrue(source.contains("## Fixed in v0.1.6"))
        assertTrue(source.contains("Provider stream closed before terminal"))
        assertTrue(source.contains("connection-test button remains visible"))
        assertTrue(source.contains("signer SHA-256 fingerprint"))
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
