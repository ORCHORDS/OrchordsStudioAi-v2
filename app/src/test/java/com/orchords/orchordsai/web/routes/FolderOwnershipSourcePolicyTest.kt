package com.orchords.orchordsai.web.routes

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderOwnershipSourcePolicyTest {
    private val root = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .first { File(it, "app/src/main/java").isDirectory }

    private fun source(path: String): String = File(root, path).readText()

    @Test
    fun `rename and delete both authorize folder against current assistant`() {
        val routes = source("app/src/main/java/com/orchords/orchordsai/web/routes/FolderRoutes.kt")
        val repository = source("app/src/main/java/com/orchords/orchordsai/data/repository/FolderRepository.kt")

        assertTrue(repository.contains("getFolderByIdForAssistant"))
        assertTrue(repository.contains("folder.assistantId == assistantId"))
        assertEquals(
            2,
            Regex("getFolderByIdForAssistant\\(uuid, settings\\.assistantId\\)").findAll(routes).count(),
        )
    }

    @Test
    fun `delete validates ownership before generation guard and mutation`() {
        val routes = source("app/src/main/java/com/orchords/orchordsai/web/routes/FolderRoutes.kt")
        val deleteBlock = routes.substringAfter("delete(\"/{id}\")").substringBefore("    }\n}")

        val authorization = deleteBlock.indexOf("getFolderByIdForAssistant")
        val generationGuard = deleteBlock.indexOf("hasGeneratingConversationInFolder")
        val mutation = deleteBlock.indexOf("deleteFolder(uuid)")
        assertTrue(authorization >= 0)
        assertTrue(authorization < generationGuard)
        assertTrue(generationGuard < mutation)
    }
}
