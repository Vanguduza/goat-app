package com.farmos.core.sync

import com.farmos.core.database.AggregateVersionDao
import com.farmos.core.database.OutboxDao
import com.farmos.core.database.OutboxEntity
import com.farmos.core.model.CommandResultCode
import com.farmos.core.model.SyncState
import com.farmos.core.network.AuthenticationRequiredException
import com.farmos.core.network.AuthorizationLoss
import com.farmos.core.network.CommandTransport
import com.farmos.core.network.WireCommand
import kotlinx.serialization.json.Json

class SyncEngine(
    private val outbox: OutboxDao,
    private val aggregateVersions: AggregateVersionDao,
    private val transport: CommandTransport,
    private val json: Json = Json,
    private val now: () -> Long = System::currentTimeMillis,
    private val observer: SyncObserver? = null,
) {
    suspend fun drain(limit: Int = 50, workName: String? = null): SyncRunResult {
        var attempted = 0
        var acknowledged = 0
        var conflicts = 0
        var rejected = 0
        var retrying = 0
        var authRejected = 0
        var sessionRequired = 0
        val traces = mutableListOf<SyncMutationTrace>()

        while (attempted < limit) {
            val items = outbox.pending(now(), limit - attempted)
            if (items.isEmpty()) break

            for (item in items) {
                attempted++
                outbox.updateState(
                    mutationId = item.mutationId,
                    state = SyncState.IN_FLIGHT.name,
                    attemptCount = item.attemptCount,
                    nextAttemptAt = null,
                    errorCode = null,
                    serverEventId = item.serverEventId,
                    serverStreamVersion = item.serverStreamVersion,
                )

                try {
                    val wire = item.toWireCommand(json)
                    val acknowledgement = transport.send(wire)
                    when (acknowledgement.code) {
                        CommandResultCode.ACCEPTED,
                        CommandResultCode.ALREADY_APPLIED -> {
                            acknowledged++
                            acknowledgement.streamVersion?.let { streamVersion ->
                                aggregateVersions.advance(
                                    farmId = item.farmId,
                                    aggregateType = item.aggregateType,
                                    aggregateId = item.aggregateId,
                                    streamVersion = streamVersion,
                                    updatedAtEpochMillis = now(),
                                )
                            }
                            outbox.updateState(
                                item.mutationId,
                                SyncState.ACKNOWLEDGED.name,
                                item.attemptCount + 1,
                                null,
                                null,
                                acknowledgement.eventId,
                                acknowledgement.streamVersion,
                            )
                            traces += item.trace(
                                rpc = wire.rpcName,
                                result = acknowledgement.code.name,
                                rejectionClass = null,
                                retryCount = item.attemptCount + 1,
                                workName = workName,
                                recordedAtEpochMillis = now(),
                            )
                        }
                        CommandResultCode.CONFLICT -> {
                            conflicts++
                            outbox.updateState(
                                item.mutationId,
                                SyncState.CONFLICT.name,
                                item.attemptCount + 1,
                                null,
                                "CONFLICT",
                                acknowledgement.eventId,
                                acknowledgement.streamVersion,
                            )
                            traces += item.trace(
                                rpc = wire.rpcName,
                                result = acknowledgement.code.name,
                                rejectionClass = "CONFLICT",
                                retryCount = item.attemptCount + 1,
                                workName = workName,
                                recordedAtEpochMillis = now(),
                            )
                        }
                        CommandResultCode.VALIDATION_REJECTED,
                        CommandResultCode.AUTH_REJECTED,
                        CommandResultCode.STALE_CLIENT -> {
                            rejected++
                            if (acknowledgement.code == CommandResultCode.AUTH_REJECTED) {
                                authRejected++
                            }
                            outbox.updateState(
                                item.mutationId,
                                SyncState.REJECTED.name,
                                item.attemptCount + 1,
                                null,
                                acknowledgement.code.name,
                                acknowledgement.eventId,
                                acknowledgement.streamVersion,
                            )
                            traces += item.trace(
                                rpc = wire.rpcName,
                                result = acknowledgement.code.name,
                                rejectionClass = acknowledgement.code.name,
                                retryCount = item.attemptCount + 1,
                                workName = workName,
                                recordedAtEpochMillis = now(),
                            )
                        }
                        CommandResultCode.TEMPORARY_FAILURE -> {
                            retrying++
                            scheduleRetry(item, "TEMPORARY_FAILURE")
                            traces += item.trace(
                                rpc = wire.rpcName,
                                result = acknowledgement.code.name,
                                rejectionClass = "TEMPORARY_FAILURE",
                                retryCount = item.attemptCount + 1,
                                workName = workName,
                                recordedAtEpochMillis = now(),
                            )
                        }
                    }
                } catch (_: AuthenticationRequiredException) {
                    retrying++
                    sessionRequired++
                    pauseForAuthentication(item)
                    traces += item.trace(
                        rpc = item.rpcName(),
                        result = "AUTH_SESSION_REQUIRED",
                        rejectionClass = "AUTH_SESSION_REQUIRED",
                        retryCount = item.attemptCount,
                        workName = workName,
                        recordedAtEpochMillis = now(),
                    )
                } catch (_: Exception) {
                    retrying++
                    scheduleRetry(item, "TRANSPORT_FAILURE")
                    traces += item.trace(
                        rpc = item.rpcName(),
                        result = "TRANSPORT_FAILURE",
                        rejectionClass = "TRANSPORT_FAILURE",
                        retryCount = item.attemptCount + 1,
                        workName = workName,
                        recordedAtEpochMillis = now(),
                    )
                }
            }
        }

        traces.forEach { observer?.onMutation(it) }
        return SyncRunResult(
            attempted = attempted,
            acknowledged = acknowledged,
            conflicts = conflicts,
            rejected = rejected,
            retrying = retrying,
            authRejected = authRejected,
            sessionRequired = sessionRequired,
            traces = traces,
        )
    }

    private suspend fun pauseForAuthentication(item: OutboxEntity) {
        outbox.updateState(
            item.mutationId,
            SyncState.RETRY_WAIT.name,
            item.attemptCount,
            now() + AUTH_RETRY_DELAY_MILLIS,
            "AUTH_SESSION_REQUIRED",
            item.serverEventId,
            item.serverStreamVersion,
        )
    }

    private suspend fun scheduleRetry(item: OutboxEntity, code: String) {
        val attempts = item.attemptCount + 1
        val exponent = attempts.coerceIn(1, MAX_BACKOFF_EXPONENT)
        val delayMs = (1L shl exponent) * 1_000L
        outbox.updateState(
            item.mutationId,
            SyncState.RETRY_WAIT.name,
            attempts,
            now() + delayMs,
            code,
            item.serverEventId,
            item.serverStreamVersion,
        )
    }

    companion object {
        private const val AUTH_RETRY_DELAY_MILLIS = 60_000L
        private const val MAX_BACKOFF_EXPONENT = 8
    }
}

