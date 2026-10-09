package com.farmos.data.goat

import androidx.room.withTransaction
import com.farmos.core.database.ApplicationState
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.model.LocalCommandContext
import com.farmos.core.model.LocalCommandResult
import com.farmos.domain.access.AccessDenied
import com.farmos.domain.access.AccountStatus
import com.farmos.domain.access.LocalRole
import com.farmos.domain.access.Permission
import com.farmos.domain.access.RolePermissions
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Local account/device admission and immutable receipt matching for the goat command boundary.
 * D-016 governs local roles; the in-module breeding matrix reserves planning for management.
 * D-022/R6 supersedes direct lifecycle status writes with auditable exit records.
 */
internal class GoatCommandJournal(
    private val database: FarmOsDatabase,
    private val farmId: String,
    private val replaying: Boolean,
) {
    suspend fun run(
        commandName: String,
        entityId: String,
        context: LocalCommandContext,
        matchesPayload: (String) -> Boolean,
        write: suspend () -> LocalCommandResult,
    ): LocalCommandResult = database.withTransaction {
        require(context.farmId == farmId) { "Farm context mismatch" }
        val permission = when (commandName) {
            "goat.record_mating.v1", "goat.plan_lactation.v1" -> Permission.MANAGE_BREEDING
            "goat.register.v1", "goat.record_weight.v1", "goat.set_status.v1",
            "goat.record_kidding.v1", "goat.record_famacha.v1", "goat.record_milk.v1",
            "goat.record_bcs.v1", "goat.record_scc.v1", "goat.record_heat.v1",
            "goat.record_pregnancy.v1", "goat.register_kid.v1", "goat.record_weaning.v1" -> Permission.RECORD_FARM_WORK
            else -> throw AccessDenied("This goat command has no approved local permission")
        }
        if (!replaying) database.requireGoatCommandAuthority(context, permission)
        val original = database.replication().operation(farmId, context.mutationId)
        if (original != null) {
            require(original.farmId == farmId && original.operationId == context.mutationId &&
                original.operationType == commandName && original.schemaVersion == 1 &&
                original.entityType == "animal" && original.entityId == entityId &&
                original.actorId == context.actorId && original.deviceId == context.deviceId &&
                original.businessTimeEpochMillis == context.occurredAtEpochMillis &&
                runCatching { matchesPayload(original.payloadJson) }.getOrDefault(false)
            ) { "Mutation id was already used for a different goat change" }
            val application = database.replicationApplications().get(farmId, context.mutationId)
            if (application == null || application.state == ApplicationState.APPLIED.name) {
                return@withTransaction LocalCommandResult(context.mutationId, entityId, locallyDurable = true)
            }
            require(replaying && application.state != ApplicationState.SET_ASIDE.name) {
                "Conflict: the original goat change has not been accepted; review it before applying"
            }
        } else {
            require(!replaying) { "A received goat change must be journalled before it is applied" }
        }
        if (!replaying && commandName == "goat.set_status.v1") {
            throw AccessDenied("Record an animal exit with its reason, cause or buyer; direct status changes are historical replay only")
        }
        write()
    }
}

internal suspend fun FarmOsDatabase.requireGoatCommandAuthority(context: LocalCommandContext, permission: Permission) {
    val account = localAccess().account(context.farmId, context.actorId)
    val role = account?.role?.let { runCatching { LocalRole.valueOf(it) }.getOrNull() }
    if (account?.status != AccountStatus.ACTIVE.name || role == null || !RolePermissions.allows(role, permission)) {
        throw AccessDenied("This account may not record this goat change on this farm")
    }
    val device = replication().device(context.farmId, context.deviceId)
    if (device == null || !device.isLocal || device.status != "ACTIVE" || device.revokedAfterSequence != null) {
        throw AccessDenied("This device may not record goat changes on this farm")
    }
}

internal suspend inline fun <reified C> GoatCommandJournal.runCommand(
    json: Json,
    commandName: String,
    entityId: String,
    command: C,
    context: LocalCommandContext,
    noinline write: suspend () -> LocalCommandResult,
): LocalCommandResult = run(commandName, entityId, context, { json.decodeFromString<C>(it) == command }, write)
