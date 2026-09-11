package com.orchords.orchordsai.data.sync

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the transport call sites; SettingsBackupProjectionTest covers the codec itself. */
class SettingsBackupTransportPolicyTest {
    private val root = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .first { File(it, "app/src/main/java").isDirectory }

    private fun source(path: String): String = File(
        root,
        "app/src/main/java/com/orchords/orchordsai/$path",
    ).readText()

    private val transports get() = listOf(
        source("data/sync/S3Sync.kt"),
        source("data/sync/webdav/WebDavSync.kt"),
    )

    @Test
    fun `both archive writers use the shared portable settings projection`() {
        transports.forEach { source ->
            assertTrue(source.contains("content = encodePortableSettingsBackup(settingsStore.settingsFlow.value, json)"))
            assertFalse(source.contains("json.encodeToString(settingsStore.settingsFlow.value)"))
        }
    }

    @Test
    fun `both restore paths sanitize settings before prompt validation and persistence`() {
        transports.forEach { source ->
            val restore = source.substringAfter("\"settings.json\" -> {")
                .substringBefore("DATABASE_BACKUP_ENTRY ->")
            val decode = restore.indexOf("decodePortableSettingsBackup(settingsJson, json)")
            val validateAndPersist = restore.indexOf("settingsStore.update(validateSettingsPromptContent(settings))")
            assertTrue(decode >= 0 && validateAndPersist > decode)
            assertFalse(restore.contains("json.decodeFromString<Settings>"))
            assertFalse(restore.contains("SettingsJsonMigrator.migrate"))
        }
    }

    @Test
    fun `settings restore failures never expose raw parser exception messages or causes`() {
        transports.forEach { source ->
            val restore = source.substringAfter("\"settings.json\" -> {")
                .substringBefore("DATABASE_BACKUP_ENTRY ->")
            assertTrue(restore.contains("throw IllegalArgumentException(\"Invalid backup settings\")"))
            assertFalse(restore.contains("e.message"))
            assertFalse(restore.contains("\", e)"))
        }
    }

    @Test
    fun `settings restore preserves coroutine cancellation`() {
        transports.forEach { source ->
            val restore = source.substringAfter("\"settings.json\" -> {")
                .substringBefore("DATABASE_BACKUP_ENTRY ->")
            assertTrue(restore.contains("if (e is CancellationException) throw e"))
        }
        val local = source("data/sync/webdav/WebDavSync.kt")
            .substringAfter("suspend fun restoreFromLocalFile(")
            .substringBefore("suspend fun prepareBackupFile(")
        assertTrue(local.contains("if (e is CancellationException) throw e"))
    }

    @Test
    fun `projection failure still deletes failed staging and propagates failure`() {
        transports.forEach { source ->
            val prepare = source.substringAfter("suspend fun prepareBackupFile(")
                .substringBefore("private suspend fun restoreFromBackupFile(")
                .replace(Regex("\\s+"), " ")
            assertTrue(prepare.contains("catch (error: Throwable) { backupFile.delete() throw error }"))
        }
    }

    @Test
    fun `webdav imports the canonical codec rather than defining a parallel sanitizer`() {
        val source = source("data/sync/webdav/WebDavSync.kt")
        assertTrue(source.contains("import com.orchords.orchordsai.data.sync.encodePortableSettingsBackup"))
        assertTrue(source.contains("import com.orchords.orchordsai.data.sync.decodePortableSettingsBackup"))
        assertFalse(source.contains("fun encodePortableSettingsBackup("))
        assertFalse(source.contains("fun decodePortableSettingsBackup("))
    }
}
