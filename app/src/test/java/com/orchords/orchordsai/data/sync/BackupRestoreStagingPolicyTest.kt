package com.orchords.orchordsai.data.sync

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BackupRestoreStagingPolicyTest {
    @Test
    fun `restore staging uses unique opaque direct cache children`() {
        val cacheDir = File(System.getProperty("java.io.tmpdir"), "orchords-restore-stage-${System.nanoTime()}")
        cacheDir.mkdirs()
        try {
            val first = allocateBackupRestoreStagingFile(cacheDir)
            val second = allocateBackupRestoreStagingFile(cacheDir)

            assertNotEquals(first.canonicalPath, second.canonicalPath)
            assertEquals(cacheDir.canonicalFile, first.canonicalFile.parentFile)
            assertEquals(cacheDir.canonicalFile, second.canonicalFile.parentFile)
            assert(first.name.startsWith("orchords-restore-"))
            assert(second.name.startsWith("orchords-restore-"))
        } finally {
            cacheDir.deleteRecursively()
        }
    }
}