private fun OutboxEntity.rpcName(): String {
    val rpc = commandName.replace('.', '_')
    require(commandName in SUPPORTED_COMMANDS) { "Unsupported command: $commandName" }
    return rpc
}

private val SUPPORTED_COMMANDS = setOf(
    "goat.register.v1",
    "goat.record_weight.v1",
    "goat.set_status.v1",
    "goat.record_kidding.v1",
    "goat.record_famacha.v1",
    "goat.record_milk.v1",
    "goat.record_bcs.v1",
    "goat.record_scc.v1",
    "poultry.house_create.v1",
    "poultry.hatch_set.v1",
    "poultry.hatch_candle.v1",
    "poultry.hatch_record.v1",
    "sheep.record_flystrike.v1",
    "rabbit.record_mating_outcome.v1",
    "rabbit.bedding_bind.v1",
    "poultry.record_vaccination.v1",
    "cattle.record_dryoff.v1",
    "poultry.flock_place.v1",
    "poultry.record_biosecurity.v1",
    "goat.record_heat.v1",
    "goat.record_mating.v1",
    "goat.record_pregnancy.v1",
    "sheep.record_famacha.v1",
    "animal.identifier_assign.v1",
    "official.record_movement.v1",
    "inventory.lot_receive.v1",
    "inventory.lot_issue.v1",
    "health.record_vet_visit.v1",
    "health.record_lab.v1",
    "cattle.record_weaning.v1",
    "sheep.record_micron.v1",
    "poultry.kind_enable.v1",
    "rabbit.record_gi_stasis.v1",
    "pedigree.link.v1",
    "health.pack_slot_add.v1",
    "health.pack_apply.v1",
    "cattle.lot_place.v1",
    "cattle.record_dof.v1",
    "cattle.lot_close.v1",
    "goat.plan_lactation.v1",
    "inventory.set_reorder.v1",
    "inventory.record_reorder.v1",
    "group.census.v1",
    "goat.register_kid.v1",
    "rabbit.kit_register.v1",
    "rabbit.kit_promote.v1",
    "rabbit.retention_decide.v1",
    "rabbit.waitlist_enqueue.v1",
    "rabbit.waitlist_fulfill.v1",
    "rabbit.contract_agree.v1",
    "rabbit.market_plan.v1",
    "cattle.record_locomotion.v1",
    "cattle.record_scc.v1",
    "sheep.record_shearing.v1",
    "sheep.record_joining.v1",
    "sheep.record_scan.v1",
    "sheep.record_lambing.v1",
    "cattle.record_service.v1",
    "cattle.record_pd.v1",
    "cattle.record_calving.v1",
    "rabbit.record_palpation.v1",
    "rabbit.record_kindling.v1",
    "rabbit.record_foster.v1",
    "supplier.create.v1",
    "purchase.record.v1",
    "health.pack_accept.v1",
    "rabbit.nest_box_set_status.v1",
    "rabbit.record_wean.v1",
    "sheep.record_marking.v1",
    "sheep.record_weaning.v1",
    "cattle.record_bcs.v1",
    "sheep.record_wool.v1",
    "cattle.record_milk.v1",
    "sheep.record_dag.v1",
    "sheep.record_footrot.v1",
    "group.create.v1",
    "paddock.create.v1",
    "grazing.start.v1",
    "grazing.end.v1",
    "labour.record.v1",
    "asset.create.v1",
    "maintenance.record.v1",
    "feed.issue.v1",
    "water.record.v1",
    "sale.record.v1",
    "formulary.item_create.v1",
    "health.record_treatment.v1",
    "poultry.flock_day.v1",
    "rabbit.register.v1",
    "rabbit.record_weight.v1",
    "rabbit.set_status.v1",
    "rabbit.cage_create.v1",
    "rabbit.nest_box_create.v1",
    "rabbit.wave_create.v1",
    "sheep.register.v1",
    "sheep.record_weight.v1",
    "sheep.set_status.v1",
    "cattle.register.v1",
    "cattle.record_weight.v1",
    "cattle.set_status.v1",
    "poultry.register.v1",
    "poultry.record_weight.v1",
    "poultry.set_status.v1",
    "task.create.v1",
    "task.complete.v1",
    "health.record_observation.v1",
    "money.record.v1",
    "inventory.item_create.v1",
    "inventory.move.v1",
)

