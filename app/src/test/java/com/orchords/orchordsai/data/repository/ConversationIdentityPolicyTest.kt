package com.orchords.orchordsai.data.repository

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.orchords.orchordsai.data.db.dao.MessageNodeDAO
import com.orchords.orchordsai.data.db.dao.MessageTokenStats
import com.orchords.orchordsai.data.db.dao.MessageDayCount
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationIdentityPolicyTest {

    /**
     * Minimal stub that supports only the lookup paths exercised by
     * [ConversationIdentityPolicy]. The full DAO surface is large; tests for
     * unrelated code paths construct their own fakes.
     */
    private class StubMessageNodeDAO(
        private val owners: Map<String, String>,
    ) : MessageNodeDAO {
        override suspend fun getNodesOfConversation(conversationId: String) = throw UnsupportedOperationException()
        override suspend fun getNodesMetaOfConversation(conversationId: String) = throw UnsupportedOperationException()
        override suspend fun getNodesOfConversationPaged(conversationId: String, limit: Int, offset: Int) =
            throw UnsupportedOperationException()
        override suspend fun getNodesMetaOfConversationPaged(conversationId: String, limit: Int, offset: Int) =
            throw UnsupportedOperationException()
        override suspend fun countNodesOfConversation(conversationId: String) = throw UnsupportedOperationException()
        override suspend fun getNodeIdAtOffset(conversationId: String, offset: Int) =
            throw UnsupportedOperationException()
        override suspend fun getNodeById(nodeId: String) = throw UnsupportedOperationException()
        override suspend fun getNodeMetaById(nodeId: String) = throw UnsupportedOperationException()
        override suspend fun getInlineMessagesUtf8ByteLength(nodeId: String): Long? =
            throw UnsupportedOperationException()
        override suspend fun getInlineMessagesChunk(nodeId: String, startCharOneBased: Int, maxChars: Int) =
            throw UnsupportedOperationException()
        override suspend fun insertAll(nodes: List<com.orchords.orchordsai.data.db.entity.MessageNodeEntity>) =
            throw UnsupportedOperationException()
        override suspend fun insert(node: com.orchords.orchordsai.data.db.entity.MessageNodeEntity) =
            throw UnsupportedOperationException()
        override suspend fun update(node: com.orchords.orchordsai.data.db.entity.MessageNodeEntity) =
            throw UnsupportedOperationException()
        override suspend fun deleteByConversation(conversationId: String) = throw UnsupportedOperationException()
        override suspend fun deleteById(nodeId: String) = throw UnsupportedOperationException()
        override suspend fun getInvalidMessageJsonCount(): Int = throw UnsupportedOperationException()
        override suspend fun getTokenStatsRaw(query: SupportSQLiteQuery): MessageTokenStats =
            throw UnsupportedOperationException()
        override suspend fun getMessageCountPerDayRaw(query: SupportSQLiteQuery): List<MessageDayCount> =
            throw UnsupportedOperationException()

        override suspend fun getOwnersByIds(nodeIds: List<String>): List<MessageNodeDAO.NodeOwnerRow> {
            return nodeIds.mapNotNull { id ->
                val owner = owners[id] ?: return@mapNotNull null
                MessageNodeDAO.NodeOwnerRow(id = id, conversationId = owner)
            }
        }
    }

    @Test
    fun `empty node list never collides`() {
        runBlocking {
            ConversationIdentityPolicy.assertNoCrossConversationCollision(
                messageNodeDAO = StubMessageNodeDAO(emptyMap()),
                targetConversationId = "conv-A",
                nodeIds = emptyList(),
            )
        }
    }

    @Test
    fun `same-owner node ids pass validation`() {
        runBlocking {
            val dao = StubMessageNodeDAO(
                mapOf(
                    "node-1" to "conv-A",
                    "node-2" to "conv-A",
                )
            )
            ConversationIdentityPolicy.assertNoCrossConversationCollision(
                messageNodeDAO = dao,
                targetConversationId = "conv-A",
                nodeIds = listOf("node-1", "node-2"),
            )
        }
    }

    @Test
    fun `missing node ids pass validation`() {
        runBlocking {
            val dao = StubMessageNodeDAO(emptyMap())
            ConversationIdentityPolicy.assertNoCrossConversationCollision(
                messageNodeDAO = dao,
                targetConversationId = "conv-A",
                nodeIds = listOf("fresh-1", "fresh-2"),
            )
        }
    }

    @Test
    fun `cross-conversation collision throws typed error`() {
        val dao = StubMessageNodeDAO(mapOf("node-1" to "conv-A"))
        val ex = runCatching {
            runBlocking {
                ConversationIdentityPolicy.assertNoCrossConversationCollision(
                    messageNodeDAO = dao,
                    targetConversationId = "conv-B",
                    nodeIds = listOf("node-1"),
                )
            }
        }.exceptionOrNull()
        assertTrue(ex is MessageNodeIdentityConflictException)
        ex as MessageNodeIdentityConflictException
        assertEquals("node-1", ex.nodeId)
        assertEquals("conv-A", ex.ownerConversationId)
        assertEquals("conv-B", ex.attemptedConversationId)
        assertTrue(ex.message!!.contains("conv-A"))
        assertTrue(ex.message!!.contains("conv-B"))
        assertTrue(ex.message!!.contains("node-1"))
    }

    @Test
    fun `mixed same-owner and missing ids pass`() {
        runBlocking {
            val dao = StubMessageNodeDAO(mapOf("node-1" to "conv-A"))
            ConversationIdentityPolicy.assertNoCrossConversationCollision(
                messageNodeDAO = dao,
                targetConversationId = "conv-A",
                nodeIds = listOf("node-1", "fresh-2"),
            )
        }
    }

    @Test
    fun `first cross-conversation collision wins`() {
        val dao = StubMessageNodeDAO(
            mapOf(
                "node-1" to "conv-A",
                "node-2" to "conv-C",
            )
        )
        val ex = runCatching {
            runBlocking {
                ConversationIdentityPolicy.assertNoCrossConversationCollision(
                    messageNodeDAO = dao,
                    targetConversationId = "conv-B",
                    nodeIds = listOf("node-1", "node-2"),
                )
            }
        }.exceptionOrNull()
        assertTrue(ex is MessageNodeIdentityConflictException)
        ex as MessageNodeIdentityConflictException
        assertEquals("node-1", ex.nodeId)
        assertEquals("conv-A", ex.ownerConversationId)
    }

    @Test
    fun `duplicate ids in input list do not skew validation`() {
        runBlocking {
            val dao = StubMessageNodeDAO(mapOf("node-1" to "conv-A"))
            ConversationIdentityPolicy.assertNoCrossConversationCollision(
                messageNodeDAO = dao,
                targetConversationId = "conv-A",
                nodeIds = listOf("node-1", "node-1", "node-1"),
            )
        }
    }

    private fun <T> runBlocking(block: suspend () -> T): T =
        kotlinx.coroutines.runBlocking { block() }
}
