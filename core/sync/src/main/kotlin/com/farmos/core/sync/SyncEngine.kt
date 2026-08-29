package com.farmos.core.sync

import com.farmos.core.database.OutboxDao
import com.farmos.core.database.OutboxEntity
import com.farmos.core.model.CommandResultCode
import com.farmos.core.model.SyncState
import com.farmos.core.network.CommandTransport
import com.farmos.core.network.WireCommand
import kotlinx.serialization.json.Json

class SyncEngine(
    private val outbox: OutboxDao,
    private val transport: CommandTransport,
    private val json: Json = Json,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun drain(limit: Int = 50): SyncRunResult {
        val items = outbox.pending(now(), limit)
        var acknowledged = 0
        var conflicts = 0
        var rejected = 0
        var retrying = 0

        for (item in items) {
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
            } catch (_: Exception) {
                retrying++
                scheduleRetry(item, "TRANSPORT_FAILURE")
            }
        }

        return SyncRunResult(items.size, acknowledged, conflicts, rejected, retrying)
    }

    private suspend fun scheduleRetry(item: OutboxEntity, code: String) {
        val attempts = item.attemptCount + 1
        val maxAttempts = 12
        if (attempts >= maxAttempts) {
            outbox.updateState(
                item.mutationId,
                SyncState.DEAD_LETTER.name,
                attempts,
                null,
                code,
                item.serverEventId,
                item.serverStreamVersion,
            )
            return
        }
        val delayMs = (1L shl attempts.coerceAtMost(8)) * 1_000L
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
