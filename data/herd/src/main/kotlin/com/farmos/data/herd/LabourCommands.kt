package com.farmos.data.herd

import androidx.room.withTransaction
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.LabourEntryEntity
import com.farmos.core.database.journalLocalOperation
import com.farmos.core.model.LocalCommandContext
import com.farmos.core.model.LocalCommandResult
import com.farmos.domain.ops.OpsValidator
import com.farmos.domain.ops.RecordLabour
import com.farmos.domain.ops.RecordWorkerLabour
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Labour recorded against a worker in the register (resolution R1), journalled for farm replication. On
 * this device the worker must be active; a replayed entry keeps the worker it was recorded for, even if the
 * register change that made them inactive arrived first.
 */
class LabourCommands(
    private val database: FarmOsDatabase,
    private val farmId: String,
    /** Replay must match an immutable operation already admitted to this farm's journal. */
    private val replaying: Boolean = false,
    private val json: Json = Json { encodeDefaults = true },
) {
    suspend fun record(command: RecordWorkerLabour, context: LocalCommandContext): LocalCommandResult {
        require(context.farmId == farmId) { "Farm context mismatch" }
        OpsValidator.labour(RecordLabour(command.entryId, command.workerName, command.taskCode, command.minutes, command.occurredEpochDay, command.note))?.let { error(it) }
        database.withTransaction {
            val payloadJson = json.encodeToString(command)
            val permission = OpsCommandPermissions.requiredFor(RECORD, payloadJson)
            if (!replaying) database.requireLocalCommandAuthority(context, permission)
            val original = database.replication().operation(farmId, context.mutationId)
            if (original != null) {
                requireOriginalCommand(original, context, RECORD, "labour_entry", command.entryId, {
                    json.decodeFromString<RecordWorkerLabour>(it) == command
                })
                if (database.commandAlreadyApplied(original, replaying)) return@withTransaction
            } else {
                require(!replaying) { "A received change must be journalled before it is applied" }
            }
            if (!replaying) {
                val worker = database.workers().get(farmId, command.workerId)
                require(worker != null && worker.active) { "Choose an active worker on this farm" }
            }
            database.labour().insert(
                LabourEntryEntity(command.entryId, farmId, command.workerName.trim(), command.taskCode.trim(), command.minutes, command.occurredEpochDay, command.note, command.workerId),
            )
            if (replaying) return@withTransaction
            database.journalLocalOperation(
                operationId = context.mutationId,
                farmId = farmId,
                entityType = "labour_entry",
                entityId = command.entryId,
                actorId = context.actorId,
                deviceId = context.deviceId,
                businessTimeEpochMillis = context.occurredAtEpochMillis,
                createdAtEpochMillis = System.currentTimeMillis(),
                baseVersion = null,
                operationType = RECORD,
                payloadJson = payloadJson,
                schemaVersion = 1,
            )
        }
        return LocalCommandResult(context.mutationId, command.entryId, true)
    }

    companion object {
        const val RECORD = "labour.record.v2"
    }
}
