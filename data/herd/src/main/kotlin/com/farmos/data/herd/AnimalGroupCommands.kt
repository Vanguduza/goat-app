package com.farmos.data.herd

import androidx.room.withTransaction
import com.farmos.core.database.AnimalGroupEntity
import com.farmos.core.database.AnimalGroupMembershipEntity
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.GroupCensusEntity
import com.farmos.core.database.PoultryPlacementEntity
import com.farmos.core.database.TaskEntity
import com.farmos.core.model.LocalCommandContext
import com.farmos.core.model.LocalCommandResult
import com.farmos.domain.ops.RecordGroupCensus
import com.farmos.domain.ops.CreateAnimalGroup
import com.farmos.domain.ops.AmendAnimalGroup
import com.farmos.domain.ops.MoveAnimalGroup
import com.farmos.domain.ops.OpsValidator
import com.farmos.domain.ops.PlacePoultryFlock
import com.farmos.domain.ops.MovePoultryFlock
import com.farmos.domain.ops.ClosePoultryFlock
import kotlinx.serialization.json.Json

/** Command handlers extracted from the operations facade; validation and writes share one transaction. */
internal class AnimalGroupCommands(
    private val database: FarmOsDatabase,
    private val farmId: String,
    private val json: Json,
    private val journal: OpsCommandJournal,
) {
    suspend fun createGroup(command: CreateAnimalGroup, context: LocalCommandContext): LocalCommandResult = database.withTransaction {
        OpsValidator.group(command)?.let { error(it) }
        journal.enqueueCommand(json, context, "group.create.v1", "animal_group", command.groupId, 0, command) {
            database.groups().insert(AnimalGroupEntity(command.groupId, farmId, command.speciesCode, command.name.trim(), command.headCount))
        }
        LocalCommandResult(context.mutationId, command.groupId, true)
    }

    suspend fun amendGroup(command: AmendAnimalGroup, context: LocalCommandContext): LocalCommandResult = database.withTransaction {
        OpsValidator.amendGroup(command)?.let { error(it) }
        val current = requireNotNull(database.groups().get(farmId, command.groupId)) { "Group not found on this farm" }
        if (current.speciesCode != command.speciesCode) journal.requireOpenFlock(command.groupId)
        require(current.speciesCode == command.speciesCode ||
            (current.headCount == 0 && database.groupMemberships().animalsInGroup(farmId, command.groupId).isEmpty() &&
                database.lifecycle().placementsForGroup(farmId, command.groupId).isEmpty())) {
            "A populated group cannot change species"
        }
        journal.enqueueCommand(json, context, "group.amend.v1", "animal_group", command.groupId, journal.observedVersion("animal_group", command.groupId), command) {
            database.groups().updateDetails(farmId, command.groupId, command.name.trim(), command.speciesCode)
        }
        LocalCommandResult(context.mutationId, command.groupId, true)
    }

    suspend fun moveAnimalGroup(command: MoveAnimalGroup, context: LocalCommandContext): LocalCommandResult = database.withTransaction {
        OpsValidator.moveAnimalGroup(command)?.let { error(it) }
        journal.requireOpenFlock(command.fromGroupId)
        journal.requireOpenFlock(command.toGroupId)
        val fromGroup = requireNotNull(database.groups().get(farmId, command.fromGroupId)) { "Group move needs a source group on this farm" }
        val toGroup = requireNotNull(database.groups().get(farmId, command.toGroupId)) { "Group move needs a target group on this farm" }
        require(fromGroup.speciesCode == toGroup.speciesCode) {
            "Group move needs two groups of the same species (${fromGroup.speciesCode} to ${toGroup.speciesCode})"
        }
        val animals = database.animals().getMany(farmId, command.animalIds)
        val byId = animals.associateBy { it.id }
        command.animalIds.forEach { animalId ->
            requireNotNull(byId[animalId]) { "Group move needs animal $animalId on this farm" }
        }
        val memberships = database.groupMemberships().getMany(farmId, command.animalIds).associateBy { it.animalId }
        animals.forEach { animal ->
            val label = animal.tag.ifBlank { animal.id }
            require(animal.speciesCode == fromGroup.speciesCode) {
                "Group move needs $label to be a ${fromGroup.speciesCode}, not a ${animal.speciesCode}"
            }
            val current = memberships[animal.id]?.groupId
            require(current == null || current == command.fromGroupId || current == command.toGroupId) {
                val currentName = database.groups().get(farmId, current!!)?.name ?: current
                "Group move needs $label to be a member of ${fromGroup.name}; it is in $currentName"
            }
        }
        journal.enqueueCommand(json, context, "group.animal_move.v1", "animal_group", command.fromGroupId, journal.observedVersion("animal_group", command.fromGroupId), command) {
            val live = database.groupMemberships().getMany(farmId, command.animalIds).associateBy { it.animalId }
            var fromDelta = 0
            var toDelta = 0
            command.animalIds.forEach { animalId ->
                val current = live[animalId]?.groupId
                if (current == command.toGroupId) return@forEach // already applied: replay is idempotent
                database.groupMemberships().upsert(
                    AnimalGroupMembershipEntity(farmId, animalId, command.toGroupId, context.occurredAtEpochMillis),
                )
                if (current == command.fromGroupId) fromDelta -= 1
                toDelta += 1
            }
            if (fromDelta != 0) {
                val from = requireNotNull(database.groups().get(farmId, command.fromGroupId)) { "Group move lost its source group" }
                database.groups().setHeadCount(farmId, command.fromGroupId, (from.headCount + fromDelta).coerceAtLeast(0))
            }
            if (toDelta != 0) {
                val to = requireNotNull(database.groups().get(farmId, command.toGroupId)) { "Group move lost its target group" }
                database.groups().setHeadCount(farmId, command.toGroupId, to.headCount + toDelta)
            }
        }
        LocalCommandResult(context.mutationId, command.moveId, true)
    }

    suspend fun recordCensus(command: RecordGroupCensus, context: LocalCommandContext): LocalCommandResult = database.withTransaction {
        OpsValidator.census(command)?.let { error(it) }
        journal.requireOpenFlock(command.groupId)
        requireNotNull(database.groups().get(farmId, command.groupId)) { "Census needs a group on this farm" }
        journal.enqueueCommand(json, context, "group.census.v1", "animal_group", command.groupId, journal.observedVersion("animal_group", command.groupId), command) {
            database.lifecycle().insertCensus(
                GroupCensusEntity(command.censusId, farmId, command.groupId, command.headCount, command.occurredEpochDay),
            )
            database.groups().setHeadCount(farmId, command.groupId, command.headCount)
        }
        LocalCommandResult(context.mutationId, command.censusId, true)
    }

    suspend fun placeFlock(command: PlacePoultryFlock, context: LocalCommandContext): LocalCommandResult = database.withTransaction {
        journal.requireOpenFlock(command.groupId)
        OpsValidator.flockPlace(command)?.let { error(it) }
        journal.enqueueCommand(json, context, "poultry.flock_place.v1", "animal_group", command.groupId, journal.observedVersion("animal_group", command.groupId), command) {
            database.lifecycle().insertPlacement(
                PoultryPlacementEntity(command.placementId, farmId, command.groupId, command.houseId, command.poultryKindCode, command.headCount, command.occurredEpochDay),
            )
            database.tasks().insert(TaskEntity(command.inspectTaskId, farmId, "poultry", "BIOSECURITY", "Placement inspection / biosecurity", command.occurredEpochDay, "open", null, null, null, context.occurredAtEpochMillis))
            database.tasks().insert(TaskEntity(command.vaxTaskId, farmId, "poultry", "FLOCK_VAX", "Kind vaccination pack", command.occurredEpochDay + 1, "open", null, null, null, context.occurredAtEpochMillis))
        }
        LocalCommandResult(context.mutationId, command.placementId, true)
    }

    suspend fun moveFlock(command: MovePoultryFlock, context: LocalCommandContext): LocalCommandResult = database.withTransaction {
        OpsValidator.flockMove(command)?.let { error(it) }
        journal.requireOpenFlock(command.groupId)
        val group = requireNotNull(database.groups().get(farmId, command.groupId)) { "Flock not found on this farm" }
        require(group.speciesCode == "poultry") { "Flock movement is for poultry groups" }
        requireNotNull(database.lifecycle().houses(farmId).firstOrNull { it.id == command.toHouseId }) { "Target house not found on this farm" }
        val latest = database.lifecycle().placementsForGroup(farmId, command.groupId).firstOrNull()
            ?: error("Flock move needs a placed flock")
        require(latest.houseId == command.fromHouseId) { "Flock is not in the selected house" }
        val kind = latest.poultryKindCode
        journal.enqueueCommand(json, context, "poultry.flock_move.v1", "animal_group", command.groupId, journal.observedVersion("animal_group", command.groupId), command) {
            database.lifecycle().insertPlacement(
                PoultryPlacementEntity(command.moveId, farmId, command.groupId, command.toHouseId, kind, command.headCount, command.occurredEpochDay),
            )
        }
        LocalCommandResult(context.mutationId, command.moveId, true)
    }

    suspend fun closeFlock(command: ClosePoultryFlock, context: LocalCommandContext): LocalCommandResult = database.withTransaction {
        OpsValidator.flockClose(command)?.let { error(it) }
        journal.requireOpenFlock(command.groupId)
        val group = requireNotNull(database.groups().get(farmId, command.groupId)) { "Flock close-out needs a flock" }
        require(group.speciesCode == "poultry") { "Flock close-out is for poultry groups" }
        journal.enqueueCommand(json, context, "poultry.flock_close.v1", "animal_group", command.groupId, journal.observedVersion("animal_group", command.groupId), command) {
            database.groups().setHeadCount(farmId, command.groupId, 0)
        }
        LocalCommandResult(context.mutationId, command.closeoutId, true)
    }
}
