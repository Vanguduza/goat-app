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

private fun OutboxEntity.rpcName(): String = when (commandName) {
    "goat.register.v1" -> "goat_register_v1"
    "goat.record_weight.v1" -> "goat_record_weight_v1"
    else -> error("Unsupported command: $commandName")
}

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
