package com.farmos.data.herd

import androidx.room.withTransaction
import com.farmos.core.database.ApplicationState
import com.farmos.core.database.ReplicationOperationEntity
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.OutboxEntity
import com.farmos.core.database.insertOutboxAndJournal
import com.farmos.core.model.LocalCommandContext
import com.farmos.core.model.SyncState
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
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
        matchesPayload: (String) -> Boolean,
        localWrite: suspend () -> Unit,
    ) {
        require(context.farmId == farmId) { "Farm context mismatch" }
        database.withTransaction {
            val permission = OpsCommandPermissions.requiredFor(commandName, payloadJson)
            if (!replaying) {
                database.requireLocalCommandAuthority(context, permission)
            }
            val original = database.replication().operation(farmId, context.mutationId)
            val legacyPreference = replaying && commandName == UNIT_PREFERENCE &&
                original?.entityType == "farm" && original.entityId == farmId
            if (original != null) {
                requireOriginalCommand(
                    original, context, commandName, aggregateType, aggregateId, matchesPayload, legacyPreference,
                )
                if (database.commandAlreadyApplied(original, replaying)) return@withTransaction
            } else {
                require(!replaying) { "A received change must be journalled before it is applied" }
            }
            if (!replaying) OpsCommandPermissions.requireLocalVersion(commandName)
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
                    commandSchemaVersion = OpsCommandPermissions.schemaVersionFor(commandName),
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
        val VERSIONED_UPDATES = setOf(
            UNIT_PREFERENCE, "group.amend.v1", "group.animal_move.v1",
            "poultry.flock_move.v1", "poultry.flock_close.v1",
        )
    }
}

/** Typed equality accepts omitted legacy defaults but never a changed replay payload. */
internal suspend inline fun <reified C> OpsCommandJournal.enqueueCommand(
    json: Json,
    context: LocalCommandContext,
    commandName: String,
    aggregateType: String,
    aggregateId: String,
    expectedStreamVersion: Long?,
    command: C,
    noinline localWrite: suspend () -> Unit,
) = enqueue(
    context, commandName, aggregateType, aggregateId, expectedStreamVersion, json.encodeToString(command),
    { original -> json.decodeFromString<C>(original) == command }, localWrite,
)
