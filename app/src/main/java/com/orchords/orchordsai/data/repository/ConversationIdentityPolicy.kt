package com.orchords.orchordsai.data.repository

import com.orchords.orchordsai.data.db.dao.MessageNodeDAO

/**
 * Identity-conflict policy for `message_node` rows. `message_node.id` is a global
 * primary key, so when one conversation's bulk save carries a node ID that already
 * belongs to a different conversation, that is an identity/integrity conflict and
 * must fail closed rather than REPLACE the existing row.
 *
 * Same-conversation updates remain supported through Room's `@Update` path and
 * through explicit upsert semantics — only cross-conversation collisions are treated
 * as errors. See issue #295.
 */
internal object ConversationIdentityPolicy {

    /**
     * Validate that none of [nodeIds] is currently owned by a conversation other than
     * [targetConversationId]. Throws [MessageNodeIdentityConflictException] on the
     * first collision found (insertion order of [nodeIds]).
     *
     * Returns silently when every id either is absent from the store or already
     * belongs to [targetConversationId].
     */
    suspend fun assertNoCrossConversationCollision(
        messageNodeDAO: MessageNodeDAO,
        targetConversationId: String,
        nodeIds: List<String>,
    ) {
        if (nodeIds.isEmpty()) return
        val deduped = nodeIds.distinct()
        val owners = messageNodeDAO.getOwnersByIds(deduped)
            .associate { it.id to it.conversationId }
        for (id in deduped) {
            val owner = owners[id] ?: continue
            if (owner != targetConversationId) {
                throw MessageNodeIdentityConflictException(
                    nodeId = id,
                    ownerConversationId = owner,
                    attemptedConversationId = targetConversationId,
                )
            }
        }
    }
}
