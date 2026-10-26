package com.farmos.app

import com.farmos.core.database.*
import com.farmos.feature.rabbit.*

/** Exact current records with full IDs. Wave-level litter counts are never attributed to a particular doe/box. */
internal suspend fun loadRabbitUnits(database:FarmOsDatabase,farmId:String):List<RabbitUnitView> {
    val dao=database.rabbitry()
    val housing=dao.housing(farmId)
    val services=dao.services(farmId)
    val matings=dao.matings(farmId)
    val assignments=dao.assignments(farmId).associateBy{it.boxId}
    val boxes=database.rabbitProgramme().boxes(farmId)
    val waves=database.rabbitProgramme().waves(farmId)
    val ids=(housing.map{it.animalId}+services.map{it.buckId}+matings.flatMap{listOf(it.doeId,it.buckId)}+assignments.values.mapNotNull{it.doeId}).distinct()
    val animals=ids.chunked(300).flatMap{database.animals().getMany(farmId,it)}.filter{it.speciesCode=="rabbit"}.associateBy{it.id}
    fun label(id:String)=animals[id]?.let{listOfNotNull(it.tag,it.name).joinToString(" · ")} ?: "Rabbit record unavailable"
    return database.rabbitProgramme().cages(farmId).map { cage ->
        RabbitUnitView(cage.id,cage.code,cage.doeCapacity,
            housing.filter{it.cageId==cage.id}.map { row ->
                val animal=animals[row.animalId]
                val mating=matings.filter{it.doeId==row.animalId || it.buckId==row.animalId}.maxWithOrNull(compareBy<RabbitPairMatingEntity>{it.matedAtEpochMillis}.thenBy{it.id})
                val mate=mating?.let{if(it.doeId==row.animalId)it.buckId else it.doeId}
                RabbitResidentView(row.animalId,label(row.animalId),animal?.sex ?: "unknown",animal?.status ?: "unavailable",
                    row.role ?: "unrecorded",row.version,row.changedAtEpochMillis,mating?.matedAtEpochMillis,mate?.let(::label))
            }.sortedWith(compareBy({it.role},{it.label},{it.animalId})),
            boxes.filter{it.cageId==cage.id}.map { box ->
                val assignment=assignments[box.id]
                RabbitUnitNestView(box.id,box.code,box.status,assignment?.doeId,assignment?.doeId?.let(::label),assignment?.waveId,
                    assignment?.version ?: 0,assignment?.changedAtEpochMillis)
            }.sortedWith(compareBy({it.code},{it.id})),
            services.filter{it.cageId==cage.id}.map { RabbitServiceView(it.id,it.buckId,label(it.buckId),it.waveId,it.startedAtEpochMillis,it.endedAtEpochMillis,it.version,it.notes) },
            matings.filter{it.cageId==cage.id}.map { RabbitPairMatingView(it.id,it.doeId,label(it.doeId),it.buckId,label(it.buckId),it.waveId,it.matedAtEpochMillis,it.notes) },
            waves.filter{it.cageId==cage.id}.map{it.id},
        )
    }.sortedWith(compareBy({it.code},{it.cageId}))
}
