package com.farmos.data.herd

import com.farmos.core.database.*
import com.farmos.core.model.LocalCommandContext
import com.farmos.core.model.LocalCommandResult
import com.farmos.domain.rabbit.*
import kotlinx.serialization.json.Json

/** Authorized, atomic, journalled rabbitry facts. A planned wave is never an occupancy or mating fact. */
class RabbitryCommands(private val database:FarmOsDatabase,private val farmId:String,
    replaying:Boolean=false,private val json:Json=Json { encodeDefaults=true }) {
    private val journal=OpsCommandJournal(database,farmId,replaying)
    private val dao get()=database.rabbitry()

    suspend fun setHousing(command:SetRabbitHousing,context:LocalCommandContext):LocalCommandResult {
        require(command.expectedVersion>=0 && command.reason.length<=2000)
        RabbitryRules.requireRecordedTime(command.occurredAtEpochMillis,context.occurredAtEpochMillis)
        require((command.cageId==null)==(command.role==null)) { "A cage and housing role must be selected together" }
        journal.enqueueCommand(json,context,HOUSING,"rabbit_housing",command.animalId,command.expectedVersion,command) {
            val animal=rabbit(command.animalId,active=command.cageId!=null)
            val old=dao.housingFor(farmId,animal.id)
            require((old?.version ?: 0)==command.expectedVersion) { "Housing changed on another device; reload and review the move" }
            require(command.occurredAtEpochMillis>=(old?.changedAtEpochMillis ?: 0)) { "A current housing update cannot predate the recorded placement" }
            if(old?.cageId!=command.cageId) {
                require(dao.activeServices(farmId,animal.id).isEmpty()) { "End the buck’s recorded service before moving him" }
                require(dao.nestsForDoe(farmId,animal.id).none { assignment ->
                    dao.box(farmId,assignment.boxId)?.status=="in_cage"
                }) { "Record nest removal before moving a doe with an in-use nest" }
            }
            if(command.cageId!=null) {
                val cage=requireNotNull(dao.cage(farmId,requireNotNull(command.cageId))) { "Cage not on this farm" }
                require(command.role in RabbitryRules.housingRoles && RabbitryRules.sexAllowsRole(animal.sex,command.role!!)) { "Housing role does not match the recorded animal" }
                if(command.role=="doe") require(dao.occupants(farmId,cage.id).count { it.role=="doe" && it.animalId!=animal.id }<cage.doeCapacity) { "Recorded doe capacity would be exceeded" }
            }
            dao.setHousing(RabbitHousingEntity(farmId,animal.id,command.cageId,command.role,
                command.occurredAtEpochMillis,command.expectedVersion+1))
        }
        return LocalCommandResult(context.mutationId,command.animalId,true)
    }

    suspend fun startService(command:StartRabbitBuckService,context:LocalCommandContext):LocalCommandResult {
        require(command.serviceId.isNotBlank() && command.notes.length<=4000)
        RabbitryRules.requireRecordedTime(command.startedAtEpochMillis,context.occurredAtEpochMillis)
        journal.enqueueCommand(json,context,START_SERVICE,"rabbit_service",command.serviceId,0,command) {
            val buck=rabbit(command.buckId)
            require(RabbitryRules.sexAllowsRole(buck.sex,"buck")) { "Select a registered buck" }
            requireNotNull(dao.cage(farmId,command.cageId)) { "Service cage is not on this farm" }
            val home=requireNotNull(dao.housingFor(farmId,buck.id)) { "Record the buck’s housing first" }
            require(home.cageId==command.cageId && home.role=="buck") { "Record the buck’s actual placement in this cage first" }
            require(command.startedAtEpochMillis>=home.changedAtEpochMillis) { "Service cannot begin before the recorded placement" }
            require(dao.activeServices(farmId,buck.id).isEmpty()) { "This buck already has an open service interval" }
            requireWave(command.waveId,command.cageId)
            dao.insertService(RabbitBuckServiceEntity(farmId,command.serviceId,command.cageId,buck.id,command.waveId,
                command.startedAtEpochMillis,null,command.notes.trim(),1))
        }
        return LocalCommandResult(context.mutationId,command.serviceId,true)
    }

    suspend fun endService(command:EndRabbitBuckService,context:LocalCommandContext):LocalCommandResult {
        RabbitryRules.requireRecordedTime(command.endedAtEpochMillis,context.occurredAtEpochMillis)
        journal.enqueueCommand(json,context,END_SERVICE,"rabbit_service",command.serviceId,command.expectedVersion,command) {
            val row=requireNotNull(dao.service(farmId,command.serviceId)) { "Service is not on this farm" }
            require(row.version==command.expectedVersion && row.endedAtEpochMillis==null) { "Service changed; reload before ending it" }
            require(command.endedAtEpochMillis>=row.startedAtEpochMillis) { "Service end precedes its start" }
            check(dao.endService(farmId,row.id,command.endedAtEpochMillis,command.expectedVersion)==1)
        }
        return LocalCommandResult(context.mutationId,command.serviceId,true)
    }

    suspend fun recordMating(command:RecordRabbitPairMating,context:LocalCommandContext):LocalCommandResult {
        require(command.matingId.isNotBlank() && command.doeId!=command.buckId && command.notes.length<=4000)
        RabbitryRules.requireRecordedTime(command.matedAtEpochMillis,context.occurredAtEpochMillis)
        journal.enqueueCommand(json,context,MATING,"rabbit_pair_mating",command.matingId,0,command) {
            val doe=rabbit(command.doeId);val buck=rabbit(command.buckId)
            require(RabbitryRules.sexAllowsRole(doe.sex,"doe") && RabbitryRules.sexAllowsRole(buck.sex,"buck")) { "Select an exact doe and buck on this farm" }
            requireNotNull(dao.cage(farmId,command.cageId)) { "Mating cage is not on this farm" }
            requireWave(command.waveId,command.cageId)
            dao.insertMating(RabbitPairMatingEntity(farmId,command.matingId,command.cageId,doe.id,buck.id,
                command.waveId,command.matedAtEpochMillis,command.notes.trim()))
            // Neither pregnancy, kit parentage, present housing nor a new due schedule is inferred here.
        }
        return LocalCommandResult(context.mutationId,command.matingId,true)
    }

    suspend fun assignNest(command:SetRabbitNestAssignment,context:LocalCommandContext):LocalCommandResult {
        require(command.expectedVersion>=0)
        require(command.doeId!=null || command.waveId==null) { "Release the nest and wave association together" }
        RabbitryRules.requireRecordedTime(command.occurredAtEpochMillis,context.occurredAtEpochMillis)
        journal.enqueueCommand(json,context,NEST,"rabbit_nest_assignment",command.boxId,command.expectedVersion,command) {
            val box=requireNotNull(dao.box(farmId,command.boxId)) { "Nest box is not on this farm" }
            val old=dao.assignment(farmId,box.id)
            require((old?.version ?: 0)==command.expectedVersion) { "Nest assignment changed; reload and review" }
            require(command.occurredAtEpochMillis>=(old?.changedAtEpochMillis ?: 0)) { "Assignment predates the current record" }
            if(command.doeId==null) {
                require(box.status!="in_cage") { "Record physical removal before releasing an in-use nest" }
            } else {
                val doe=rabbit(requireNotNull(command.doeId))
                require(RabbitryRules.sexAllowsRole(doe.sex,"doe")) { "A nest is reserved for a registered doe" }
                require(dao.housingFor(farmId,doe.id)?.cageId==box.cageId) { "Record this doe’s placement in the nest’s cage first" }
                require(box.status in setOf("available","sanitized","assigned") || box.status=="in_cage" && old?.doeId==doe.id) { "Clean or remove this nest before assigning another doe" }
                requireWave(command.waveId,box.cageId)
                require(dao.nestsForDoe(farmId,doe.id).none { assignment ->
                    assignment.boxId!=box.id && dao.box(farmId,assignment.boxId)?.status in setOf("assigned","in_cage")
                }) { "This doe already has an assigned or in-use nest" }
            }
            dao.setAssignment(RabbitNestAssignmentEntity(farmId,box.id,command.doeId,command.waveId,
                command.occurredAtEpochMillis,command.expectedVersion+1))
            if(command.doeId!=null && box.status in setOf("available","sanitized")) database.rabbitProgramme().updateBoxStatus(farmId,box.id,"assigned")
            if(command.doeId==null && box.status=="assigned") database.rabbitProgramme().updateBoxStatus(farmId,box.id,"available")
        }
        return LocalCommandResult(context.mutationId,command.boxId,true)
    }

    private suspend fun rabbit(id:String,active:Boolean=true):AnimalEntity {
        val row=requireNotNull(database.animals().get(farmId,id)) { "Rabbit not on this farm" }
        require(row.speciesCode=="rabbit" && (!active || row.status=="active")) { "Select an active rabbit" }
        return row
    }
    private suspend fun requireWave(id:String?,cageId:String) {
        if(id!=null) require(dao.wave(farmId,id)?.cageId==cageId) { "The selected wave belongs to another cage or farm" }
    }
    companion object {
        const val HOUSING="rabbit.housing_set.v1"
        const val START_SERVICE="rabbit.buck_service_start.v1"
        const val END_SERVICE="rabbit.buck_service_end.v1"
        const val MATING="rabbit.pair_mating_record.v1"
        const val NEST="rabbit.nest_assign.v1"
    }
}
