package com.orchords.orchordsai.data.db.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.orchords.orchordsai.data.db.entity.ProviderUsageEventEntity

@Dao
interface ProviderUsageEventDAO {
    /**
     * Immutable/idempotent recording: replay of the same stable event id is a no-op, never an
     * increment. A separately identified billable retry/attempt uses its own event id.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: ProviderUsageEventEntity): Long

    @Query("SELECT * FROM provider_usage_event WHERE usage_event_id = :eventId LIMIT 1")
    suspend fun getById(eventId: String): ProviderUsageEventEntity?

    @Query("SELECT COUNT(*) FROM provider_usage_event")
    suspend fun countAll(): Long

    @Query(
        """SELECT
            COUNT(*) AS eventCount,
            COALESCE(SUM(prompt_tokens), 0) AS promptTokens,
            COALESCE(SUM(completion_tokens), 0) AS completionTokens,
            COALESCE(SUM(cached_tokens), 0) AS cachedTokens,
            COALESCE(SUM(CASE WHEN prompt_tokens IS NULL OR completion_tokens IS NULL THEN 1 ELSE 0 END), 0) AS unknownUsageEvents,
            COALESCE(SUM(CASE WHEN cost_total IS NULL THEN 1 ELSE 0 END), 0) AS unknownCostEvents
        FROM provider_usage_event"""
    )
    suspend fun aggregateLifetime(): ProviderUsageAggregate

    @Query("DELETE FROM provider_usage_event")
    suspend fun deleteAll()
}

data class ProviderUsageAggregate(
    @ColumnInfo("eventCount") val eventCount: Long = 0,
    @ColumnInfo("promptTokens") val promptTokens: Long = 0,
    @ColumnInfo("completionTokens") val completionTokens: Long = 0,
    @ColumnInfo("cachedTokens") val cachedTokens: Long = 0,
    @ColumnInfo("unknownUsageEvents") val unknownUsageEvents: Long = 0,
    @ColumnInfo("unknownCostEvents") val unknownCostEvents: Long = 0,
)
