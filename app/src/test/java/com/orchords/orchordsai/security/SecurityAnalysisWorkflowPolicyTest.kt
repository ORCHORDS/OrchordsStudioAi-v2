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
    fun `osv scanner is installed from the official release tarball`() {
        val source = workflow()
        val installBlock = source.substringAfter("name: Install OSV Scanner")
            .substringBefore("name: Scan dependency manifests and lockfiles")
        assertTrue(
            "OSV install must download the prebuilt Linux tarball, not rely on go",
            installBlock.contains("osv-scanner_Linux_x86_64.tar.gz"),
        )
        assertTrue(
            "OSV install must extract to /tmp and install into the runner temp dir",
            installBlock.contains("tar xz -C /tmp") && installBlock.contains("RUNNER_TEMP/osv-scanner"),
        )
        assertFalse(
            "OSV install must not require the Go toolchain on the runner",
            installBlock.contains("go install"),
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
        listOf("osv", "configuration").forEach { jobName ->
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
}
