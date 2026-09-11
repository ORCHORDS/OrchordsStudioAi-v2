package com.orchords.orchordsai.data.db

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationTitleSearchPolicyTest {
    private val root = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .first { File(it, "app/src/main/java").isDirectory }

    @Test
    fun `all title LIKE queries declare literal escape semantics`() {
        val dao = root.resolve(
            "app/src/main/java/com/orchords/orchordsai/data/db/dao/ConversationDAO.kt"
        ).readText()

        val titleLikes = Regex("title LIKE").findAll(dao).count()
        val escapes = Regex("ESCAPE").findAll(dao).count()
        assertEquals(4, titleLikes)
        assertEquals(titleLikes, escapes)
        assertTrue(dao.contains("replace(replace(replace(:searchText"))
        assertFalse(dao.contains("title LIKE '%' || :searchText || '%'"))
    }
}
