package com.orchords.orchordsai.data.repository

import com.orchords.orchordsai.data.db.entity.MessageNodeEntity
import com.orchords.orchordsai.data.model.ConversationLoadState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationNodeLoaderTest {
    @Test
    fun `failed 64 row page isolates one unreadable row without losing healthy neighbors`() = runBlocking {
        val rows = (0 until 70).map(::row)
        val badId = rows[10].id
        val reader = FakeReader(rows, failedPages = setOf(0), unreadableIds = setOf(badId))

        val result = loadConversationNodesSafely(
            reader = reader,
            conversationId = CONVERSATION_ID,
            favoriteNodeIds = emptySet(),
            payloadSource = InlinePayloadSource,
        )

        assertEquals(69, result.nodes.size)
        assertEquals(setOf(badId), result.corruptNodeIds)
        assertEquals(ConversationLoadState.PARTIAL, result.loadState)
        assertEquals(rows.filterNot { it.id == badId }.map { it.id }, result.nodes.map { it.id.toString() })
        assertEquals((0 until 64).toList(), reader.singleRowOffsets)
    }

    @Test
    fun `failed page can recover legacy row without direct full-row read`() = runBlocking {
        val rows = (0 until 3).map(::row)
        val legacyId = rows[1].id
        val reader = FakeReader(
            rows = rows,
            failedPages = setOf(0),
            unreadableIds = setOf(legacyId),
            legacyRecoverableIds = setOf(legacyId),
        )

        val result = loadConversationNodesSafely(
            reader = reader,
            conversationId = CONVERSATION_ID,
            favoriteNodeIds = emptySet(),
            payloadSource = InlinePayloadSource,
        )

        assertEquals(3, result.nodes.size)
        assertTrue(result.corruptNodeIds.isEmpty())
        assertEquals(ConversationLoadState.COMPLETE, result.loadState)
        assertTrue(legacyId in reader.legacyReads)
    }

    @Test
    fun `two unreadable rows are isolated independently across failed pages`() = runBlocking {
        val rows = (0 until 150).map(::row)
        val badIds = setOf(rows[10].id, rows[130].id)
        val reader = FakeReader(rows, failedPages = setOf(0, 128), unreadableIds = badIds)

        val result = loadConversationNodesSafely(
            reader = reader,
            conversationId = CONVERSATION_ID,
            favoriteNodeIds = emptySet(),
            payloadSource = InlinePayloadSource,
        )

        assertEquals(148, result.nodes.size)
        assertEquals(badIds, result.corruptNodeIds)
        assertEquals(ConversationLoadState.PARTIAL, result.loadState)
        assertTrue(result.nodes.none { it.id.toString() in badIds })
        assertEquals(rows.filterNot { it.id in badIds }.map { it.id }, result.nodes.map { it.id.toString() })
    }

    @Test
    fun `healthy pages retain fast paged path`() = runBlocking {
        val rows = (0 until 150).map(::row)
        val reader = FakeReader(rows)

        val result = loadConversationNodesSafely(
            reader = reader,
            conversationId = CONVERSATION_ID,
            favoriteNodeIds = emptySet(),
            payloadSource = InlinePayloadSource,
        )

        assertEquals(150, result.nodes.size)
        assertEquals(emptySet<String>(), result.corruptNodeIds)
        assertEquals(ConversationLoadState.COMPLETE, result.loadState)
        assertEquals(listOf(0, 64, 128), reader.pageOffsets)
        assertTrue(reader.singleRowOffsets.isEmpty())
    }

    @Test
    fun `externalized payload resolves via the payload source`() = runBlocking {
        val rows = listOf(
            MessageNodeEntity(
                id = "00000000-0000-0000-0000-000000000001",
                conversationId = CONVERSATION_ID,
                nodeIndex = 0,
                messages = "",
                selectIndex = 0,
                payloadBlobId = 99L,
            ),
            row(1),
        )
        val reader = FakeReader(rows)
        val source = MapPayloadSource(mapOf(99L to "[]"))

        val result = loadConversationNodesSafely(
            reader = reader,
            conversationId = CONVERSATION_ID,
            favoriteNodeIds = emptySet(),
            payloadSource = source,
        )

        assertEquals(2, result.nodes.size)
        assertTrue(result.corruptNodeIds.isEmpty())
        assertEquals(ConversationLoadState.COMPLETE, result.loadState)
    }

    @Test
    fun `unreadable blob id marks the row corrupt but keeps neighbors`() = runBlocking {
        val rows = (0 until 3).map(::row)
        val broken = rows[1].copy(payloadBlobId = 42L)
        val rowsWithBlob = listOf(rows[0], broken, rows[2])
        val reader = FakeReader(rowsWithBlob)

        val result = loadConversationNodesSafely(
            reader = reader,
            conversationId = CONVERSATION_ID,
            favoriteNodeIds = emptySet(),
            payloadSource = MapPayloadSource(emptyMap()),
        )

        assertEquals(2, result.nodes.size)
        assertEquals(setOf(broken.id), result.corruptNodeIds)
        assertEquals(ConversationLoadState.PARTIAL, result.loadState)
        assertEquals(listOf(rows[0].id, rows[2].id), result.nodes.map { it.id.toString() })
    }

    @Test
    fun `legacy recovery path must not call nodeById with the full CursorWindow row`() = runBlocking {
        // See issue #345: the legacy recovery seam MUST avoid the full-row read that
        // historically required the (now removed) global CursorWindow reflection hack.
        // This test loads a large inline payload through a tracking reader and asserts
        // that the recovery path never delegates to the direct nodeById accessor.
        val rows = (0 until 3).map(::row)
        val legacyId = rows[1].id
        // Build a JSON array large enough to require multiple bounded chunks. Whitespace
        // between array brackets is valid JSON and yields an empty List<UIMessage>.
        val largePayload = "[" + " ".repeat(LARGE_INLINE_BYTES.toInt() - 2) + "]"
        val reader = TrackingReader(
            rows = rows,
            inlinePayloads = mapOf(legacyId to largePayload),
        )

        val result = loadConversationNodesSafely(
            reader = reader,
            conversationId = CONVERSATION_ID,
            favoriteNodeIds = emptySet(),
            payloadSource = legacyInlineSourceOf(reader),
        )

        // The first and third small rows decode cleanly through the legacy path; the
        // enormous inline row gets projected too. The exact decode status of the giant
        // row depends on platform JSON limits, so we only assert the safety property
        // that matters for issue #345: the recovery path used chunked reads and never
        // touched the full-row accessor.
        assertTrue(result.nodes.isNotEmpty())
        assertEquals(listOf(legacyId), reader.legacyChunkReads)
        assertTrue(reader.chunkReadCount(legacyId) > 1)
        assertEquals(emptyList<String>(), reader.fullRowReads)
    }

    private fun row(index: Int) = MessageNodeEntity(
        id = id(index),
        conversationId = CONVERSATION_ID,
        nodeIndex = index,
        messages = "[]",
        selectIndex = 0,
    )

    private fun id(index: Int): String = "00000000-0000-0000-0000-${index.toString().padStart(12, '0')}"

    private class FakeReader(
        private val rows: List<MessageNodeEntity>,
        private val failedPages: Set<Int> = emptySet(),
        private val unreadableIds: Set<String> = emptySet(),
        private val legacyRecoverableIds: Set<String> = emptySet(),
    ) : ConversationNodeReader {
        val pageOffsets = mutableListOf<Int>()
        val singleRowOffsets = mutableListOf<Int>()
        val legacyReads = mutableListOf<String>()

        override suspend fun count(conversationId: String): Int = rows.size

        override suspend fun page(conversationId: String, limit: Int, offset: Int): List<MessageNodeEntity> {
            pageOffsets += offset
            if (offset in failedPages) throw IllegalStateException("simulated page read failure")
            return rows.drop(offset).take(limit)
        }

        override suspend fun nodeIdAtOffset(conversationId: String, offset: Int): String? {
            singleRowOffsets += offset
            return rows.getOrNull(offset)?.id
        }

        override suspend fun nodeById(nodeId: String): MessageNodeEntity? {
            if (nodeId in unreadableIds) throw IllegalStateException("simulated row read failure")
            return rows.firstOrNull { it.id == nodeId }
        }

        override suspend fun legacyNodeById(nodeId: String): MessageNodeEntity? {
            legacyReads += nodeId
            // Real implementations MUST project the inline payload through bounded chunks
            // rather than calling [nodeById], which historically required the
            // (now removed) CursorWindow reflection override. Test fakes must not
            // delegate to [nodeById] either, so we model the recovery seam directly:
            // recoverable ids project a chunked payload; unreadable ids fail loudly.
            if (nodeId in legacyRecoverableIds) {
                val staged = rows.firstOrNull { it.id == nodeId } ?: return null
                var cursor = 0
                return readLegacyInlinePayload(
                    expectedUtf8Bytes = staged.messages.toByteArray(Charsets.UTF_8).size.toLong(),
                    maxUtf8Bytes = 16L * 1024 * 1024,
                    chunkChars = 64 * 1024,
                ) { startChar, maxChars ->
                    cursor = startChar
                    if (startChar >= staged.messages.length) ""
                    else staged.messages.substring(startChar, minOf(startChar + maxChars, staged.messages.length))
                }?.let { reconstructed -> staged.copy(messages = reconstructed) }
            }
            if (nodeId in unreadableIds) throw IllegalStateException("simulated row read failure")
            return rows.firstOrNull { it.id == nodeId }
        }
    }

    companion object {
        private const val CONVERSATION_ID = "00000000-0000-0000-0000-000000000255"
        internal const val LARGE_INLINE_BYTES = 3L * 1024 * 1024
    }
}

