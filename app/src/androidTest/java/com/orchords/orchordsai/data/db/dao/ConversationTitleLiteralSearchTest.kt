package com.orchords.orchordsai.data.db.dao

import android.content.Context
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orchords.orchordsai.data.db.AppDatabase
import com.orchords.orchordsai.data.db.entity.ConversationEntity
import com.orchords.orchordsai.data.repository.LightConversationEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConversationTitleLiteralSearchTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: ConversationDAO

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.conversationDao()
        runBlocking {
            listOf(
                conversation("1", ASSISTANT_A, "100% ready", 6),
                conversation("2", ASSISTANT_A, "100x ready", 5),
                conversation("3", ASSISTANT_A, "a_b", 4),
                conversation("4", ASSISTANT_A, "axb", 3),
                conversation("5", ASSISTANT_A, "path\\name", 2),
                conversation("6", ASSISTANT_B, "100% other", 1),
            ).forEach { dao.insert(it) }
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun globalFlowSearchTreatsLikeMetacharactersLiterally() = runBlocking {
        assertEquals(listOf("1", "6"), dao.searchConversations("%").first().map { it.id })
        assertEquals(listOf("3"), dao.searchConversations("_").first().map { it.id })
        assertEquals(listOf("1", "6"), dao.searchConversations("100%").first().map { it.id })
        assertEquals(listOf("5"), dao.searchConversations("\\").first().map { it.id })
    }

    @Test
    fun assistantFlowAndPagingUseTheSameLiteralSemantics() = runBlocking {
        assertEquals(
            listOf("1"),
            dao.searchConversationsOfAssistant(ASSISTANT_A, "100%").first().map { it.id },
        )
        assertEquals(listOf("1", "6"), loadIds(dao.searchConversationsPaging("100%")))
        assertEquals(
            listOf("1"),
            loadIds(dao.searchConversationsOfAssistantPaging(ASSISTANT_A, "100%")),
        )
        assertEquals(listOf("1", "2", "6"), dao.searchConversations("100").first().map { it.id })
    }

    private suspend fun loadIds(source: PagingSource<Int, LightConversationEntity>): List<String> = try {
        when (val result = source.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 20,
                placeholdersEnabled = false,
            )
        )) {
            is PagingSource.LoadResult.Page -> result.data.map { it.id }
            is PagingSource.LoadResult.Error -> throw result.throwable
            is PagingSource.LoadResult.Invalid -> emptyList()
        }
    } finally {
        source.invalidate()
    }

    private fun conversation(
        id: String,
        assistantId: String,
        title: String,
        updateAt: Long,
    ) = ConversationEntity(
        id = id,
        assistantId = assistantId,
        title = title,
        nodes = "[]",
        createAt = updateAt,
        updateAt = updateAt,
        chatSuggestions = "[]",
        isPinned = false,
    )

    private companion object {
        const val ASSISTANT_A = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
        const val ASSISTANT_B = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
    }
}
