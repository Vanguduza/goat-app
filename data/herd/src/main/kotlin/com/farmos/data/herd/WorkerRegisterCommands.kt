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
import kotlinx.serialization.decodeFromString
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
        journal(context, CREATE, command.workerId, command) {
            if (database.workers().get(farmId, command.workerId) == null) {
                database.workers().upsert(FarmWorkerEntity(command.workerId, farmId, command.name.trim(), true, context.occurredAtEpochMillis, context.actorId))
            }
        }
        return LocalCommandResult(context.mutationId, command.workerId, true)
    }

    suspend fun update(command: UpdateFarmWorker, context: LocalCommandContext): LocalCommandResult {
        WorkerRules.update(command)?.let { error(it) }
        journal(context, UPDATE, command.workerId, command) {
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

    private suspend inline fun <reified C> journal(context: LocalCommandContext, commandName: String, workerId: String, command: C, noinline localWrite: suspend () -> Unit) {
        require(context.farmId == farmId) { "Farm context mismatch" }
        database.withTransaction {
            val payloadJson = json.encodeToString(command)
            val permission = OpsCommandPermissions.requiredFor(commandName, payloadJson)
            if (!replaying) database.requireLocalCommandAuthority(context, permission)
            val original = database.replication().operation(farmId, context.mutationId)
            if (original != null) {
                requireOriginalCommand(original, context, commandName, "farm_worker", workerId, {
                    json.decodeFromString<C>(it) == command
                })
                if (database.commandAlreadyApplied(original, replaying)) return@withTransaction
            } else {
                require(!replaying) { "A received change must be journalled before it is applied" }
            }
            localWrite()
            if (replaying) return@withTransaction
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
