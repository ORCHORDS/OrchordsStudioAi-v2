package com.orchords.orchordsai.data.repository

import com.orchords.orchordsai.data.db.dao.FavoriteDAO
import com.orchords.orchordsai.data.db.entity.FavoriteEntity
import com.orchords.orchordsai.data.favorite.NodeFavoriteAdapter
import com.orchords.orchordsai.data.model.FavoriteType
import com.orchords.orchordsai.data.model.MessageNode
import com.orchords.orchordsai.data.model.NodeFavoriteRef
import com.orchords.orchordsai.data.model.NodeFavoriteTarget
import com.orchords.orchordsai.utils.JsonInstant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class FavoriteRepositoryQuarantineTest {

    private class StubFavoriteDAO(
        private val byRefKey: Map<String, FavoriteEntity>,
    ) : FavoriteDAO {
        override fun listAll() = throw UnsupportedOperationException()
        override fun listByType(type: String) = throw UnsupportedOperationException()
        override suspend fun upsert(entity: FavoriteEntity) = throw UnsupportedOperationException()
        override suspend fun getRefKeysByType(type: String): List<String> = byRefKey.keys.toList()
        override suspend fun getNodeFavoritesOfConversation(conversationId: String): List<FavoriteEntity> =
            byRefKey.values.filter { it.type == FavoriteType.NODE.value && it.refKey.contains(conversationId) }
        override suspend fun getByRefKey(refKey: String): FavoriteEntity? = byRefKey[refKey]
        override suspend fun existsByRefKey(refKey: String): Boolean = byRefKey.containsKey(refKey)
        override suspend fun deleteByRefKey(refKey: String) = throw UnsupportedOperationException()
        override suspend fun deleteById(id: String) = throw UnsupportedOperationException()
    }

    private fun validEntity(conv: Uuid, node: Uuid): FavoriteEntity {
        val ref = NodeFavoriteRef(conversationId = conv, nodeId = node)
        val meta = com.orchords.orchordsai.data.model.FavoriteMeta(title = "t", previewText = "hi")
        return FavoriteEntity(
            id = NodeFavoriteAdapter.buildRefKey(conv.toString(), node.toString()),
            type = FavoriteType.NODE.value,
            refKey = NodeFavoriteAdapter.buildRefKey(conv.toString(), node.toString()),
            refJson = JsonInstant.encodeToString(ref),
            snapshotJson = "{}",
            metaJson = JsonInstant.encodeToString(meta),
            createdAt = 0L,
            updatedAt = 0L,
        )
    }

    private fun malformedEntity(refKey: String, conv: Uuid, node: Uuid): FavoriteEntity {
        val ref = NodeFavoriteRef(conversationId = conv, nodeId = node)
        return FavoriteEntity(
            id = refKey,
            type = FavoriteType.NODE.value,
            refKey = refKey,
            refJson = JsonInstant.encodeToString(ref),
            snapshotJson = "",
            metaJson = "",
            createdAt = 0L,
            updatedAt = 0L,
        )
    }

    @Test
    fun existsByRefKey_returnsFalse_forMalformedRow() = runBlocking {
        val conv = Uuid.random()
        val node = Uuid.random()
        val refKey = NodeFavoriteAdapter.buildRefKey(conv.toString(), node.toString())
        val entity = malformedEntity(refKey = "node:corrupt:ref", conv = conv, node = node)
        val repo = FavoriteRepository(StubFavoriteDAO(mapOf(refKey to entity)))
        assertFalse(repo.existsByRefKey(refKey))
    }

    @Test
    fun existsByRefKey_returnsTrue_forValidRow() = runBlocking {
        val conv = Uuid.random()
        val node = Uuid.random()
        val entity = validEntity(conv, node)
        val repo = FavoriteRepository(StubFavoriteDAO(mapOf(entity.refKey to entity)))
        assertTrue(repo.existsByRefKey(entity.refKey))
    }

    @Test
    fun getNodeFavoritesOfConversation_filtersMalformedRows() = runBlocking {
        val conv = Uuid.random()
        val otherConv = Uuid.random()
        val valid = validEntity(conv, Uuid.random())
        val malformed = malformedEntity(refKey = "node:corrupt:ref", conv = conv, node = Uuid.random())
        val foreign = validEntity(otherConv, Uuid.random())
        val repo = FavoriteRepository(
            StubFavoriteDAO(
                mapOf(valid.refKey to valid, malformed.refKey to malformed, foreign.refKey to foreign)
            )
        )
        val refs = repo.getNodeFavoritesOfConversation(conv).map { it.refKey }
        assertEquals(listOf(valid.refKey), refs)
    }
}
