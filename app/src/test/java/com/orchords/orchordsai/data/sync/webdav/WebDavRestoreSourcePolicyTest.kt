package com.orchords.orchordsai.data.sync.webdav

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavRestoreSourcePolicyTest {
    @Test
    fun `restore separates remote href identity from opaque local staging identity`() {
        val source = File("src/main/java/com/orchords/orchordsai/data/sync/webdav/WebDavSync.kt").readText()
        val restore = source.substringAfter("suspend fun restore(config: WebDavConfig, item: WebDavBackupItem)")
            .substringBefore("suspend fun deleteBackupFile")

        assertTrue(restore.contains("allocateBackupRestoreStagingFile(context.cacheDir)"))
        assertTrue(restore.contains("downloadWebDavResourceToFile(config, httpClient, item.href, backupFile)"))
        assertFalse(restore.contains("resolveBackupCacheFile"))
        assertFalse(restore.contains("client.downloadToFile"))
    }
}
