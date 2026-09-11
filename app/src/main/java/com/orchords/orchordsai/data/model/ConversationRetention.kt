package com.orchords.orchordsai.data.model

import kotlinx.serialization.Serializable

/**
 * Controls whether a conversation is allowed to enter the durable conversation store.
 *
 * Temporary conversations are intentionally session-only. They may still use the normal
 * in-memory chat/generation pipeline, but repository persistence, history/search indexing,
 * backup, and sync must treat them as non-durable unless an explicit promotion flow changes
 * this policy first.
 */
@Serializable
enum class ConversationRetention {
    RETAINED,
    TEMPORARY,
}

internal fun ConversationRetention.allowsDurablePersistence(): Boolean =
    this == ConversationRetention.RETAINED
