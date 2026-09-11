package com.orchords.orchordsai.data.provider

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceDocumentsProviderAuthorityPolicyTest {
    private val root = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .first { File(it, "app/src/main/java").isDirectory }

    private fun source(path: String): String = File(root, path).readText()

    @Test
    fun `provider validates workspace roots before subtree operations`() {
        val dao = source("app/src/main/java/com/orchords/orchordsai/data/db/dao/WorkspaceDAO.kt")
        val provider = source("app/src/main/java/com/orchords/orchordsai/data/provider/WorkspaceDocumentsProvider.kt")

        assertTrue(dao.contains("suspend fun getByRoot(root: String): WorkspaceEntity?"))
        assertTrue(provider.contains("requireAuthoritativeWorkspace(parseDocId(documentId))"))
        assertTrue(provider.contains("requireAuthoritativeWorkspace(parseDocId(parentDocumentId))"))
        assertTrue(provider.contains("workspaceByRoot(child.root) == null"))
        assertFalse(provider.contains("manager().ensureWorkspace(parent.root)"))
    }

    @Test
    fun `file resolution does not create workspace roots as an authorization side effect`() {
        val provider = source("app/src/main/java/com/orchords/orchordsai/data/provider/WorkspaceDocumentsProvider.kt")
        val resolver = provider.substringAfter("private fun resolveFile").substringBefore("private fun parseDocId")

        assertFalse(resolver.contains("mkdirs()"))
    }
}
