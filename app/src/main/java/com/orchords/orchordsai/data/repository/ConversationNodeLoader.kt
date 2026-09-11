package com.orchords.orchordsai.data.repository

import android.database.sqlite.SQLiteBlobTooBigException
import android.util.Log
import kotlinx.coroutines.CancellationException
import com.orchords.ai.ui.UIMessage
import com.orchords.orchordsai.data.db.dao.MessageNodeDAO
import com.orchords.orchordsai.data.db.dao.MessageNodeMeta
import com.orchords.orchordsai.data.db.entity.MessageNodeEntity
import com.orchords.orchordsai.data.model.ConversationLoadState
import com.orchords.orchordsai.data.model.MessageNode
import com.orchords.orchordsai.utils.JsonInstant
import kotlin.uuid.Uuid

private const val TAG = "ConversationNodeLoader"
private const val NODE_PAGE_SIZE = 64
private const val LEGACY_INLINE_CHUNK_CHARS = 64 * 1024
private const val MAX_LEGACY_INLINE_UTF8_BYTES = 16L * 1024 * 1024

private fun logUnreadable(message: String) {
    runCatching { Log.w(TAG, message) }
}

internal data class MessageNodeLoadResult(
    val nodes: List<MessageNode>,
    val corruptNodeIds: Set<String>,
) {
    val loadState: ConversationLoadState
        get() = if (corruptNodeIds.isEmpty()) ConversationLoadState.COMPLETE else ConversationLoadState.PARTIAL
}

internal interface ConversationNodePayloadSource {
    suspend fun resolve(entity: MessageNodeEntity): String?
}

internal interface ConversationNodeReader {
    suspend fun count(conversationId: String): Int
    suspend fun page(conversationId: String, limit: Int, offset: Int): List<MessageNodeEntity>
    suspend fun nodeIdAtOffset(conversationId: String, offset: Int): String?
    suspend fun nodeById(nodeId: String): MessageNodeEntity?

    /**
     * Recovery seam for rows whose full inline payload cannot fit a CursorWindow.
     * Tests/fakes may keep the old direct-row behavior; production overrides this
     * with metadata + bounded substr projections.
     */
    suspend fun legacyNodeById(nodeId: String): MessageNodeEntity? = nodeById(nodeId)
}

internal suspend fun loadConversationNodesSafely(
    messageNodeDAO: MessageNodeDAO,
    conversationId: String,
    favoriteNodeIds: Set<Uuid>,
    payloadSource: ConversationNodePayloadSource,
): MessageNodeLoadResult = loadConversationNodesSafely(
    reader = object : ConversationNodeReader {
        override suspend fun count(conversationId: String) = messageNodeDAO.countNodesOfConversation(conversationId)
        override suspend fun page(conversationId: String, limit: Int, offset: Int) =
            messageNodeDAO.getNodesOfConversationPaged(conversationId, limit, offset)
        override suspend fun nodeIdAtOffset(conversationId: String, offset: Int) =
            messageNodeDAO.getNodeIdAtOffset(conversationId, offset)
        override suspend fun nodeById(nodeId: String) = messageNodeDAO.getNodeById(nodeId)

        override suspend fun legacyNodeById(nodeId: String): MessageNodeEntity? {
            val meta = messageNodeDAO.getNodeMetaById(nodeId) ?: return null
            if (meta.payloadBlobId != null) {
                return meta.toEntity(messages = "")
            }
            val expectedBytes = messageNodeDAO.getInlineMessagesUtf8ByteLength(nodeId) ?: return null
            val payload = readLegacyInlinePayload(
                expectedUtf8Bytes = expectedBytes,
                maxUtf8Bytes = MAX_LEGACY_INLINE_UTF8_BYTES,
                chunkChars = LEGACY_INLINE_CHUNK_CHARS,
            ) { startChar, maxChars ->
                messageNodeDAO.getInlineMessagesChunk(
                    nodeId = nodeId,
                    startCharOneBased = startChar + 1,
                    maxChars = maxChars,
                )
            } ?: return null
            return meta.toEntity(messages = payload)
        }
    },
    payloadSource = payloadSource,
    conversationId = conversationId,
    favoriteNodeIds = favoriteNodeIds,
)

private fun MessageNodeMeta.toEntity(messages: String) = MessageNodeEntity(
    id = id,
    conversationId = conversationId,
    nodeIndex = nodeIndex,
    messages = messages,
    selectIndex = selectIndex,
    payloadBlobId = payloadBlobId,
)

internal suspend fun loadConversationNodesSafely(
    reader: ConversationNodeReader,
    conversationId: String,
    favoriteNodeIds: Set<Uuid>,
    payloadSource: ConversationNodePayloadSource,
): MessageNodeLoadResult {
    val nodes = mutableListOf<MessageNode>()
    val corruptNodeIds = linkedSetOf<String>()
    val total = reader.count(conversationId)
    var offset = 0

    suspend fun decode(entity: MessageNodeEntity) {
        try {
            val nodeId = Uuid.parse(entity.id)
            val rawJson = payloadSource.resolve(entity)
                ?: run {
                    corruptNodeIds += entity.id
                    logUnreadable(
                        "Unresolvable message node conversation=$conversationId node=${entity.id} (payload_blob_id=${entity.payloadBlobId})"
                    )
                    return
                }
            nodes += MessageNode(
                id = nodeId,
                messages = JsonInstant.decodeFromString<List<UIMessage>>(rawJson),
                selectIndex = entity.selectIndex,
                isFavorite = favoriteNodeIds.contains(nodeId),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            corruptNodeIds += entity.id
            logUnreadable(
                "Unreadable message node conversation=$conversationId node=${entity.id} error=${error.javaClass.simpleName}"
            )
        }
    }

    while (offset < total) {
        val page = try {
            reader.page(conversationId, NODE_PAGE_SIZE, offset)
        } catch (error: SQLiteBlobTooBigException) {
            null
        } catch (error: IllegalStateException) {
            null
        }

        if (page != null) {
            if (page.isEmpty()) break
            for (entity in page) decode(entity)
            offset += page.size
            continue
        }

        val pageEnd = minOf(offset + NODE_PAGE_SIZE, total)
        for (rowOffset in offset until pageEnd) {
            val nodeId = reader.nodeIdAtOffset(conversationId, rowOffset) ?: continue
            val entity = try {
                reader.legacyNodeById(nodeId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                corruptNodeIds += nodeId
                logUnreadable(
                    "Unreadable message row conversation=$conversationId node=$nodeId error=${error.javaClass.simpleName}"
                )
                null
            }
            if (entity != null) decode(entity) else corruptNodeIds += nodeId
        }
        offset = pageEnd
    }

    return MessageNodeLoadResult(nodes = nodes, corruptNodeIds = corruptNodeIds)
}
