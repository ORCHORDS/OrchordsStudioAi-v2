package com.orchords.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicReference

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
    fun inheritedPipeThatOutlivesParentProcessIsReportedIncomplete() {
        val python = File("/usr/bin/python3")
        assumeTrue("Inherited-pipe integration fixture requires python3", python.isFile)
        val process = ProcessBuilder(
            python.absolutePath,
            "-c",
            "import os,sys,time; pid=os.fork(); " +
                "(time.sleep(5), os._exit(0)) if pid == 0 else " +
                "(sys.stdout.write('parent'), sys.stdout.flush(), os._exit(0))",
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

    @Test
    fun commandTimeoutDestroysProcessAndReturnsPromptly() {
        assumeTrue("Host shell integration requires a POSIX shell", File("/bin/sh").isFile)
        val process = ProcessBuilder(
            "/bin/sh",
            "-c",
            "printf 'before-timeout'; sleep 30",
        ).start()
        val startedAt = System.nanoTime()

        val result = process.readResult(timeoutMillis = 100)
        val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000

        assertEquals(-1, result.exitCode)
        assertTrue(result.timedOut)
        assertTrue("timeout cleanup took ${elapsedMillis}ms", elapsedMillis < 5_000)
        assertFalse("timed-out process must be destroyed", process.isAlive)
    }

    @Test
    fun interruptedWaitDestroysProcessAndCollectorThreadsDoNotHoldCaller() {
        assumeTrue("Host shell integration requires a POSIX shell", File("/bin/sh").isFile)
        val process = ProcessBuilder(
            "/bin/sh",
            "-c",
            "sleep 30",
        ).start()
        val failure = AtomicReference<Throwable?>()
        val worker = Thread {
            try {
                process.readResult(timeoutMillis = 30_000)
                failure.set(AssertionError("readResult returned normally after caller interruption"))
            } catch (_: InterruptedException) {
                // Expected: readResult cleans up the process/streams and preserves interruption.
            } catch (t: Throwable) {
                failure.set(t)
            }
        }

        worker.start()
        Thread.sleep(100)
        worker.interrupt()
        worker.join(5_000)

        assertFalse("interrupted readResult caller must terminate", worker.isAlive)
        assertFalse("interrupted command process must be destroyed", process.isAlive)
        failure.get()?.let { throw AssertionError("unexpected readResult failure", it) }
    }
}
