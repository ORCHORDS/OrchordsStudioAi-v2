package com.orchords.orchordsai.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Durable non-content accounting for one provider request/attempt whose usage is known or partial.
 *
 * There is intentionally no foreign key to conversations/messages: deleting mutable conversation
 * content must not silently rewrite historical provider usage. A user-facing explicit ledger-delete
 * policy can remove matching rows separately.
 */
@Entity(
    tableName = "provider_usage_event",
    indices = [
        Index("completed_at"),
        Index("provider_route_id"),
        Index("model_id"),
        Index("operation"),
        Index("conversation_id"),
        Index(value = ["logical_request_id", "attempt_index"]),
    ],
)
data class ProviderUsageEventEntity(
    @PrimaryKey
    @ColumnInfo("usage_event_id")
    val usageEventId: String,
    @ColumnInfo("logical_request_id")
    val logicalRequestId: String,
    @ColumnInfo("attempt_index")
    val attemptIndex: Int,
    @ColumnInfo("conversation_id")
    val conversationId: String? = null,
    @ColumnInfo("message_id")
    val messageId: String? = null,
    @ColumnInfo("provider_route_id")
    val providerRouteId: String? = null,
    @ColumnInfo("provider_label")
    val providerLabel: String? = null,
    @ColumnInfo("model_id")
    val modelId: String? = null,
    @ColumnInfo("model_label")
    val modelLabel: String? = null,
    @ColumnInfo("operation")
    val operation: String,
    @ColumnInfo("started_at")
    val startedAt: Long? = null,
    @ColumnInfo("completed_at")
    val completedAt: Long,
    @ColumnInfo("prompt_tokens")
    val promptTokens: Int? = null,
    @ColumnInfo("completion_tokens")
    val completionTokens: Int? = null,
    @ColumnInfo("cached_tokens")
    val cachedTokens: Int? = null,
    @ColumnInfo("request_count")
    val requestCount: Int = 1,
    @ColumnInfo("usage_source")
    val usageSource: String,
    @ColumnInfo("is_partial")
    val isPartial: Boolean = false,
    @ColumnInfo("schema_version")
    val schemaVersion: Int = 1,
    /** Immutable decimal-string snapshots; null means cost is unknown. */
    @ColumnInfo("cost_currency")
    val costCurrency: String? = null,
    @ColumnInfo("cost_input")
    val costInput: String? = null,
    @ColumnInfo("cost_cached_input")
    val costCachedInput: String? = null,
    @ColumnInfo("cost_output")
    val costOutput: String? = null,
    @ColumnInfo("cost_total")
    val costTotal: String? = null,
    @ColumnInfo("pricing_revision")
    val pricingRevision: String? = null,
) {
    init {
        require(usageEventId.isNotBlank())
        require(logicalRequestId.isNotBlank())
        require(attemptIndex >= 0)
        require(operation.isNotBlank() && operation.length <= 48)
        require(usageSource.isNotBlank() && usageSource.length <= 48)
        require(requestCount >= 1)
        require(promptTokens == null || promptTokens >= 0)
        require(completionTokens == null || completionTokens >= 0)
        require(cachedTokens == null || cachedTokens >= 0)
        require(schemaVersion >= 1)
        require(providerLabel == null || providerLabel.length <= 160)
        require(modelLabel == null || modelLabel.length <= 160)
    }
}
