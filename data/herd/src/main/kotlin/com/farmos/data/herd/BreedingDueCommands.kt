package com.farmos.data.herd

import androidx.room.withTransaction
import com.farmos.core.database.CattleServiceEntity
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.SheepJoiningEntity
import com.farmos.core.database.TaskEntity
import com.farmos.core.database.journalLocalOperation
import com.farmos.core.model.LocalCommandContext
import com.farmos.core.model.LocalCommandResult
import com.farmos.domain.ops.BreedingDueSchedule
import com.farmos.domain.ops.RecordCattleServiceV2
import com.farmos.domain.ops.RecordSheepJoiningV2
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** The service and its generated work; shared by the v1 (fixed-day) and v2 (carried-day) commands. */
internal suspend fun FarmOsDatabase.writeCattleService(
    farmId: String,
    serviceId: String,
    animalId: String,
    method: String,
    serviceEpochDay: Long,
    expectedCalvingEpochDay: Long,
    pdTaskId: String,
    paddockTaskId: String,
    calvingTaskId: String,
    createdAtEpochMillis: Long,
) {
    lifecycle().insertService(CattleServiceEntity(serviceId, farmId, animalId, method, serviceEpochDay))
    val pdDay = serviceEpochDay + BreedingDueSchedule.CATTLE_PD_DAYS_AFTER_SERVICE
    val paddockDay = expectedCalvingEpochDay - BreedingDueSchedule.CATTLE_PADDOCK_DAYS_BEFORE_CALVING
    tasks().insert(TaskEntity(pdTaskId, farmId, "cattle", "PD", "Pregnancy diagnosis (PD)", pdDay, "open", animalId, null, null, createdAtEpochMillis))
    tasks().insert(TaskEntity(paddockTaskId, farmId, "cattle", "CALVING_PADDOCK", "Calving paddock / close-up pen", paddockDay, "open", animalId, null, null, createdAtEpochMillis))
    tasks().insert(TaskEntity(calvingTaskId, farmId, "cattle", "EXPECTED_CALVING", "Expected calving", expectedCalvingEpochDay, "open", animalId, null, null, createdAtEpochMillis))
}

/** The joining and its generated work; shared by the v1 (fixed-day) and v2 (carried-day) commands. */
internal suspend fun FarmOsDatabase.writeSheepJoining(
    farmId: String,
    joiningId: String,
    groupId: String,
    startedEpochDay: Long,
    expectedLambingEpochDay: Long,
    scanTaskId: String,
    preLambTaskId: String,
    paddockTaskId: String,
    lambingTaskId: String,
    createdAtEpochMillis: Long,
) {
    lifecycle().insertJoining(SheepJoiningEntity(joiningId, farmId, groupId, startedEpochDay))
    val scanDay = startedEpochDay + BreedingDueSchedule.SHEEP_SCAN_DAYS_AFTER_JOINING
    val preLambDay = expectedLambingEpochDay - BreedingDueSchedule.SHEEP_PRE_LAMB_DAYS_BEFORE_LAMBING
    tasks().insert(TaskEntity(scanTaskId, farmId, "sheep", "SCAN", "Pregnancy scanning", scanDay, "open", null, null, null, createdAtEpochMillis))
    tasks().insert(TaskEntity(preLambTaskId, farmId, "sheep", "PRE_LAMB", "Pre-lambing vaccination / nutrition", preLambDay, "open", null, null, null, createdAtEpochMillis))
    tasks().insert(TaskEntity(paddockTaskId, farmId, "sheep", "LAMBING_PADDOCK", "Lambing paddock set-up", preLambDay, "open", null, null, null, createdAtEpochMillis))
    tasks().insert(TaskEntity(lambingTaskId, farmId, "sheep", "EXPECTED_LAMBING", "Expected lambing start", expectedLambingEpochDay, "open", null, null, null, createdAtEpochMillis))
}

/**
 * Cattle service and sheep joining commands that carry their expected birth day (owner decision D-019).
 * They are journalled for farm replication only: no server-era outbox row is written, because there is
 * no application server to receive them.
 */
class BreedingDueCommands(
    private val database: FarmOsDatabase,
    private val farmId: String,
    /** Replays an operation received from another device: domain writes only, it is already journalled. */
    private val replaying: Boolean = false,
    private val json: Json = Json { encodeDefaults = true },
) {
    suspend fun recordCattleService(command: RecordCattleServiceV2, context: LocalCommandContext): LocalCommandResult {
        BreedingDueSchedule.cattleService(command)?.let { error(it) }
        val cow = requireNotNull(database.animals().get(farmId, command.animalId)) { "Active cow not found" }
        require(cow.speciesCode == "cattle" && cow.status == "active") { "Active cow not found" }
        journal(context, CATTLE_SERVICE_V2, "animal", command.animalId, json.encodeToString(command)) {
            database.writeCattleService(
                farmId, command.serviceId, command.animalId, command.method, command.occurredEpochDay, command.expectedCalvingEpochDay,
                command.pdTaskId, command.paddockTaskId, command.calvingTaskId, context.occurredAtEpochMillis,
            )
        }
        return LocalCommandResult(context.mutationId, command.serviceId, true)
    }

    suspend fun recordJoining(command: RecordSheepJoiningV2, context: LocalCommandContext): LocalCommandResult {
        BreedingDueSchedule.joining(command)?.let { error(it) }
        val group = requireNotNull(database.groups().get(farmId, command.groupId)) { "Joining needs a sheep mob" }
        require(group.speciesCode == "sheep") { "Joining needs a sheep mob" }
        journal(context, SHEEP_JOINING_V2, "animal_group", command.groupId, json.encodeToString(command)) {
            database.writeSheepJoining(
                farmId, command.joiningId, command.groupId, command.startedEpochDay, command.expectedLambingEpochDay,
                command.scanTaskId, command.preLambTaskId, command.paddockTaskId, command.lambingTaskId, context.occurredAtEpochMillis,
            )
        }
        return LocalCommandResult(context.mutationId, command.joiningId, true)
    }

    private suspend fun journal(
        context: LocalCommandContext,
        commandName: String,
        entityType: String,
        entityId: String,
        payloadJson: String,
        localWrite: suspend () -> Unit,
    ) {
        require(context.farmId == farmId) { "Farm context mismatch" }
        if (replaying) return database.withTransaction { localWrite() }
        database.withTransaction {
            localWrite()
            database.journalLocalOperation(
                operationId = context.mutationId,
                farmId = farmId,
                entityType = entityType,
                entityId = entityId,
                actorId = context.actorId,
                deviceId = context.deviceId,
                businessTimeEpochMillis = context.occurredAtEpochMillis,
                createdAtEpochMillis = System.currentTimeMillis(),
                baseVersion = null,
                operationType = commandName,
                payloadJson = payloadJson,
                schemaVersion = 2,
            )
        }
    }

    companion object {
        const val CATTLE_SERVICE_V2 = "cattle.record_service.v2"
        const val SHEEP_JOINING_V2 = "sheep.record_joining.v2"
    }
}
