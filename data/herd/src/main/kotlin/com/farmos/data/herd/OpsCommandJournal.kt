package com.farmos.data.herd

import androidx.room.withTransaction
import com.farmos.core.database.ApplicationState
import com.farmos.core.database.ReplicationOperationEntity
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.OutboxEntity
import com.farmos.core.database.insertOutboxAndJournal
import com.farmos.core.model.LocalCommandContext
import com.farmos.core.model.SyncState
import com.farmos.domain.access.AccessDenied
import com.farmos.domain.access.AccountStatus
import com.farmos.domain.access.LocalRole
import com.farmos.domain.access.Permission
import com.farmos.domain.access.RolePermissions
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Shared write boundary for local commands and their immutable operation journal. */
internal class OpsCommandJournal(
    private val database: FarmOsDatabase,
    private val farmId: String,
    private val replaying: Boolean,
) {
    suspend fun expectedVersion(aggregateType: String, aggregateId: String): Long {
        val authoritative = database.aggregateVersions().getVersion(farmId, aggregateType, aggregateId) ?: 0L
        val queued = database.outbox().countUnacknowledgedForAggregate(farmId, aggregateType, aggregateId)
        return authoritative + queued
    }

    suspend fun enqueue(
        context: LocalCommandContext,
        commandName: String,
        aggregateType: String,
        aggregateId: String,
        expectedStreamVersion: Long?,
        payloadJson: String,
        localWrite: suspend () -> Unit,
    ) {
        require(context.farmId == farmId) { "Farm context mismatch" }
        database.withTransaction {
            if (!replaying) {
                permissionFor(commandName)?.let { requireLocalPermission(context, it) }
                val known = database.replication().operation(farmId, context.mutationId)
                if (known != null) {
                    require(known.operationType == commandName && known.entityType == aggregateType &&
                        known.entityId == aggregateId && known.payloadJson == payloadJson) {
                        "Mutation id was already used for a different change"
                    }
                    return@withTransaction
                }
            }
            val original = if (replaying) database.replication().operation(farmId, context.mutationId) else null
            val legacyPreference = commandName == UNIT_PREFERENCE &&
                original?.entityType == "farm" && original.entityId == farmId
            if (replaying) {
                require(original != null && (legacyPreference ||
                    (original.entityType == aggregateType && original.entityId == aggregateId))) {
                    "Received operation does not identify the record being changed"
                }
            }
            if (commandName in VERSIONED_UPDATES) {
                val expected = originalBaseVersion(context, requireNotNull(expectedStreamVersion))
                val actual = if (legacyPreference) {
                    // Older builds sealed one version for the whole farm. Replay that original
                    // sequence, but never let an old writer replace a newer per-quantity edit.
                    require(appliedOperations("unit_preference", aggregateId).isEmpty()) {
                        "Conflict: a newer unit preference exists; review the original legacy change"
                    }
                    appliedOperations("farm", farmId).size.toLong()
                } else {
                    observedVersion(aggregateType, aggregateId)
                }
                require(expected == actual) {
                    "Conflict: $aggregateType changed from version $expected to $actual; review the original operation"
                }
            }
            val ordinal = database.outbox().nextAggregateOrdinal(farmId, aggregateType, aggregateId)
            localWrite()
            if (replaying) return@withTransaction
            database.insertOutboxAndJournal(
                OutboxEntity(
                    mutationId = context.mutationId,
                    farmId = farmId,
                    actorId = context.actorId,
                    deviceId = context.deviceId,
                    commandName = commandName,
                    commandSchemaVersion = 1,
                    aggregateType = aggregateType,
                    aggregateId = aggregateId,
                    aggregateOrdinal = ordinal,
                    expectedStreamVersion = expectedStreamVersion,
                    payloadJson = payloadJson,
                    occurredAtEpochMillis = context.occurredAtEpochMillis,
                    createdAtEpochMillis = System.currentTimeMillis(),
                    state = SyncState.PENDING.name,
                    attemptCount = 0,
                    nextAttemptAtEpochMillis = null,
                    lastErrorCode = null,
                    serverEventId = null,
                    serverStreamVersion = null,
                ),
            )
        }
    }
    /** Only the extracted command paths are governed here; historical receipts retain their original authority. */
    private suspend fun requireLocalPermission(context: LocalCommandContext, permission: Permission) {
        val account = database.localAccess().account(farmId, context.actorId)
        val role = account?.role?.let { runCatching { LocalRole.valueOf(it) }.getOrNull() }
        if (account?.status != AccountStatus.ACTIVE.name || role == null || !RolePermissions.allows(role, permission)) {
            throw AccessDenied("This account may not record this change on this farm")
        }
        val device = database.replication().device(farmId, context.deviceId)
        if (device == null || !device.isLocal || device.status != "ACTIVE" || device.revokedAfterSequence != null) {
            throw AccessDenied("This device may not record changes on this farm")
        }
    }

    private fun permissionFor(commandName: String): Permission? = when {
        commandName == UNIT_PREFERENCE -> Permission.MANAGE_FARM_SETTINGS
        commandName in WORK_COMMANDS -> Permission.RECORD_FARM_WORK
        else -> null // Untouched legacy handlers keep their existing boundary.
    }

    /** Failed/unapplied receipts remain history, not accepted configuration versions. */
    private suspend fun appliedOperations(aggregateType: String, aggregateId: String): List<ReplicationOperationEntity> =
        database.replication().operationsForEntity(farmId, aggregateType, aggregateId).filter {
            val application = database.replicationApplications().get(farmId, it.operationId)
            application == null || application.state == ApplicationState.APPLIED.name
        }

    suspend fun observedVersion(aggregateType: String, aggregateId: String): Long {
        val current = appliedOperations(aggregateType, aggregateId).size.toLong()
        if (aggregateType != "unit_preference") return current
        val quantityKind = aggregateId.removePrefix(farmId + ":")
        // Preserve accepted legacy history when a farm upgrades to per-quantity versions.
        val legacy = appliedOperations("farm", farmId).count {
            it.operationType == UNIT_PREFERENCE &&
                runCatching { Json.parseToJsonElement(it.payloadJson).jsonObject["quantityKind"]?.jsonPrimitive?.content }.getOrNull() == quantityKind
        }
        return current + legacy
    }

    /** The receiving device must never replace the base sealed by the origin with its own current version. */
    suspend fun originalBaseVersion(context: LocalCommandContext, locallyObserved: Long): Long {
        require(context.farmId == farmId) { "Farm context mismatch" }
        if (!replaying) return locallyObserved
        return requireNotNull(database.replication().operation(farmId, context.mutationId)?.baseVersion) {
            "Conflict: received change has no original base version; review it before applying"
        }
    }

    suspend fun requireOpenFlock(groupId: String) {
        require(appliedOperations("animal_group", groupId).none { it.operationType == "poultry.flock_close.v1" }) {
            "Flock is closed; review its close-out before recording further group changes"
        }
    }

    private companion object {
        const val UNIT_PREFERENCE = "farm.record_unit_preference.v1"
        // Existing ordinary work capture contract; settings has its explicit management permission above.
        val WORK_COMMANDS = setOf(
            "finance.record_budget.v1", "finance.revise_budget.v1",
            "group.create.v1", "group.amend.v1", "group.animal_move.v1", "group.census.v1",
            "poultry.flock_place.v1", "poultry.flock_move.v1", "poultry.flock_close.v1",
            "asset.create.v1", "maintenance.record.v1", "asset.meter_record.v1",
            "feed.issue.v1", "feed.record_plan.v1",
            "water.record.v1", "water.record_point.v1", "water.record_point_event.v1",
            "supplier.create.v1", "purchase.record.v1", "paddock.create.v1", "grazing.start.v1", "grazing.end.v1",
        )
        val VERSIONED_UPDATES = setOf(
            UNIT_PREFERENCE, "group.amend.v1", "group.animal_move.v1",
            "poultry.flock_move.v1", "poultry.flock_close.v1",
        )
    }
}
