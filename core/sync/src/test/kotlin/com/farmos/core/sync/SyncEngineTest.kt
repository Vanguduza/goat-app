package com.farmos.core.sync

import com.farmos.core.database.AggregateVersionDao
import com.farmos.core.database.AggregateVersionEntity
import com.farmos.core.database.OutboxDao
import com.farmos.core.database.OutboxEntity
import com.farmos.core.model.CommandAcknowledgement
import com.farmos.core.model.CommandResultCode
import com.farmos.core.model.SyncState
import com.farmos.core.network.AuthenticationRequiredException
import com.farmos.core.network.CommandTransport
import com.farmos.core.network.WireCommand
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SyncEngineTest {
    private val fixedNow = 1_700_000_000_000L

    @Test
    fun `accepted commands advance stream versions and preserve aggregate order`() = runSuspend {
        val outbox = FakeOutboxDao(
            mutableListOf(
                item(mutationId = "m1", aggregateOrdinal = 1, expectedStreamVersion = 0),
                item(mutationId = "m2", aggregateOrdinal = 2, expectedStreamVersion = 1),
            ),
        )
        val versions = FakeAggregateVersionDao()
        val observedVersions = mutableListOf<Long?>()
        var nextServerVersion = 1L
        val transport = object : CommandTransport {
            override suspend fun send(command: WireCommand): CommandAcknowledgement {
                observedVersions += command.expectedStreamVersion
                return CommandAcknowledgement(
                    code = CommandResultCode.ACCEPTED,
                    eventId = "event-${command.mutationId}",
                    streamVersion = nextServerVersion++,
                )
            }
        }

        val result = SyncEngine(outbox, versions, transport, now = { fixedNow }).drain()

        assertEquals(2, result.acknowledged)
        assertEquals(listOf(0L, 1L), observedVersions)
        assertEquals(SyncState.ACKNOWLEDGED.name, outbox.byId("m1").state)
        assertEquals(SyncState.ACKNOWLEDGED.name, outbox.byId("m2").state)
        assertEquals(2L, versions.getVersion("farm-1", "animal", "goat-1"))
    }

    @Test
    fun `authentication pause does not consume retry budget`() = runSuspend {
        val outbox = FakeOutboxDao(mutableListOf(item(mutationId = "auth", attemptCount = 7)))
        val transport = object : CommandTransport {
            override suspend fun send(command: WireCommand): CommandAcknowledgement {
                throw AuthenticationRequiredException("sign in")
            }
        }

        val result = SyncEngine(outbox, FakeAggregateVersionDao(), transport, now = { fixedNow }).drain()
        val saved = outbox.byId("auth")

        assertEquals(1, result.retrying)
        assertEquals(7, saved.attemptCount)
        assertEquals(SyncState.RETRY_WAIT.name, saved.state)
        assertEquals("AUTH_SESSION_REQUIRED", saved.lastErrorCode)
        assertEquals(fixedNow + 60_000L, saved.nextAttemptAtEpochMillis)
    }

    @Test
    fun `transient transport failure never dead letters valid offline work`() = runSuspend {
        val outbox = FakeOutboxDao(mutableListOf(item(mutationId = "network", attemptCount = 99)))
        val transport = object : CommandTransport {
            override suspend fun send(command: WireCommand): CommandAcknowledgement {
                error("network down")
            }
        }

        SyncEngine(outbox, FakeAggregateVersionDao(), transport, now = { fixedNow }).drain()
        val saved = outbox.byId("network")

        assertEquals(SyncState.RETRY_WAIT.name, saved.state)
        assertEquals(100, saved.attemptCount)
        assertEquals("TRANSPORT_FAILURE", saved.lastErrorCode)
    }

    @Test
    fun `conflict remains visible and blocks later command for same aggregate`() = runSuspend {
        val outbox = FakeOutboxDao(
            mutableListOf(
                item(mutationId = "first", aggregateOrdinal = 1, expectedStreamVersion = 0),
                item(mutationId = "second", aggregateOrdinal = 2, expectedStreamVersion = 1),
            ),
        )
        val sent = mutableListOf<String>()
        val transport = object : CommandTransport {
            override suspend fun send(command: WireCommand): CommandAcknowledgement {
                sent += command.mutationId
                return CommandAcknowledgement(code = CommandResultCode.CONFLICT, streamVersion = 4)
            }
        }

        val result = SyncEngine(outbox, FakeAggregateVersionDao(), transport, now = { fixedNow }).drain()

        assertEquals(listOf("first"), sent)
        assertEquals(1, result.conflicts)
        assertEquals(SyncState.CONFLICT.name, outbox.byId("first").state)
        assertEquals(SyncState.PENDING.name, outbox.byId("second").state)
    }

    private fun item(
        mutationId: String,
        aggregateOrdinal: Long = 1,
        expectedStreamVersion: Long? = 0,
        attemptCount: Int = 0,
    ) = OutboxEntity(
        mutationId = mutationId,
        farmId = "farm-1",
        actorId = "user-1",
        deviceId = "device-1",
        commandName = "goat.register.v1",
        commandSchemaVersion = 1,
        aggregateType = "animal",
        aggregateId = "goat-1",
        aggregateOrdinal = aggregateOrdinal,
        expectedStreamVersion = expectedStreamVersion,
        payloadJson = "{}",
        occurredAtEpochMillis = fixedNow,
        createdAtEpochMillis = fixedNow,
        state = SyncState.PENDING.name,
        attemptCount = attemptCount,
        nextAttemptAtEpochMillis = null,
        lastErrorCode = null,
        serverEventId = null,
        serverStreamVersion = null,
    )
}

