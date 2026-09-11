package com.orchords.orchordsai.data.db.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orchords.orchordsai.data.db.AppDatabase
import com.orchords.orchordsai.data.db.entity.ProviderUsageEventEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProviderUsageEventDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: ProviderUsageEventDAO

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.providerUsageEventDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun duplicateStableEventIdIsCountedExactlyOnceWhileDistinctAttemptRemainsIndependent() = runTest {
        val first = event(
            eventId = "usage-request-1-attempt-0",
            logicalRequestId = "request-1",
            attemptIndex = 0,
            promptTokens = 11,
            completionTokens = 7,
            cachedTokens = 3,
        )
        val retry = event(
            eventId = "usage-request-1-attempt-1",
            logicalRequestId = "request-1",
            attemptIndex = 1,
            promptTokens = 5,
            completionTokens = 4,
            cachedTokens = null,
            partial = true,
        )

        dao.insert(first)
        dao.insert(first.copy(promptTokens = 999)) // immutable replay is ignored
        dao.insert(retry)

        val aggregate = dao.aggregateLifetime()
        assertEquals(2L, aggregate.eventCount)
        assertEquals(16L, aggregate.promptTokens)
        assertEquals(11L, aggregate.completionTokens)
        assertEquals(3L, aggregate.cachedTokens)
        assertEquals(0L, aggregate.unknownUsageEvents)
        assertEquals(11, dao.getById(first.usageEventId)?.promptTokens)
    }

    @Test
    fun missingUsageRemainsUnknownInsteadOfBeingPersistedAsFabricatedZero() = runTest {
        val unknown = event(
            eventId = "usage-unknown",
            logicalRequestId = "request-unknown",
            attemptIndex = 0,
            promptTokens = null,
            completionTokens = null,
            cachedTokens = null,
            partial = true,
            usageSource = "UNKNOWN",
        )

        dao.insert(unknown)

        val stored = dao.getById(unknown.usageEventId)!!
        assertNull(stored.promptTokens)
        assertNull(stored.completionTokens)
        assertNull(stored.cachedTokens)
        assertEquals(1L, dao.aggregateLifetime().unknownUsageEvents)
    }

    private fun event(
        eventId: String,
        logicalRequestId: String,
        attemptIndex: Int,
        promptTokens: Int?,
        completionTokens: Int?,
        cachedTokens: Int?,
        partial: Boolean = false,
        usageSource: String = "PROVIDER_REPORTED",
    ) = ProviderUsageEventEntity(
        usageEventId = eventId,
        logicalRequestId = logicalRequestId,
        attemptIndex = attemptIndex,
        conversationId = "conversation-id-is-metadata-not-foreign-key",
        messageId = "message-1",
        providerRouteId = "orchords-gateway",
        providerLabel = "Orchords",
        modelId = "oai-1.0",
        modelLabel = "oai-1.0",
        operation = "CHAT",
        startedAt = 1_000L,
        completedAt = 2_000L,
        promptTokens = promptTokens,
        completionTokens = completionTokens,
        cachedTokens = cachedTokens,
        usageSource = usageSource,
        isPartial = partial,
    )
}