private fun OutboxEntity.toWireCommand(json: Json): WireCommand = WireCommand(
    rpcName = rpcName(),
    mutationId = mutationId,
    farmId = farmId,
    actorId = actorId,
    deviceId = deviceId,
    expectedStreamVersion = expectedStreamVersion,
    occurredAtEpochMillis = occurredAtEpochMillis,
    payload = json.parseToJsonElement(payloadJson),
)

private fun OutboxEntity.trace(
    rpc: String,
    result: String,
    rejectionClass: String?,
    retryCount: Int,
    workName: String?,
    recordedAtEpochMillis: Long,
) = SyncMutationTrace(
    mutationId = mutationId,
    farmId = farmId,
    aggregateType = aggregateType,
    aggregateId = aggregateId,
    aggregateOrdinal = aggregateOrdinal,
    rpc = rpc,
    result = result,
    rejectionClass = rejectionClass,
    retryCount = retryCount,
    workName = workName,
    recordedAtEpochMillis = recordedAtEpochMillis,
)

data class SyncRunResult(
    val attempted: Int,
    val acknowledged: Int,
    val conflicts: Int,
    val rejected: Int,
    val retrying: Int,
    val authRejected: Int = 0,
    val sessionRequired: Int = 0,
    val traces: List<SyncMutationTrace> = emptyList(),
) {
    val authorizationLoss: AuthorizationLoss?
        get() = when {
            authRejected > 0 -> AuthorizationLoss.FARM_ACCESS_REVOKED
            sessionRequired > 0 -> AuthorizationLoss.SESSION_EXPIRED
            else -> null
        }
}