private class FakeOutboxDao(
    private val items: MutableList<OutboxEntity>,
) : OutboxDao {
    override suspend fun insert(item: OutboxEntity) {
        items += item
    }

    override suspend fun pending(now: Long, limit: Int): List<OutboxEntity> =
        items
            .filter { candidate ->
                candidate.state in setOf(
                    SyncState.PENDING.name,
                    SyncState.RETRY_WAIT.name,
                    SyncState.IN_FLIGHT.name,
                ) &&
                    (candidate.nextAttemptAtEpochMillis == null || candidate.nextAttemptAtEpochMillis <= now) &&
                    items.none { blocker ->
                        blocker.farmId == candidate.farmId &&
                            blocker.aggregateType == candidate.aggregateType &&
                            blocker.aggregateId == candidate.aggregateId &&
                            blocker.aggregateOrdinal < candidate.aggregateOrdinal &&
                            blocker.state != SyncState.ACKNOWLEDGED.name
                    }
            }
            .sortedWith(compareBy<OutboxEntity> { it.aggregateOrdinal }.thenBy { it.mutationId })
            .take(limit)

    override suspend fun nextAggregateOrdinal(
        farmId: String,
        aggregateType: String,
        aggregateId: String,
    ): Long = items
        .filter { it.farmId == farmId && it.aggregateType == aggregateType && it.aggregateId == aggregateId }
        .maxOfOrNull { it.aggregateOrdinal }
        ?.plus(1)
        ?: 1L

    override suspend fun countUnacknowledgedForAggregate(
        farmId: String,
        aggregateType: String,
        aggregateId: String,
    ): Long = items.count {
        it.farmId == farmId &&
            it.aggregateType == aggregateType &&
            it.aggregateId == aggregateId &&
            it.state != SyncState.ACKNOWLEDGED.name
    }.toLong()

    override suspend fun updateState(
        mutationId: String,
        state: String,
        attemptCount: Int,
        nextAttemptAt: Long?,
        errorCode: String?,
        serverEventId: String?,
        serverStreamVersion: Long?,
    ) {
        val index = items.indexOfFirst { it.mutationId == mutationId }
        require(index >= 0)
        items[index] = items[index].copy(
            state = state,
            attemptCount = attemptCount,
            nextAttemptAtEpochMillis = nextAttemptAt,
            lastErrorCode = errorCode,
            serverEventId = serverEventId,
            serverStreamVersion = serverStreamVersion,
        )
    }

    override suspend fun hasPending(farmId: String, aggregateId: String): Boolean =
        items.any {
            it.farmId == farmId &&
                it.aggregateId == aggregateId &&
                it.state != SyncState.ACKNOWLEDGED.name
        }

    fun byId(mutationId: String): OutboxEntity = items.single { it.mutationId == mutationId }
}

private class FakeAggregateVersionDao : AggregateVersionDao {
    private val versions = linkedMapOf<Triple<String, String, String>, AggregateVersionEntity>()

    override suspend fun getVersion(farmId: String, aggregateType: String, aggregateId: String): Long? =
        versions[Triple(farmId, aggregateType, aggregateId)]?.streamVersion

    override suspend fun insertIfMissing(entity: AggregateVersionEntity): Long {
        val key = Triple(entity.farmId, entity.aggregateType, entity.aggregateId)
        if (versions.containsKey(key)) return -1L
        versions[key] = entity
        return 1L
    }

    override suspend fun advanceExisting(
        farmId: String,
        aggregateType: String,
        aggregateId: String,
        streamVersion: Long,
        updatedAtEpochMillis: Long,
    ) {
        val key = Triple(farmId, aggregateType, aggregateId)
        val current = versions[key] ?: return
        if (current.streamVersion < streamVersion) {
            versions[key] = current.copy(
                streamVersion = streamVersion,
                updatedAtEpochMillis = updatedAtEpochMillis,
            )
        }
    }
}

private fun <T> runSuspend(block: suspend () -> T): T {
    var outcome: Result<T>? = null
    block.startCoroutine(
        object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<T>) {
                outcome = result
            }
        },
    )
    return outcome?.getOrThrow() ?: error("Test coroutine did not complete synchronously")
}
