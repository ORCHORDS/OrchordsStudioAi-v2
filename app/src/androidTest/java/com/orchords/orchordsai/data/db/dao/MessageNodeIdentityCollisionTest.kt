package com.orchords.orchordsai.data.db.dao

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orchords.orchordsai.data.db.AppDatabase
import com.orchords.orchordsai.data.db.entity.ConversationEntity
import com.orchords.orchordsai.data.db.entity.MessageNodeEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MessageNodeIdentityCollisionTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: MessageNodeDAO

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.messageNodeDao()
        runBlocking {
            database.conversationDao().insert(conversation(CONVERSATION_A))
            database.conversationDao().insert(conversation(CONVERSATION_B))
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun crossConversationCollisionAbortsAndPreservesBothOwners() = runBlocking {
        dao.insert(node(SHARED_NODE, CONVERSATION_A, "[\"a\"]"))
        dao.insert(node(B_ORIGINAL_NODE, CONVERSATION_B, "[\"b-old\"]"))

        val failure = runCatching {
            database.withTransaction {
                dao.deleteByConversation(CONVERSATION_B)
                dao.insertAll(
                    listOf(
                        node(B_NEW_NODE, CONVERSATION_B, "[\"b-new\"]"),
                        node(SHARED_NODE, CONVERSATION_B, "[\"collision\"]"),
                    )
                )
            }
        }.exceptionOrNull()

        assertNotNull(failure)
        assertEquals(CONVERSATION_A, dao.getNodeMetaById(SHARED_NODE)?.conversationId)
        assertEquals(CONVERSATION_B, dao.getNodeMetaById(B_ORIGINAL_NODE)?.conversationId)
        assertNull(dao.getNodeMetaById(B_NEW_NODE))
    }

    @Test
    fun sameConversationUpdateRemainsExplicitAndDeterministic() = runBlocking {
        dao.insert(node(SHARED_NODE, CONVERSATION_A, "[\"before\"]"))

        dao.update(node(SHARED_NODE, CONVERSATION_A, "[\"after\"]"))

        val stored = dao.getNodeById(SHARED_NODE)
        assertNotNull(stored)
        assertEquals(CONVERSATION_A, stored?.conversationId)
        assertEquals("[\"after\"]", stored?.messages)
        assertTrue(dao.countNodesOfConversation(CONVERSATION_A) == 1)
    }

    private fun conversation(id: String) = ConversationEntity(
        id = id,
        assistantId = ASSISTANT_ID,
        title = id,
        nodes = "[]",
        createAt = 1L,
        updateAt = 1L,
        chatSuggestions = "[]",
        isPinned = false,
    )

    private fun node(id: String, conversationId: String, messages: String) = MessageNodeEntity(
        id = id,
        conversationId = conversationId,
        nodeIndex = 0,
        messages = messages,
        selectIndex = 0,
    )

    private companion object {
        const val ASSISTANT_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
        const val CONVERSATION_A = "11111111-1111-1111-1111-111111111111"
        const val CONVERSATION_B = "22222222-2222-2222-2222-222222222222"
        const val SHARED_NODE = "99999999-9999-9999-9999-999999999999"
        const val B_ORIGINAL_NODE = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
        const val B_NEW_NODE = "cccccccc-cccc-cccc-cccc-cccccccccccc"
    }
}
