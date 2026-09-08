package com.orchords.orchordsai.ui.pages.backup.tabs

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

/**
 * Pins the fail-closed contract from issue #366: a null SAF destination stream must NOT be
 * reported as success, the source ZIP must be copied byte-for-byte to the destination when
 * delivery does succeed, and any failure path (null sink, mid-write, write-failure on a
 * previously-opened stream, or a zero-byte source edge case) must surface an exception so
 * the caller's `runCatching` block can refuse to advance the `lastBackupTime` timestamp.
 *
 * The contract intentionally does NOT verify what happens to the destination after a partial
 * write — that is the responsibility of the caller choosing an explicit truncation policy
 * ("wt" mode on `ContentResolver.openOutputStream`). See ImportExportTab.
 */
class LocalBackupExporterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun writeSource(content: ByteArray): File {
        val src = tempFolder.newFile("staged.zip")
        src.writeBytes(content)
        return src
    }

    @Test
    fun `successful copy writes the staged bytes to the destination`() {
        val payload = ByteArray(4096) { (it and 0xFF).toByte() }
        val src = writeSource(payload)

        val captured = ByteArrayOutputStream()
        LocalBackupExporter.deliverOrThrow(source = src, openSink = { captured })

        assertArrayEquals(payload, captured.toByteArray())
    }

    @Test
    fun `null sink throws IOException and never reports success`() {
        val src = writeSource(byteArrayOf(1, 2, 3))

        val thrown = assertThrows(IOException::class.java) {
            LocalBackupExporter.deliverOrThrow(source = src, openSink = { null })
        }
        assertEquals(
            "Destination stream unavailable; refusing to claim success",
            thrown.message,
        )
    }

    @Test
    fun `mid-write IOException propagates so callers can refuse success`() {
        val src = writeSource(ByteArray(1024) { 0x55.toByte() })

        assertThrows(IOException::class.java) {
            LocalBackupExporter.deliverOrThrow(source = src, openSink = {
                object : ByteArrayOutputStream() {
                    override fun write(b: ByteArray, off: Int, len: Int) {
                        throw IOException("Disk full")
                    }
                }
            })
        }
    }

    @Test
    fun `write failure on previously-opened stream propagates so callers can refuse success`() {
        // Simulates the destination provider accepting the openOutputStream call but then
        // failing on the very first buffered write. Issue #366 specifically calls out
        // "stream fails after N bytes" as a scenario that must not be reported as success.
        val src = writeSource(ByteArray(1024) { 0x66.toByte() })

        assertThrows(IOException::class.java) {
            LocalBackupExporter.deliverOrThrow(source = src, openSink = {
                object : ByteArrayOutputStream() {
                    override fun write(b: ByteArray, off: Int, len: Int) {
                        throw IOException("Provider disconnected")
                    }
                }
            })
        }
    }

    @Test
    fun `zero-byte source still delivers a zero-byte destination and does not throw`() {
        val src = writeSource(ByteArray(0))
        val captured = ByteArrayOutputStream()

        LocalBackupExporter.deliverOrThrow(source = src, openSink = { captured })

        assertEquals(0, captured.size())
    }

    @Test
    fun `deliverOrThrow invokes openSink exactly once per call`() {
        val src = writeSource(ByteArray(16) { 0x42.toByte() })

        var invocations = 0
        LocalBackupExporter.deliverOrThrow(source = src, openSink = {
            invocations++
            ByteArrayOutputStream()
        })

        assertEquals(1, invocations)
    }
}
