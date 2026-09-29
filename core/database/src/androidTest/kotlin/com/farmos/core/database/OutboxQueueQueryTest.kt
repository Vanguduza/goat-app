package com.farmos.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.model.SyncState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Read-only sync queue queries stay inside one farm and one sync state. */
@RunWith(AndroidJUnit4::class)
class OutboxQueueQueryTest {
    private lateinit var database: FarmOsDatabase
    private val farmA = "11111111-1111-4111-8111-111111111111"
    private val farmB = "22222222-2222-4222-8222-222222222222"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, FarmOsDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun listForFarmInStateIsFarmScopedStateScopedNewestFirstAndBounded() = runBlocking {
        val outbox = database.outbox()
        outbox.insert(row("a-old", farmA, SyncState.CONFLICT, createdAt = 1_000L))
        outbox.insert(row("a-new", farmA, SyncState.CONFLICT, createdAt = 3_000L))
        outbox.insert(row("a-retry", farmA, SyncState.RETRY_WAIT, createdAt = 2_000L))
        outbox.insert(row("b-conflict", farmB, SyncState.CONFLICT, createdAt = 4_000L))

        assertEquals(
            listOf("a-new", "a-old"),
            outbox.listForFarmInState(farmA, SyncState.CONFLICT.name, 10).map { it.mutationId },
        )
        assertEquals(listOf("a-new"), outbox.listForFarmInState(farmA, SyncState.CONFLICT.name, 1).map { it.mutationId })
        assertEquals(listOf("b-conflict"), outbox.listForFarmInState(farmB, SyncState.CONFLICT.name, 10).map { it.mutationId })
        assertEquals(emptyList<String>(), outbox.listForFarmInState(farmB, SyncState.RETRY_WAIT.name, 10).map { it.mutationId })
    }

    @Test
    fun countByStateForFarmNeverCountsAnotherFarm() = runBlocking {
        val outbox = database.outbox()
        outbox.insert(row("a1", farmA, SyncState.REJECTED, createdAt = 1L))
        outbox.insert(row("a2", farmA, SyncState.REJECTED, createdAt = 2L))
        outbox.insert(row("a3", farmA, SyncState.ACKNOWLEDGED, createdAt = 3L))
        outbox.insert(row("b1", farmB, SyncState.REJECTED, createdAt = 4L))

        val counts = outbox.countByStateForFarm(farmA).associate { it.state to it.count }
        assertEquals(mapOf("REJECTED" to 2L, "ACKNOWLEDGED" to 1L), counts)
    }

    private fun row(id: String, farmId: String, state: SyncState, createdAt: Long) = OutboxEntity(
        mutationId = id,
        farmId = farmId,
        actorId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
        deviceId = "device-a",
        commandName = "goat.weight.v1",
        commandSchemaVersion = 1,
        aggregateType = "animal",
        aggregateId = "agg-$id",
        aggregateOrdinal = 0,
        expectedStreamVersion = null,
        payloadJson = "{}",
        occurredAtEpochMillis = createdAt,
        createdAtEpochMillis = createdAt,
        state = state.name,
        attemptCount = 1,
        nextAttemptAtEpochMillis = null,
        lastErrorCode = null,
        serverEventId = null,
        serverStreamVersion = null,
    )
}
