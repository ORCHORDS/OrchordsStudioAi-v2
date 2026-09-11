package com.orchords.orchordsai.data.model

import java.util.concurrent.ConcurrentHashMap

/**
 * Fail-closed registry for conversation IDs that entered the app as temporary.
 *
 * [Conversation.retention] remains the semantic source of truth once the in-memory conversation
 * exists. The registry closes the initialization/background-writer gap and is restored from a
 * non-content marker store after process recreation.
 */
object TemporaryConversationRegistry {
    private val temporaryIds = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var markerStore: TemporaryConversationMarkerStore? = null

    @Synchronized
    fun initialize(store: TemporaryConversationMarkerStore) {
        markerStore = store
        store.listTemporaryIds().forEach(temporaryIds::add)
    }

    fun markTemporary(conversationId: String) {
        temporaryIds.add(conversationId)
        markerStore?.markTemporary(conversationId)
    }

    fun markRetained(conversationId: String) {
        temporaryIds.remove(conversationId)
        markerStore?.markRetained(conversationId)
    }

    fun isTemporary(conversationId: String): Boolean = conversationId in temporaryIds
}
