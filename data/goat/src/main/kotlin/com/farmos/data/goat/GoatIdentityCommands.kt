package com.farmos.data.goat

import androidx.room.withTransaction
import com.farmos.core.database.AnimalIdentifierEntity
import com.farmos.core.database.ApplicationState
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.OutboxEntity
import com.farmos.core.database.ReplicationOperationEntity
import com.farmos.core.database.insertOutboxAndJournal
import com.farmos.core.model.LocalCommandContext
import com.farmos.core.model.LocalCommandResult
import com.farmos.core.model.SyncState
import com.farmos.domain.access.AccessDenied
import com.farmos.domain.access.AccountStatus
import com.farmos.domain.access.LocalRole
import com.farmos.domain.access.Permission
import com.farmos.domain.access.RolePermissions
import com.farmos.domain.goat.AmendGoatIdentity
import com.farmos.domain.goat.GoatValidationResult
import com.farmos.domain.goat.GoatValidator
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** An identity correction and its history form one authorised, versioned Room change. */
internal class GoatIdentityCommands(
    private val database: FarmOsDatabase,
    private val farmId: String,
    private val json: Json,
    private val replaying: Boolean,
) {
    suspend fun amend(command: AmendGoatIdentity, context: LocalCommandContext): LocalCommandResult {
        require(context.farmId == farmId) { "Farm context mismatch" }
        require(command.animalId.isNotBlank()) { "Select a goat first" }
        val validation = GoatValidator.amendIdentity(command)
        require(validation is GoatValidationResult.Valid) { (validation as GoatValidationResult.Invalid).message }
        // Capture the local base before entering the write transaction so overlapping commands
        // cannot both overwrite the version they observed.
        val locallyObserved = if (replaying) null else observedVersion(command.animalId, context.mutationId)
        database.withTransaction {
            if (!replaying) requireLocalPermission(context)
            val known = database.replication().operation(farmId, context.mutationId)
            if (known != null) {
                requireSameOperation(known, command, context)
                val application = database.replicationApplications().get(farmId, context.mutationId)
                if (application == null || application.state == ApplicationState.APPLIED.name) return@withTransaction
                require(replaying && application.state != ApplicationState.SET_ASIDE.name) {
                    "Conflict: the original identity change has not been accepted; review it before applying"
                }
            } else {
                require(!replaying) { "A received identity change must be journalled before it is applied" }
            }
            // Keep the v1 animal aggregate contract: received edits retain the origin's sealed
            // base, including other accepted animal events. A concurrent edit needs review.
            val expected = if (replaying) requireNotNull(known?.baseVersion) {
                "Conflict: received identity change has no original base version; review it before applying"
            } else requireNotNull(locallyObserved)
            val actual = observedVersion(command.animalId, context.mutationId)
            require(expected == actual) {
                "Conflict: animal identity changed from version $expected to $actual; review the original operation"
            }

            val animal = requireNotNull(database.animals().get(farmId, command.animalId)) { "Goat not found" }
            require(animal.speciesCode == "goat") { "Identity amendment needs a goat on this farm" }
            val newTag = command.tag?.trim()?.takeIf { it.isNotEmpty() } ?: animal.tag
            val requestedName = command.name
            val newName = if (requestedName == null) animal.name else requestedName.trim().takeIf { it.isNotEmpty() }
            val officialId = command.officialId?.trim()?.takeIf { it.isNotEmpty() }
            if (officialId != null) {
                require(database.lifecycle().activeIdentifierValuesExcept(farmId, command.animalId).none {
                    it.equals(officialId, ignoreCase = true)
                }) { "Identifier '$officialId' is already recorded on this farm" }
            }
            database.animals().updateIdentity(farmId, command.animalId, newTag, newName, context.occurredAtEpochMillis)
            if (officialId != null) {
                database.lifecycle().deactivateIdentifiers(farmId, command.animalId, "official_id")
                database.lifecycle().insertIdentifier(
                    AnimalIdentifierEntity(
                        id = UUID.nameUUIDFromBytes(
                            "goat-official-id:$farmId:${context.mutationId}".toByteArray(StandardCharsets.UTF_8),
                        ).toString(),
                        farmId = farmId,
                        animalId = command.animalId,
                        type = "official_id",
                        value = officialId,
                        isActive = true,
                        assignedEpochDay = Math.floorDiv(context.occurredAtEpochMillis, 86_400_000L),
                    ),
                )
            }
            if (!replaying) {
                database.insertOutboxAndJournal(
                    OutboxEntity(
                        mutationId = context.mutationId,
                        farmId = farmId,
                        actorId = context.actorId,
                        deviceId = context.deviceId,
                        commandName = COMMAND,
                        commandSchemaVersion = 1,
                        aggregateType = "animal",
                        aggregateId = command.animalId,
                        aggregateOrdinal = database.outbox().nextAggregateOrdinal(farmId, "animal", command.animalId),
                        expectedStreamVersion = expected,
                        payloadJson = json.encodeToString(command),
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
        return LocalCommandResult(context.mutationId, command.animalId, locallyDurable = true)
    }

    /** Read current authority in the same transaction as the change, never a remembered screen role. */
    private suspend fun requireLocalPermission(context: LocalCommandContext) {
        val account = database.localAccess().account(farmId, context.actorId)
        val role = account?.role?.let { runCatching { LocalRole.valueOf(it) }.getOrNull() }
        if (account?.status != AccountStatus.ACTIVE.name || role == null ||
            !RolePermissions.allows(role, Permission.RECORD_FARM_WORK)
        ) {
            throw AccessDenied("This account may not amend animal identity on this farm")
        }
        val device = database.replication().device(farmId, context.deviceId)
        if (device == null || !device.isLocal || device.status != "ACTIVE" || device.revokedAfterSequence != null) {
            throw AccessDenied("This device may not record identity changes on this farm")
        }
    }

    private fun requireSameOperation(
        known: ReplicationOperationEntity,
        command: AmendGoatIdentity,
        context: LocalCommandContext,
    ) {
        require(known.operationType == COMMAND && known.schemaVersion == 1 &&
            known.entityType == "animal" && known.entityId == command.animalId &&
            known.actorId == context.actorId && known.deviceId == context.deviceId &&
            known.businessTimeEpochMillis == context.occurredAtEpochMillis &&
            json.decodeFromString<AmendGoatIdentity>(known.payloadJson) == command
        ) { "Mutation id was already used for a different change" }
    }

    /** Received but unapplied operations are history, not accepted versions of this animal. */
    private suspend fun observedVersion(animalId: String, excluding: String): Long =
        database.replication().operationsForEntity(farmId, "animal", animalId).count { operation ->
            if (operation.operationId == excluding) {
                false
            } else {
                val application = database.replicationApplications().get(farmId, operation.operationId)
                application == null || application.state == ApplicationState.APPLIED.name
            }
        }.toLong()

    private companion object {
        const val COMMAND = "goat.amend_identity.v1"
    }
}
