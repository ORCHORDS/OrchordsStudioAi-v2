package com.orchords.orchordsai.data.provider

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceDocumentsProviderNotificationPolicyTest {
    private val root = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .first { File(it, "app/src/main/java").isDirectory }

    private fun providerSource(): String = root.resolve(
        "app/src/main/java/com/orchords/orchordsai/data/provider/WorkspaceDocumentsProvider.kt"
    ).readText()

    @Test
    fun `child queries subscribe to the same URI used by mutation notifications`() {
        val source = providerSource()

        assertTrue(source.contains("registerNotificationUri(cursor, parentDocumentId)"))
        assertTrue(source.contains("cursor.setNotificationUri("))
        assertTrue(source.contains("childDocumentsUri(ctx.packageName, parentDocumentId)"))
        assertTrue(source.contains("DocumentsContract.buildChildDocumentsUri("))
        assertTrue(source.contains("ctx.contentResolver.notifyChange(\n            childDocumentsUri(ctx.packageName, parentDocumentId)"))
    }

    @Test
    fun `moves continue notifying both source and destination parent listings`() {
        val source = providerSource()
        val moveBody = source.substringAfter("override fun moveDocument(").substringBefore("override fun getDocumentType(")

        assertTrue(moveBody.contains("notifyChange(buildDocId(source.root, source.relPath.substringBeforeLast('/', \"\")))"))
        assertTrue(moveBody.contains("notifyChange(targetParentDocumentId)"))
    }
}
