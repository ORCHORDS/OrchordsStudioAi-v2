package com.orchords.orchordsai.data.db

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageNodeIdentityConflictPolicyTest {
    private val root = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .first { File(it, "app/src/main/java").isDirectory }

    private fun source(path: String): String = File(root, path).readText()

    @Test
    fun `message node inserts abort instead of replacing global primary keys`() {
        val dao = source("app/src/main/java/com/orchords/orchordsai/data/db/dao/MessageNodeDAO.kt")

        assertTrue(dao.contains("@Insert(onConflict = OnConflictStrategy.ABORT)\n    suspend fun insertAll"))
        assertTrue(dao.contains("@Insert(onConflict = OnConflictStrategy.ABORT)\n    suspend fun insert(node"))
        assertFalse(dao.contains("@Insert(onConflict = OnConflictStrategy.REPLACE)\n    suspend fun insertAll"))
    }

    @Test
    fun `full conversation rewrite remains one rollback boundary`() {
        val repository = source("app/src/main/java/com/orchords/orchordsai/data/repository/ConversationRepository.kt")
        val update = repository.substringAfter("suspend fun updateConversation(conversation: Conversation)")
            .substringBefore("suspend fun deleteConversation(conversation: Conversation)")

        assertTrue(update.contains("database.withTransaction"))
        assertTrue(update.contains("messageNodeDAO.deleteByConversation"))
        assertTrue(update.contains("saveMessageNodes"))
    }
}
