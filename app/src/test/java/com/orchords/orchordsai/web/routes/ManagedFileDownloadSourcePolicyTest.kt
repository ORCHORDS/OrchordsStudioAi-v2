package com.orchords.orchordsai.web.routes

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagedFileDownloadSourcePolicyTest {
    private val root = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .first { File(it, "app/src/main/java").isDirectory }

    @Test
    fun `both managed file routes use one inert download policy`() {
        val source = File(
            root,
            "app/src/main/java/com/orchords/orchordsai/web/routes/FilesRoutes.kt",
        ).readText()

        assertEquals(2, Regex("managedFileDownloadHeaders\\(entity\\.displayName\\)").findAll(source).count())
        assertTrue(source.contains("Content-Disposition"))
        assertTrue(source.contains("X-Content-Type-Options"))
    }
}
