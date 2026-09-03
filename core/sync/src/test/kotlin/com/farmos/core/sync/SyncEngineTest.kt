package com.farmos.core.sync

import com.farmos.core.database.AggregateVersionDao
import com.farmos.core.database.AggregateVersionEntity
import com.farmos.core.database.OutboxDao
import com.farmos.core.database.OutboxEntity
import com.farmos.core.model.CommandAcknowledgement
import com.farmos.core.model.CommandResultCode
import com.farmos.core.model.SyncState
import com.farmos.core.network.AuthenticationRequiredException
import com.farmos.core.network.AuthorizationLoss
import com.farmos.core.network.CommandTransport
import com.farmos.core.network.WireCommand
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals

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

        val result = SyncEngine(outbox, versions, transport, now = { fixedNow }).drain(workName = "foreground-test")

        assertEquals(2, result.acknowledged)
        assertEquals(listOf<Long?>(0L, 1L), observedVersions)
        assertEquals(SyncState.ACKNOWLEDGED.name, outbox.byId("m1").state)
        assertEquals(SyncState.ACKNOWLEDGED.name, outbox.byId("m2").state)
        assertEquals(2L, versions.getVersion("farm-1", "animal", "goat-1"))
        assertNull(result.authorizationLoss)
        assertEquals(listOf("m1", "m2"), result.traces.map { it.mutationId })
        assertEquals("goat_register_v1", result.traces.first().rpc)
        assertEquals("foreground-test", result.traces.first().workName)
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
        assertEquals(1, result.sessionRequired)
        assertEquals(AuthorizationLoss.SESSION_EXPIRED, result.authorizationLoss)
        assertEquals(7, saved.attemptCount)
        assertEquals(SyncState.RETRY_WAIT.name, saved.state)
        assertEquals("AUTH_SESSION_REQUIRED", saved.lastErrorCode)
        assertEquals(fixedNow + 60_000L, saved.nextAttemptAtEpochMillis)
    }

    @Test
    fun `revoked farm response rejects pending work without retry loop`() = runSuspend {
        val outbox = FakeOutboxDao(mutableListOf(item(mutationId = "revoked", attemptCount = 2)))
        val transport = object : CommandTransport {
            override suspend fun send(command: WireCommand): CommandAcknowledgement =
                CommandAcknowledgement(
                    code = CommandResultCode.AUTH_REJECTED,
                    safeMessage = "Farm access denied",
                )
        }

        val result = SyncEngine(outbox, FakeAggregateVersionDao(), transport, now = { fixedNow }).drain()
        val saved = outbox.byId("revoked")

        assertEquals(1, result.rejected)
        assertEquals(1, result.authRejected)
        assertEquals(0, result.retrying)
        assertEquals(AuthorizationLoss.FARM_ACCESS_REVOKED, result.authorizationLoss)
        assertEquals(SyncState.REJECTED.name, saved.state)
        assertEquals(3, saved.attemptCount)
        assertEquals("AUTH_REJECTED", saved.lastErrorCode)
        val trace = result.traces.single()
        assertEquals("revoked", trace.mutationId)
        assertEquals("farm-1", trace.farmId)
        assertEquals("animal", trace.aggregateType)
        assertEquals("goat-1", trace.aggregateId)
        assertEquals(1L, trace.aggregateOrdinal)
        assertEquals("goat_register_v1", trace.rpc)
        assertEquals("AUTH_REJECTED", trace.result)
        assertEquals("AUTH_REJECTED", trace.rejectionClass)
        assertEquals(3, trace.retryCount)
        assertTrue(!trace.toStructuredLine().contains("token", ignoreCase = true))
        assertTrue(!trace.toStructuredLine().contains("password", ignoreCase = true))
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
    fun `lifecycle command maps to the versioned status rpc`() = runSuspend {
        val outbox = FakeOutboxDao(
            mutableListOf(
                item(mutationId = "status-1").copy(commandName = "goat.set_status.v1"),
            ),
        )
        val sent = mutableListOf<String>()
        val transport = object : CommandTransport {
            override suspend fun send(command: WireCommand): CommandAcknowledgement {
                sent += command.rpcName
                return CommandAcknowledgement(
                    code = CommandResultCode.ACCEPTED,
                    eventId = "event-status",
                    streamVersion = 2,
                )
            }
        }

        val result = SyncEngine(outbox, FakeAggregateVersionDao(), transport, now = { fixedNow }).drain()

        assertEquals(listOf("goat_set_status_v1"), sent)
        assertEquals(1, result.acknowledged)
        assertEquals("goat_set_status_v1", result.traces.single().rpc)
    }

    @Test
    fun `operating spine commands map to versioned rpcs`() = runSuspend {
        val names = listOf(
            "goat.record_kidding.v1" to "goat_record_kidding_v1",
            "rabbit.wave_create.v1" to "rabbit_wave_create_v1",
            "task.create.v1" to "task_create_v1",
            "health.record_observation.v1" to "health_record_observation_v1",
            "money.record.v1" to "money_record_v1",
            "inventory.move.v1" to "inventory_move_v1",
            "sheep.register.v1" to "sheep_register_v1",
            "sheep.record_joining.v1" to "sheep_record_joining_v1",
            "cattle.record_service.v1" to "cattle_record_service_v1",
            "rabbit.record_kindling.v1" to "rabbit_record_kindling_v1",
            "goat.record_milk.v1" to "goat_record_milk_v1",
            "purchase.record.v1" to "purchase_record_v1",
            "rabbit.nest_box_set_status.v1" to "rabbit_nest_box_set_status_v1",
            "cattle.record_bcs.v1" to "cattle_record_bcs_v1",
            "sheep.record_marking.v1" to "sheep_record_marking_v1",
            "goat.record_bcs.v1" to "goat_record_bcs_v1",
            "rabbit.waitlist_enqueue.v1" to "rabbit_waitlist_enqueue_v1",
            "cattle.record_scc.v1" to "cattle_record_scc_v1",
            "sheep.record_shearing.v1" to "sheep_record_shearing_v1",
            "poultry.hatch_set.v1" to "poultry_hatch_set_v1",
            "sheep.record_flystrike.v1" to "sheep_record_flystrike_v1",
            "rabbit.record_mating_outcome.v1" to "rabbit_record_mating_outcome_v1",
            "goat.record_scc.v1" to "goat_record_scc_v1",
            "rabbit.bedding_bind.v1" to "rabbit_bedding_bind_v1",
            "poultry.record_vaccination.v1" to "poultry_record_vaccination_v1",
            "cattle.record_dryoff.v1" to "cattle_record_dryoff_v1",
            "poultry.flock_place.v1" to "poultry_flock_place_v1",
            "poultry.record_biosecurity.v1" to "poultry_record_biosecurity_v1",
            "goat.record_heat.v1" to "goat_record_heat_v1",
            "goat.record_mating.v1" to "goat_record_mating_v1",
            "inventory.lot_issue.v1" to "inventory_lot_issue_v1",
            "pedigree.link.v1" to "pedigree_link_v1",
            "health.pack_apply.v1" to "health_pack_apply_v1",
            "cattle.record_dof.v1" to "cattle_record_dof_v1",
            "goat.plan_lactation.v1" to "goat_plan_lactation_v1",
            "goat.register_kid.v1" to "goat_register_kid_v1",
        )
        names.forEach { (command, rpc) ->
            val outbox = FakeOutboxDao(mutableListOf(item(mutationId = command).copy(commandName = command)))
            val sent = mutableListOf<String>()
            val transport = object : CommandTransport {
                override suspend fun send(wire: WireCommand): CommandAcknowledgement {
                    sent += wire.rpcName
                    return CommandAcknowledgement(code = CommandResultCode.ACCEPTED, eventId = "e", streamVersion = 1)
                }
            }
            SyncEngine(outbox, FakeAggregateVersionDao(), transport, now = { fixedNow }).drain()
            assertEquals(listOf(rpc), sent)
        }
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
                    (candidate.nextAttemptAtEpochMillis?.let { it <= now } != false) &&
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

    override suspend fun countUnacknowledgedForFarm(farmId: String): Long =
        items.count { it.farmId == farmId && it.state != SyncState.ACKNOWLEDGED.name }.toLong()

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
