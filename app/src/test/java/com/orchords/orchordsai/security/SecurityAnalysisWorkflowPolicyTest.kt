package com.orchords.orchordsai.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pins the install method for the Security Analysis scanners so the
 * workflow does not regress to a Go toolchain dependency that the
 * GitHub-hosted runners do not provide.
 */
class SecurityAnalysisWorkflowPolicyTest {
    private fun repoRoot(): File = File("..").canonicalFile

    private fun workflow(): String =
        repoRoot().resolve(".github/workflows/security-analysis.yml").readText()

    @Test
    fun `osv scanner is installed from the official release binary`() {
        val source = workflow()
        val installBlock = source.substringAfter("name: Install OSV Scanner")
            .substringBefore("name: Scan dependency manifests and lockfiles")
        assertTrue(
            "OSV install must download the prebuilt Linux amd64 binary, not rely on go",
            installBlock.contains("osv-scanner_linux_amd64"),
        )
        assertTrue(
            "OSV install must place the binary in RUNNER_TEMP and mark it executable",
            installBlock.contains("RUNNER_TEMP/osv-scanner") &&
                installBlock.contains("install -m 0755"),
        )
        assertFalse(
            "OSV install must not require the Go toolchain on the runner",
            installBlock.contains("go install"),
        )
        assertFalse(
            "OSV v2.x publishes a raw binary, not a tarball; tar extraction must not be used",
            installBlock.contains("tar xz"),
        )
    }

    @Test
    fun `trivy is installed from the official release tarball`() {
        val source = workflow()
        val installBlock = source.substringAfter("name: Install Trivy")
            .substringBefore("name: Scan")
        assertTrue(
            "Trivy install must download the prebuilt Linux tarball",
            installBlock.contains("trivy_\${TRIVY_VERSION}_Linux-64bit.tar.gz") ||
                installBlock.contains("trivy_${'$'}{TRIVY_VERSION}_Linux-64bit.tar.gz"),
        )
    }

    @Test
    fun `each scanner step gates on schedule or manual dispatch`() {
        // Pull each job's own block. Top-level YAML job keys are indented by
        // exactly two spaces and end with `:`. Anything indented further is a
        // key under that job, so the next `\n  <word>:` (no extra space)
        // marks the boundary to the next top-level job.
        val source = workflow()
        val jobRegex = Regex("\n {2}([a-z][a-z0-9_-]*):")
        val matches = jobRegex.findAll(source).toList()
        listOf("osv", "configuration", "aab-artifact").forEach { jobName ->
            val jobMatch = matches.firstOrNull { it.groupValues[1] == jobName }
                ?: error("Could not locate job '$jobName' in workflow")
            val nextMatch = matches.firstOrNull { it.range.first > jobMatch.range.first }
            val jobBlock = if (nextMatch == null) source.substring(jobMatch.range.first)
            else source.substring(jobMatch.range.first, nextMatch.range.first)
            assertTrue(
                "$jobName must guard on schedule or workflow_dispatch",
                jobBlock.contains("github.event_name == 'schedule'") &&
                    jobBlock.contains("github.event_name == 'workflow_dispatch'"),
            )
        }
    }

    @Test
    fun `aab-artifact job builds inspects and scans the release bundle`() {
        val source = workflow()
        // Locate the aab-artifact job block.
        val jobRegex = Regex("\n {2}([a-z][a-z0-9_-]*):")
        val matches = jobRegex.findAll(source).toList()
        val jobMatch = matches.firstOrNull { it.groupValues[1] == "aab-artifact" }
            ?: error("Could not locate aab-artifact job in workflow")
        val nextMatch = matches.firstOrNull { it.range.first > jobMatch.range.first }
        val jobBlock = if (nextMatch == null) source.substring(jobMatch.range.first)
        else source.substring(jobMatch.range.first, nextMatch.range.first)

        assertTrue(
            "aab-artifact must run on the standard GitHub-hosted Ubuntu runner",
            jobBlock.contains("runs-on: ubuntu-24.04"),
        )
        assertTrue(
            "aab-artifact must build the release bundle",
            jobBlock.contains(":app:bundleRelease"),
        )
        assertTrue(
            "aab-artifact must inspect package via apkanalyzer",
            jobBlock.contains("manifest application-id"),
        )
        assertTrue(
            "aab-artifact must inspect version via apkanalyzer",
            jobBlock.contains("manifest version-name") &&
                jobBlock.contains("manifest version-code"),
        )
        assertTrue(
            "aab-artifact must enumerate APK contents via apkanalyzer",
            jobBlock.contains("files list"),
        )
        assertTrue(
            "aab-artifact must generate a SHA-256 checksum of the AAB",
            jobBlock.contains("sha256sum"),
        )
        assertTrue(
            "aab-artifact must install Trivy from the official tarball",
            jobBlock.contains("trivy_\${TRIVY_VERSION}_Linux-64bit.tar.gz") ||
                jobBlock.contains("trivy_${'$'}{TRIVY_VERSION}_Linux-64bit.tar.gz"),
        )
        assertTrue(
            "aab-artifact must scan the extracted AAB with Trivy",
            jobBlock.contains("AAB_INSPECT_DIR"),
        )
        assertTrue(
            "aab-artifact must remove signing material after build",
            jobBlock.contains("Remove signing files"),
        )
    }
}
