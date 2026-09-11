package com.orchords.orchordsai.data.sync.webdav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WebDavResourcePolicyTest {
    private val collection = "https://example.com/dav/backups/"

    @Test
    fun `accepts absolute path and absolute uri inside configured collection`() {
        assertEquals(
            "https://example.com/dav/backups/backup_a.zip",
            resolveWebDavResourceHref(collection, "/dav/backups/backup_a.zip"),
        )
        assertEquals(
            "https://example.com/dav/backups/backup_b.zip",
            resolveWebDavResourceHref(collection, "https://example.com/dav/backups/backup_b.zip"),
        )
    }

    @Test
    fun `rejects hrefs outside exact credential audience and collection`() {
        listOf(
            "https://evil.example/dav/backups/backup.zip",
            "https://example.com:444/dav/backups/backup.zip",
            "/dav/other/backup.zip",
            "/dav/backups/../secret.zip",
            "/dav/backups/%2e%2e/secret.zip",
            "../backup.zip",
            "https://user@example.com/dav/backups/backup.zip",
        ).forEach { href ->
            assertThrows(IllegalArgumentException::class.java) {
                resolveWebDavResourceHref(collection, href)
            }
        }
    }
}
