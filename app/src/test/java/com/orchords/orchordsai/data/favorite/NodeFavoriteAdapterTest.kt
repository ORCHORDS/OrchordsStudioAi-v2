package com.orchords.orchordsai.data.favorite

import com.orchords.orchordsai.data.db.entity.FavoriteEntity
import com.orchords.orchordsai.data.model.NodeFavoriteRef
import com.orchords.orchordsai.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.uuid.Uuid

class NodeFavoriteAdapterTest {
    private val conversationId = Uuid.random()
    private val nodeId = Uuid.random()

    private fun entity(refKey: String = NodeFavoriteAdapter.buildRefKey(conversationId.toString(), nodeId.toString()), refJson: String = JsonInstant.encodeToString(NodeFavoriteRef(conversationId, nodeId)), type: String = "node") = FavoriteEntity("id", type, refKey, refJson, "", null, 0, 0)

    @Test fun validReferenceRequiresMatchingCanonicalKey() {
        assertEquals(NodeFavoriteRef(conversationId, nodeId), NodeFavoriteAdapter.decodeRef(entity()))
        assertNull(NodeFavoriteAdapter.decodeRef(entity(refKey = "node:$conversationId:${Uuid.random()}")))
        assertNull(NodeFavoriteAdapter.decodeRef(entity(refJson = "{")))
        assertNull(NodeFavoriteAdapter.decodeRef(entity(type = "message")))
    }
}
