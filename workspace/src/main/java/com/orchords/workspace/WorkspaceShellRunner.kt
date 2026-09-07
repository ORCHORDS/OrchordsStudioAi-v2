package com.orchords.workspace

import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

interface WorkspaceShellRunner {
    fun execute(context: WorkspaceShellContext): WorkspaceCommandResult
}

data class WorkspaceShellContext(
    val root: String,
    val command: String,
    val cwd: String,
    val filesDir: File,
    val linuxDir: File,
    val tempDir: File,
    val workingDir: File,
    val timeoutMillis: Long,
    val stdin: ByteArray? = null,
    val bindMounts: List<WorkspaceBindMount> = emptyList(),
)

class HostShellRunner : WorkspaceShellRunner {
    override fun execute(context: WorkspaceShellContext): WorkspaceCommandResult {
        val process = ProcessBuilder(defaultShell(), "-c", context.command)
            .directory(context.workingDir)
            .redirectErrorStream(false)
            .start()
        return process.readResult(context.timeoutMillis, context.stdin)
    }

    private fun defaultShell(): String =
        if (File("/system/bin/sh").exists()) "/system/bin/sh" else "/bin/sh"
}

const val MAX_OUTPUT_CHARS = 128 * 1024
private const val OUTPUT_DRAIN_TIMEOUT_MS = 1_000L
private const val FORCED_CLOSE_JOIN_MS = 1_000L

fun Process.readResult(timeoutMillis: Long, stdin: ByteArray? = null): WorkspaceCommandResult {
    val stdoutStream = inputStream
    val stderrStream = errorStream
    val stdout = StreamCollector(stdoutStream)
    val stderr = StreamCollector(stderrStream)
    val stdinWriter = stdin?.let { bytes -> StreamWriter(outputStream, bytes) }
    if (stdinWriter == null) {
        outputStream.closeQuietly()
    }
    try {
        val finished = waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
        if (!finished) {
            destroyForcibly()
        }
        stdinWriter?.join(OUTPUT_DRAIN_TIMEOUT_MS)

        val stdoutDrained = stdout.awaitTermination(OUTPUT_DRAIN_TIMEOUT_MS)
        val stderrDrained = stderr.awaitTermination(OUTPUT_DRAIN_TIMEOUT_MS)
        val forcedCaptureClose = !stdoutDrained || !stderrDrained
        if (forcedCaptureClose) {
            stdoutStream.closeQuietly()
            stderrStream.closeQuietly()
            stdout.join(FORCED_CLOSE_JOIN_MS)
            stderr.join(FORCED_CLOSE_JOIN_MS)
        }

        val outputIncomplete = forcedCaptureClose ||
            !stdout.completedNormally ||
            !stderr.completedNormally
        return WorkspaceCommandResult(
            exitCode = if (finished) exitValue() else -1,
            stdout = stdout.text(),
            stderr = stderr.text(),
            timedOut = !finished,
            // Keep legacy callers fail-closed: they already reject truncated output.
            // outputIncomplete distinguishes capture failure from a size-bound truncation.
            truncated = stdout.truncated || stderr.truncated || outputIncomplete,
            outputIncomplete = outputIncomplete,
        )
    } catch (e: InterruptedException) {
        destroyForcibly()
        outputStream.closeQuietly()
        stdoutStream.closeQuietly()
        stderrStream.closeQuietly()
        stdinWriter?.join(FORCED_CLOSE_JOIN_MS)
        stdout.join(FORCED_CLOSE_JOIN_MS)
        stderr.join(FORCED_CLOSE_JOIN_MS)
        throw e
    }
}

private fun Closeable.closeQuietly() {
    try {
        close()
    } catch (_: IOException) {
    }
}

private class StreamWriter(
    private val stream: java.io.OutputStream,
    private val bytes: ByteArray,
) {
    private val thread = Thread {
        try {
            stream.use { output ->
                output.write(bytes)
                output.flush()
            }
        } catch (_: IOException) {
        }
    }.apply {
        isDaemon = true
        start()
    }

    fun join(millis: Long) = thread.join(millis)
}

private class StreamCollector(
    stream: InputStream,
    private val maxChars: Int = MAX_OUTPUT_CHARS,
) {
    private val builder = StringBuilder()

    @Volatile
    var truncated = false
        private set

    @Volatile
    var completedNormally = false
        private set

    private val thread = Thread {
        try {
            stream.bufferedReader().use { reader ->
                val buffer = CharArray(4096)
                while (true) {
                    val read = reader.read(buffer)
                    if (read < 0) {
                        completedNormally = true
                        break
                    }
                    synchronized(builder) {
                        val remaining = maxChars - builder.length
                        if (remaining > 0) {
                            builder.append(buffer, 0, minOf(read, remaining))
                        }
                        if (read > remaining) {
                            truncated = true
                        }
                    }
                }
            }
        } catch (_: IOException) {
            // A read failure is intentionally represented by completedNormally=false.
        }
    }.apply {
        isDaemon = true
        start()
    }

    fun awaitTermination(millis: Long): Boolean {
        thread.join(millis)
        return !thread.isAlive
    }

    fun join(millis: Long) = thread.join(millis)

    fun text(): String = synchronized(builder) { builder.toString() }
}
