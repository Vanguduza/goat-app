package com.farmos.core.sync

import com.farmos.core.database.AggregateVersionDao
import com.farmos.core.database.OutboxDao
import com.farmos.core.database.OutboxEntity
import com.farmos.core.model.CommandResultCode
import com.farmos.core.model.SyncState
import com.farmos.core.network.AuthenticationRequiredException
import com.farmos.core.network.CommandTransport
import com.farmos.core.network.WireCommand
import kotlinx.serialization.json.Json

class SyncEngine(
    private val outbox: OutboxDao,
    private val aggregateVersions: AggregateVersionDao,
    private val transport: CommandTransport,
    private val json: Json = Json,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun drain(limit: Int = 50): SyncRunResult {
        var attempted = 0
        var acknowledged = 0
        var conflicts = 0
        var rejected = 0
        var retrying = 0

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
                    val acknowledgement = transport.send(item.toWireCommand(json))
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
                        }
                        CommandResultCode.VALIDATION_REJECTED,
                        CommandResultCode.AUTH_REJECTED,
                        CommandResultCode.STALE_CLIENT -> {
                            rejected++
                            outbox.updateState(
                                item.mutationId,
                                SyncState.REJECTED.name,
                                item.attemptCount + 1,
                                null,
                                acknowledgement.code.name,
                                acknowledgement.eventId,
                                acknowledgement.streamVersion,
                            )
                        }
                        CommandResultCode.TEMPORARY_FAILURE -> {
                            retrying++
                            scheduleRetry(item, "TEMPORARY_FAILURE")
                        }
                    }
                } catch (_: AuthenticationRequiredException) {
                    retrying++
                    pauseForAuthentication(item)
                } catch (_: Exception) {
                    retrying++
                    scheduleRetry(item, "TRANSPORT_FAILURE")
                }
            }
        }

        return SyncRunResult(attempted, acknowledged, conflicts, rejected, retrying)
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

private fun OutboxEntity.toWireCommand(json: Json): WireCommand = WireCommand(
    rpcName = when (commandName) {
        "goat.register.v1" -> "goat_register_v1"
        "goat.record_weight.v1" -> "goat_record_weight_v1"
        else -> error("Unsupported command: $commandName")
    },
    mutationId = mutationId,
    farmId = farmId,
    actorId = actorId,
    deviceId = deviceId,
    expectedStreamVersion = expectedStreamVersion,
    occurredAtEpochMillis = occurredAtEpochMillis,
    payload = json.parseToJsonElement(payloadJson),
)

data class SyncRunResult(
    val attempted: Int,
    val acknowledged: Int,
    val conflicts: Int,
    val rejected: Int,
    val retrying: Int,
)
