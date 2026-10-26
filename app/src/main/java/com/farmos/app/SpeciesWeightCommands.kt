package com.farmos.app

import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.model.LocalCommandContext
import com.farmos.core.model.LocalCommandResult
import com.farmos.data.herd.RoomHerdRepository
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.goat.RecordGoatWeight
import com.farmos.domain.rabbit.RecordRabbitWeight
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Adapter only: exact species-specific, journalled command handlers remain the mutation authority. */
internal suspend fun recordSpeciesWeight(database:FarmOsDatabase,farmId:String,ops:RoomOpsRepository,
    species:String,animalId:String,weightKg:String,day:LocalDate,context:LocalCommandContext,
    recordGoat:suspend (RecordGoatWeight,LocalCommandContext)->LocalCommandResult) {
    require(context.farmId==farmId && species in WEIGHT_SPECIES)
    val animal=requireNotNull(database.animals().get(farmId,animalId)) { "Selected animal is not on this farm" }
    require(animal.speciesCode==species && animal.status=="active") { "Choose an active $species record" }
    val commandDay=java.time.Instant.ofEpochMilli(context.occurredAtEpochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    require(!day.isAfter(commandDay)) { "Weight date cannot be after the command’s recorded time" }
    val grams=weightKg.trim().toBigDecimal().movePointRight(3).longValueExact()
    require(grams>0) { "Weight must be greater than zero" }
    val at=if(day==commandDay) context.occurredAtEpochMillis else day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val id=UUID.randomUUID().toString()
    when(species) {
        "goat" -> recordGoat(RecordGoatWeight(animalId,id,grams,at),context)
        "rabbit" -> ops.recordRabbitWeight(RecordRabbitWeight(id,animalId,grams/1000.0,at),context)
        else -> RoomHerdRepository(database,farmId,species).recordWeight(animalId,id,grams,at,context)
    }
}
