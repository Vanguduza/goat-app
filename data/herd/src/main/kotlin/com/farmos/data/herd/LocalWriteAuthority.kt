package com.farmos.data.herd

import com.farmos.core.database.ApplicationState
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.ReplicationOperationEntity
import com.farmos.core.model.LocalCommandContext
import com.farmos.domain.access.AccessDenied
import com.farmos.domain.access.AccountStatus
import com.farmos.domain.access.LocalRole
import com.farmos.domain.access.Permission
import com.farmos.domain.access.RolePermissions

/** Read authority inside the business transaction; a remembered screen role is never admission. */
internal suspend fun FarmOsDatabase.requireLocalCommandAuthority(
    context: LocalCommandContext,
    permission: Permission,
) {
    val account = localAccess().account(context.farmId, context.actorId)
    val role = account?.role?.let { runCatching { LocalRole.valueOf(it) }.getOrNull() }
    if (account?.status != AccountStatus.ACTIVE.name || role == null || !RolePermissions.allows(role, permission)) {
        throw AccessDenied("This account may not record this change on this farm")
    }
    val device = replication().device(context.farmId, context.deviceId)
    if (device == null || !device.isLocal || device.status != "ACTIVE" || device.revokedAfterSequence != null) {
        throw AccessDenied("This device may not record changes on this farm")
    }
}

/** Bind a replay or retry to its original admitted command, including defaults in older payloads. */
internal fun requireOriginalCommand(
    original: ReplicationOperationEntity,
    context: LocalCommandContext,
    commandName: String,
    entityType: String,
    entityId: String,
    matchesPayload: (String) -> Boolean,
    legacyEntity: Boolean = false,
) {
    require(original.operationId == context.mutationId && original.farmId == context.farmId &&
        original.operationType == commandName && original.schemaVersion == OpsCommandPermissions.schemaVersionFor(commandName) &&
        (legacyEntity || (original.entityType == entityType && original.entityId == entityId)) &&
        original.actorId == context.actorId && original.deviceId == context.deviceId &&
        original.businessTimeEpochMillis == context.occurredAtEpochMillis &&
        runCatching { matchesPayload(original.payloadJson) }.getOrDefault(false)
    ) { "Mutation id was already used for a different change" }
}

/** Local receipts are already applied; received pending/failed work keeps its original admission. */
internal suspend fun FarmOsDatabase.commandAlreadyApplied(
    original: ReplicationOperationEntity,
    replaying: Boolean,
): Boolean {
    val state = replicationApplications().get(original.farmId, original.operationId)?.state
    if (state == null || state == ApplicationState.APPLIED.name) return true
    require(replaying && state != ApplicationState.SET_ASIDE.name) {
        "Conflict: the original change has not been accepted; review it before applying"
    }
    return false
}
