package com.farmos.app

import androidx.room.withTransaction
import com.farmos.core.database.ApplicationState
import com.farmos.core.database.COMMAND_PAYLOAD_KEY
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.OperationApplier
import com.farmos.core.database.ReplicationApplicationEntity
import com.farmos.core.database.UnappliedOperation
import com.farmos.core.database.journalLocalOperation
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.AnimalExitCommands
import com.farmos.domain.ops.ReverseAnimalExit
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import org.json.JSONObject

/** Management set a received operation aside in conflict review (D-013); replicated so every device agrees. */
internal const val SET_ASIDE_COMMAND = "replication.set_aside.v1"

/** A received change listed in the Conflict Centre (FOS-SYNC-006), with where it came from and why it waits. */
internal data class ConflictItem(
    val operationId: String,
    val operationType: String,
    val label: String,
    val deviceName: String,
    val actorName: String,
    val businessTimeEpochMillis: Long,
    val state: String,
    val reason: String?,
    val attempts: Int,
)

/** A conflict with its full recorded content, for Conflict Detail (FOS-SYNC-007). */
internal data class ConflictDetail(val item: ConflictItem, val fields: List<Pair<String, String>>, val correction: String?)

/** The review lists: changes waiting, how many in all, and those already set aside. */
internal data class ConflictReview(val waiting: List<ConflictItem>, val waitingTotal: Long, val setAside: List<ConflictItem>)

/** A readable name for a command type: `animal.exit_record.v1` reads "Animal exit record". */
internal fun operationLabel(operationType: String): String =
    operationType.substringBeforeLast(".v").replace('.', ' ').replace('_', ' ').replaceFirstChar { it.uppercase() }

/**
 * The correction recorded with a set-aside, for commands whose effect another device may already show.
 * An exit that stands elsewhere is reversed there, so every device ends with the same animal status.
 */
internal fun correctionFor(operationType: String): String? = when (operationType) {
    AnimalExitCommands.RECORD -> "Setting this exit aside also records its reversal, so a device that recorded it returns the animal to the herd."
    else -> null
}

/** Reads the Conflict Centre from this device's journal. Blocking reads: call off the main thread. */
internal suspend fun FarmOsDatabase.loadConflictReview(farmId: String, limit: Int = CONFLICT_PAGE): ConflictReview {
    val devices = replication().devices(farmId).associate { it.deviceId to it.name }
    val people = localAccess().accounts(farmId).associate { it.accountId to it.displayName }
    fun UnappliedOperation.toItem() = ConflictItem(
        operationId, operationType, operationLabel(operationType), devices[deviceId] ?: deviceId, people[actorId] ?: actorId,
        businessTimeEpochMillis, state, reason, attempts,
    )
    val applications = replicationApplications()
    return ConflictReview(
        waiting = applications.unappliedForReview(farmId, limit).map { it.toItem() },
        waitingTotal = applications.count(farmId, ApplicationState.FAILED.name) + applications.count(farmId, ApplicationState.AWAITING_APPLIER.name),
        setAside = applications.setAsideForReview(farmId, limit).map { it.toItem() },
    )
}

/** Every field the received change recorded, as sent, for Conflict Detail. */
internal suspend fun FarmOsDatabase.conflictDetail(farmId: String, item: ConflictItem): ConflictDetail {
    val payload = replication().operation(farmId, item.operationId)?.payloadJson?.let { runCatching { JSONObject(it) }.getOrNull() }
    val fields = if (payload == null) emptyList() else payload.keys().asSequence().sorted().map { key -> key to payload.opt(key).toString() }.toList()
    return ConflictDetail(item, fields, correctionFor(item.operationType))
}

/**
 * Sets a received change aside after review (D-013): it stays in the journal as history, is never retried,
 * and the decision replicates. For a change another device may already show, the correction is recorded
 * with it. Only a received change that has not taken effect here can be set aside.
 */
internal suspend fun FarmOsDatabase.setAsideReceivedOperation(farmId: String, operationId: String, reason: String, context: LocalCommandContext) {
    require(context.farmId == farmId) { "Farm context mismatch" }
    require(reason.isNotBlank()) { "Say why this change is set aside" }
    val operation = requireNotNull(replication().operation(farmId, operationId)) { "Change not found on this device" }
    val application = replicationApplications().get(farmId, operationId)
    require(application != null && application.state != ApplicationState.APPLIED.name && application.state != ApplicationState.SET_ASIDE.name) {
        "Only a received change waiting for review can be set aside"
    }
    withTransaction {
        if (operation.operationType == AnimalExitCommands.RECORD) {
            val exit = JSONObject(operation.payloadJson)
            val day = Instant.ofEpochMilli(context.occurredAtEpochMillis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
            AnimalExitCommands(this, farmId).reverse(
                ReverseAnimalExit(UUID.randomUUID().toString(), exit.getString("animalId"), exit.getString("exitId"), "Set aside in conflict review: ${reason.trim()}", day),
                context.copy(mutationId = UUID.randomUUID().toString()),
                ofUnappliedExit = true,
            )
        }
        replicationApplications().upsert(application.copy(state = ApplicationState.SET_ASIDE.name, reason = reason.trim(), updatedAtEpochMillis = context.occurredAtEpochMillis))
        journalLocalOperation(
            operationId = context.mutationId,
            farmId = farmId,
            entityType = "replication_operation",
            entityId = operationId,
            actorId = context.actorId,
            deviceId = context.deviceId,
            businessTimeEpochMillis = context.occurredAtEpochMillis,
            createdAtEpochMillis = context.occurredAtEpochMillis,
            baseVersion = null,
            operationType = SET_ASIDE_COMMAND,
            payloadJson = JSONObject().put("operationId", operationId).put("reason", reason.trim()).toString(),
            schemaVersion = 1,
        )
    }
}

/**
 * On receipt of a set-aside decision: a change still waiting here is set aside too; one not received yet is
 * set aside when it arrives. A change that took effect here, or this device's own, is left as it is; the
 * correction recorded with the decision is what changes it.
 */
internal val conflictReplicationAppliers: Map<String, OperationApplier> = mapOf(
    SET_ASIDE_COMMAND to OperationApplier { database, op ->
        val decision = JSONObject(op.payload.getValue(COMMAND_PAYLOAD_KEY))
        val target = decision.getString("operationId")
        val reason = decision.getString("reason")
        val applications = database.replicationApplications()
        val existing = applications.get(op.farmId, target)
        val received = database.replication().operation(op.farmId, target) != null
        when {
            existing != null && existing.state != ApplicationState.APPLIED.name ->
                applications.upsert(existing.copy(state = ApplicationState.SET_ASIDE.name, reason = reason, updatedAtEpochMillis = op.businessTimeEpochMillis))
            existing == null && !received ->
                applications.upsert(ReplicationApplicationEntity(target, op.farmId, ApplicationState.SET_ASIDE.name, reason, 0, op.businessTimeEpochMillis))
        }
    },
)

private const val CONFLICT_PAGE = 50
