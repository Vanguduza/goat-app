package com.farmos.data.herd

import androidx.room.withTransaction
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.FarmWorkerEntity
import com.farmos.core.database.journalLocalOperation
import com.farmos.core.model.LocalCommandContext
import com.farmos.core.model.LocalCommandResult
import com.farmos.domain.ops.CreateFarmWorker
import com.farmos.domain.ops.UpdateFarmWorker
import com.farmos.domain.ops.WorkerRules
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The farm's worker register (D-016, resolution R1): governed master data, journalled for farm
 * replication. A worker is never deleted; renames and status changes merge by later business time.
 */
class WorkerRegisterCommands(
    private val database: FarmOsDatabase,
    private val farmId: String,
    /** Replays an operation received from another device: domain writes only, it is already journalled. */
    private val replaying: Boolean = false,
    private val json: Json = Json { encodeDefaults = true },
) {
    suspend fun create(command: CreateFarmWorker, context: LocalCommandContext): LocalCommandResult {
        WorkerRules.name(command.name)?.let { error(it) }
        journal(context, CREATE, command.workerId, json.encodeToString(command)) {
            if (database.workers().get(farmId, command.workerId) == null) {
                database.workers().upsert(FarmWorkerEntity(command.workerId, farmId, command.name.trim(), true, context.occurredAtEpochMillis, context.actorId))
            }
        }
        return LocalCommandResult(context.mutationId, command.workerId, true)
    }

    suspend fun update(command: UpdateFarmWorker, context: LocalCommandContext): LocalCommandResult {
        WorkerRules.update(command)?.let { error(it) }
        requireNotNull(database.workers().get(farmId, command.workerId)) { "Worker not found" }
        journal(context, UPDATE, command.workerId, json.encodeToString(command)) {
            val current = requireNotNull(database.workers().get(farmId, command.workerId)) { "Worker not found" }
            // The later change by business time wins, whatever order changes arrive in.
            if (current.updatedAtEpochMillis <= context.occurredAtEpochMillis) {
                database.workers().upsert(
                    current.copy(
                        name = command.name?.trim() ?: current.name,
                        active = command.active ?: current.active,
                        updatedAtEpochMillis = context.occurredAtEpochMillis,
                        updatedByActorId = context.actorId,
                    ),
                )
            }
        }
        return LocalCommandResult(context.mutationId, command.workerId, true)
    }

    private suspend fun journal(context: LocalCommandContext, commandName: String, workerId: String, payloadJson: String, localWrite: suspend () -> Unit) {
        require(context.farmId == farmId) { "Farm context mismatch" }
        if (replaying) return database.withTransaction { localWrite() }
        database.withTransaction {
            localWrite()
            database.journalLocalOperation(
                operationId = context.mutationId,
                farmId = farmId,
                entityType = "farm_worker",
                entityId = workerId,
                actorId = context.actorId,
                deviceId = context.deviceId,
                businessTimeEpochMillis = context.occurredAtEpochMillis,
                createdAtEpochMillis = System.currentTimeMillis(),
                baseVersion = null,
                operationType = commandName,
                payloadJson = payloadJson,
                schemaVersion = 1,
            )
        }
    }

    companion object {
        const val CREATE = "worker.create.v1"
        const val UPDATE = "worker.update.v1"
    }
}
