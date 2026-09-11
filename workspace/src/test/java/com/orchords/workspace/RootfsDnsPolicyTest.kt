package com.orchords.workspace

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootfsDnsPolicyTest {
    @Test
    fun `no approved resolver preserves archive local stub instead of injecting public DNS`() {
        val linuxDir = Files.createTempDirectory("rootfs-dns-stub-").toFile()
        val etc = File(linuxDir, "etc").apply { mkdirs() }
        val resolv = File(etc, "resolv.conf").apply { writeText("nameserver 127.0.0.53\n") }

        RootfsPatcher().patch(linuxDir)

        assertEquals("nameserver 127.0.0.53\n", resolv.readText())
        assertFalse(resolv.readText().contains("1.1.1.1"))
        assertFalse(resolv.readText().contains("8.8.8.8"))
        assertFalse(resolv.readText().contains("223.5.5.5"))
    }

    @Test
    fun `no approved resolver leaves missing resolv conf absent`() {
        val linuxDir = Files.createTempDirectory("rootfs-dns-missing-").toFile()
        File(linuxDir, "etc").mkdirs()

        RootfsPatcher().patch(linuxDir)

        assertFalse(File(linuxDir, "etc/resolv.conf").exists())
    }

    @Test
    fun `explicit approved resolvers replace unusable archive stub`() {
        val linuxDir = Files.createTempDirectory("rootfs-dns-explicit-").toFile()
        val etc = File(linuxDir, "etc").apply { mkdirs() }
        val resolv = File(etc, "resolv.conf").apply { writeText("nameserver 127.0.0.53\n") }

        RootfsPatcher().patch(
            linuxDir,
            RootfsPatchOptions(nameservers = listOf("10.0.0.53", "10.0.0.54")),
        )

        val text = resolv.readText()
        assertTrue(text.contains("nameserver 10.0.0.53"))
        assertTrue(text.contains("nameserver 10.0.0.54"))
        assertFalse(text.contains("127.0.0.53"))
    }

    @Test
    fun `blank and local-only supplied values cannot trigger a resolver rewrite`() {
        val linuxDir = Files.createTempDirectory("rootfs-dns-local-only-").toFile()
        val etc = File(linuxDir, "etc").apply { mkdirs() }
        val resolv = File(etc, "resolv.conf").apply { writeText("nameserver 127.0.0.53\n") }

        RootfsPatcher().patch(
            linuxDir,
            RootfsPatchOptions(nameservers = listOf("", "127.0.0.1", "::1")),
        )

        assertEquals("nameserver 127.0.0.53\n", resolv.readText())
    }
}