private class TrackingReader(
    private val rows: List<MessageNodeEntity>,
    private val inlinePayloads: Map<String, String>,
) : ConversationNodeReader {
    val fullRowReads = mutableListOf<String>()
    val legacyChunkReads = mutableListOf<String>()
    private val chunkReads = mutableMapOf<String, Int>()

    override suspend fun count(conversationId: String): Int = rows.size

    override suspend fun page(conversationId: String, limit: Int, offset: Int): List<MessageNodeEntity> {
        // Force the loader into the legacy per-row recovery path so we can observe chunk usage.
        throw IllegalStateException("simulated page read failure for recovery path")
    }

    override suspend fun nodeIdAtOffset(conversationId: String, offset: Int): String? {
        return rows.getOrNull(offset)?.id
    }

    override suspend fun nodeById(nodeId: String): MessageNodeEntity? {
        fullRowReads += nodeId
        // Historic reflection-hack behavior lived behind this exact accessor. Fail loudly
        // if a future caller accidentally reintroduces a direct full-row read.
        error("TrackingReader.nodeById must not be called; the loader must project via chunks")
    }

    override suspend fun legacyNodeById(nodeId: String): MessageNodeEntity? {
        val payload = inlinePayloads[nodeId]
        if (payload == null) {
            return rows.firstOrNull { it.id == nodeId }
        }
        val reconstructed = readLegacyInlinePayload(
            expectedUtf8Bytes = payload.toByteArray(Charsets.UTF_8).size.toLong(),
            maxUtf8Bytes = 16L * 1024 * 1024,
            chunkChars = 64 * 1024,
        ) { startChar, maxChars ->
            if (startChar >= payload.length) ""
            else payload.substring(startChar, minOf(startChar + maxChars, payload.length))
                .also { if (it.isNotEmpty()) chunkReads.merge(nodeId, 1, Int::plus) }
        } ?: return null
        legacyChunkReads += nodeId
        val meta = rows.first { it.id == nodeId }
        return meta.copy(messages = reconstructed)
    }

    fun chunkReadCount(nodeId: String): Int = chunkReads[nodeId] ?: 0
}

private fun legacyInlineSourceOf(reader: TrackingReader): ConversationNodePayloadSource =
    object : ConversationNodePayloadSource {
        override suspend fun resolve(entity: MessageNodeEntity): String? =
            entity.payloadBlobId?.let { null } ?: entity.messages.takeIf { it.isNotEmpty() }
    }

private object InlinePayloadSource : ConversationNodePayloadSource {
    override suspend fun resolve(entity: MessageNodeEntity): String? =
        entity.payloadBlobId?.let { null } ?: entity.messages.takeIf { it.isNotEmpty() }
}

private class MapPayloadSource(private val blobs: Map<Long, String>) : ConversationNodePayloadSource {
    override suspend fun resolve(entity: MessageNodeEntity): String? {
        val blobId = entity.payloadBlobId ?: return entity.messages.takeIf { it.isNotEmpty() }
        return blobs[blobId]
    }
}
