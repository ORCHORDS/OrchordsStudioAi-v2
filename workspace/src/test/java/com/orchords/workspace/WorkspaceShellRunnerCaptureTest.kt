package com.orchords.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class WorkspaceShellRunnerCaptureTest {
    @Test
    fun normalExitDrainsStdoutAndStderrCompletely() {
        assumeTrue("Host shell integration requires a POSIX shell", File("/bin/sh").isFile)
        val process = ProcessBuilder(
            "/bin/sh",
            "-c",
            "printf 'stdout-tail'; printf 'stderr-tail' >&2",
        ).start()

        val result = process.readResult(timeoutMillis = 10_000)

        assertEquals(0, result.exitCode)
        assertEquals("stdout-tail", result.stdout)
        assertEquals("stderr-tail", result.stderr)
        assertFalse(result.timedOut)
        assertFalse(result.truncated)
        assertFalse(result.outputIncomplete)
    }

    @Test
    fun inheritedPipeThatOutlivesShellIsReportedIncomplete() {
        assumeTrue("Host shell integration requires a POSIX shell", File("/bin/sh").isFile)
        val process = ProcessBuilder(
            "/bin/sh",
            "-c",
            "printf 'parent'; (sleep 5) &",
        ).start()

        val result = process.readResult(timeoutMillis = 10_000)

        assertEquals(0, result.exitCode)
        assertEquals("parent", result.stdout)
        assertTrue(result.outputIncomplete)
        // Legacy consumers already reject truncated output; keep them fail-closed until
        // every caller handles outputIncomplete explicitly.
        assertTrue(result.truncated)
    }

    @Test
    fun sizeTruncationIsDistinctFromCaptureIncompleteness() {
        assumeTrue("Host shell integration requires a POSIX shell", File("/bin/sh").isFile)
        val process = ProcessBuilder(
            "/bin/sh",
            "-c",
            "awk 'BEGIN { for (i = 0; i < 300000; i++) printf \"a\" }'",
        ).start()

        val result = process.readResult(timeoutMillis = 10_000)

        assertEquals(0, result.exitCode)
        assertEquals(MAX_OUTPUT_CHARS, result.stdout.length)
        assertTrue(result.truncated)
        assertFalse(result.outputIncomplete)
    }
}
