package com.orchords.orchordsai.data.repository

/**
 * Raised when an attempt to insert or rewrite message nodes references a node ID that
 * already exists in the store under a different conversation.
 *
 * `message_node.id` is a global primary key, so a collision across conversations is an
 * identity/integrity conflict, not a legitimate upsert. Silently REPLACE-ing (or even
 * ABORT-ing after a destructive delete-then-insert) would silently move or delete the
 * other conversation's history. See issue #295.
 *
 * The exception intentionally carries only the colliding node ID and the conflicting
 * owner conversation ID — never the node payload — so it can be surfaced as a bounded
 * error to callers (e.g. restore/import pipelines) without leaking private message
 * contents.
 */
class MessageNodeIdentityConflictException(
    val nodeId: String,
    val ownerConversationId: String,
    val attemptedConversationId: String,
) : IllegalStateException(
    "Message node id '$nodeId' already belongs to conversation '$ownerConversationId' " +
        "and cannot be reused by conversation '$attemptedConversationId'"
)
