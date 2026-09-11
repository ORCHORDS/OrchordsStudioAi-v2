package com.orchords.orchordsai.data.model

import java.util.concurrent.ConcurrentHashMap

/**
 * Process-local fail-closed registry for conversation IDs that entered the app as temporary.
 *
 * The canonical semantic state still lives on [Conversation.retention]. This registry exists to
 * close the short initialization window before a newly created in-memory Conversation has been
 * classified by the session/UI layer. Repository writes consult both boundaries.
 *
 * IDs intentionally remain registered for the process lifetime unless an explicit promotion flow
 * marks them retained. That is safer than allowing a late background writer to persist a temporary
 * chat after the UI has been destroyed.
 */
object TemporaryConversationRegistry {
    private val temporaryIds = ConcurrentHashMap.newKeySet<String>()

    fun markTemporary(conversationId: String) {
        temporaryIds.add(conversationId)
    }

    fun markRetained(conversationId: String) {
        temporaryIds.remove(conversationId)
    }

    fun isTemporary(conversationId: String): Boolean = conversationId in temporaryIds
}
