package com.farmos.app

import com.farmos.core.database.*
import com.farmos.core.design.FarmHealthSubject
import com.farmos.core.design.FarmHealthSubjectKind
import com.farmos.domain.ops.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class HealthEvidenceLine(val id:String,val title:String,val detail:String,val epochDay:Long?)
data class ProfileHealthModel(
    val subject:FarmHealthSubject,val label:String,val active:Boolean,val reviewedAt:Long,
    val signals:List<HealthReviewSignal>,val history:List<HealthEvidenceLine>,val weights:List<HealthWeightEvidence>,
    val observationCount:Int,val treatmentCount:Int?,val vaccinationCount:Int,val dueTasks:List<TaskEntity>,
    val protocols:List<HealthPackEntity>,val speciesFocus:String,val animalSex:String?=null,
)

/** Exact subject queries, not a filter over a capped farm-wide recent-record list. */
internal suspend fun loadProfileHealth(database:FarmOsDatabase,farmId:String,subject:FarmHealthSubject,
    now:Long=System.currentTimeMillis()):ProfileHealthModel=withContext(Dispatchers.IO) {
    if(subject.kind==FarmHealthSubjectKind.FLOCK) return@withContext loadFlockHealth(database,farmId,subject,now)
    val animal=requireNotNull(database.animals().get(farmId,subject.id)) { "This animal is not available on the current farm" }
    require(animal.speciesCode==subject.speciesCode) { "Profile health species does not match the animal" }
    require(animal.speciesCode in AnimalHealthReview.supportedSpecies)
    val zone=ZoneId.systemDefault();fun day(at:Long)=Instant.ofEpochMilli(at).atZone(zone).toLocalDate().toEpochDay()
    val today=day(now)
    val observations=database.healthObservations().forAnimal(farmId,animal.id).filter { it.speciesCode==animal.speciesCode }
    val treatments=database.treatments().forAnimal(farmId,animal.id).filter { it.speciesCode==animal.speciesCode }
    val vaccines=database.vaccinations().forAnimal(farmId,animal.id).filter { it.speciesCode==animal.speciesCode }
    // Both historical species-herd grams and the rabbit-native kg ledger are legitimate evidence.
    // Source prefixes distinguish equal row IDs in independent tables; duplicate same-time values do not imply a trend.
    val weights=database.measurements().history(farmId,animal.id,"weight").filter { it.unit=="g" }.map {
        HealthWeightEvidence("measurement:${it.id}",it.valueLong,it.measuredAtEpochMillis)
    } + if(animal.speciesCode=="rabbit") database.lifecycle().rabbitWeightHistory(farmId,animal.id).mapNotNull {
        runCatching { HealthWeightEvidence("rabbit-weight:${it.id}",BigDecimal.valueOf(it.weightKg).movePointRight(3).longValueExact(),it.weighedAtEpochMillis) }.getOrNull()
    } else emptyList()
    val rabbitFlags=if(animal.speciesCode=="rabbit") database.lifecycle().rabbitGiFlagsForAnimal(farmId,animal.id) else emptyList()
    val withdrawals=database.lifecycle().withdrawalsForAnimal(farmId,animal.id).filter {
        it.endsEpochDay>=today && treatments.any { t-> t.id==it.treatmentId && t.occurredAtEpochMillis<=now }
    }
    val taskReader=TaskPlanning(database,farmId,false,null)
    val stored=com.farmos.data.herd.RoomOpsRepository(database,farmId).openTasks().filter { it.seriesId==null }
    val projected=taskReader.openOccurrences(today).map { occurrence ->
        TaskEntity(occurrence.id,farmId,occurrence.moduleCode,occurrence.taskCode,occurrence.title,occurrence.dueEpochDay,
            "open",occurrence.animalId,null,null,now,seriesId=occurrence.seriesId,occurrenceEpochDay=occurrence.occurrenceEpochDay,
            assigneeAccountId=occurrence.assignee.accountId,assigneeWorkerId=occurrence.assignee.workerId)
    }
    val tasks=(stored+projected).filter { it.animalId==animal.id && it.dueOnEpochDay<=today }.distinctBy{it.id}
    val protocols=database.lifecycle().packs(farmId).filter { it.speciesCode==animal.speciesCode && it.status=="vet_accepted" }
    val history=buildList {
        observations.forEach { add(HealthEvidenceLine(it.id,if(it.redFlag) "Observation · urgent flag" else "Health observation",it.signs,day(it.occurredAtEpochMillis))) }
        rabbitFlags.forEach { add(HealthEvidenceLine("rabbit-gi:${it.id}","Rabbit GI concern recorded",it.signs,it.occurredEpochDay)) }
        treatments.forEach { add(HealthEvidenceLine(it.id,"Treatment recorded",it.reason,day(it.occurredAtEpochMillis))) }
        vaccines.forEach { add(HealthEvidenceLine(it.id,"Vaccination recorded",it.formularyItemId,day(it.occurredAtEpochMillis))) }
        withdrawals.forEach { add(HealthEvidenceLine(it.id,"Withdrawal · ${it.product}","${it.windowKind} · recorded end ${LocalDate.ofEpochDay(it.endsEpochDay)}",null)) }
        database.lifecycle().vetVisitsForAnimal(farmId,animal.id).forEach { add(HealthEvidenceLine(it.id,"Veterinary visit",it.reason,it.occurredEpochDay)) }
        database.lifecycle().labResultsForAnimal(farmId,animal.id).forEach { add(HealthEvidenceLine(it.id,"Lab · ${it.testName}",it.resultText,it.occurredEpochDay)) }
        if(animal.speciesCode in setOf("goat","sheep")) database.famacha().forAnimal(farmId,animal.id).forEach {
            add(HealthEvidenceLine(it.id,"FAMACHA recorded","${it.score} · interpretation requires the applicable trained assessment",it.occurredEpochDay))
        }
        if(animal.speciesCode=="goat") database.lifecycle().goatBcsFor(farmId,animal.id).forEach {
            add(HealthEvidenceLine(it.id,"Goat body condition","${BigDecimal.valueOf(it.scoreTenths.toLong(),1)} / 5",it.occurredEpochDay))
        }
        if(animal.speciesCode=="cattle") database.lifecycle().cattleBcsFor(farmId,animal.id).forEach {
            add(HealthEvidenceLine(it.id,"Cattle body condition","${BigDecimal.valueOf(it.scoreTenths.toLong(),1)} · recorded scale ${it.scale}",it.occurredEpochDay))
        }
    }.sortedWith(compareByDescending<HealthEvidenceLine>{it.epochDay ?: Long.MAX_VALUE}.thenBy{it.id})
    val signals=AnimalHealthReview.signals(AnimalHealthReviewInput(animal.speciesCode,now,weights,
        observations.filter{it.occurredAtEpochMillis<=now}.map{it.id}+rabbitFlags.filter{it.occurredEpochDay<=today}.map{"rabbit-gi:${it.id}"},
        observations.filter{it.redFlag && it.occurredAtEpochMillis<=now}.map{it.id}+rabbitFlags.filter{it.occurredEpochDay<=today}.map{"rabbit-gi:${it.id}"},
        withdrawals.map{it.id},tasks.map{it.id},vaccines.filter{it.occurredAtEpochMillis<=now}.map{it.id},protocols.map{it.id}))
    ProfileHealthModel(subject,listOfNotNull(animal.tag,animal.name).joinToString(" · "),animal.status=="active",now,
        signals,history,weights,observations.size+rabbitFlags.size,treatments.size,vaccines.size,tasks,protocols,profileSpeciesFocus(animal.speciesCode),animal.sex)
}

internal fun profileSpeciesFocus(species:String):String=when(species) {
    "goat"->"Review the goat’s weight trend, body-condition and trained parasite assessments alongside its health history and reproductive stage."
    "sheep"->"Review individual weight, body condition, parasite and foot/skin records alongside lambing and flock history."
    "rabbit"->"Review rabbit-specific weight, appetite/faecal observations, reproductive and litter context. Goat/sheep parasite scores do not apply."
    "cattle"->"Review the recorded body-condition scale, weight, locomotion, reproductive stage and any dairy records together."
    "poultry"->"Keep individual bird health separate from flock losses, intake, vaccination, housing and biosecurity observations."
    else->"Review the species-specific record and qualified farm health plan."
}

private suspend fun loadFlockHealth(database:FarmOsDatabase,farmId:String,subject:FarmHealthSubject,now:Long):ProfileHealthModel {
    val group=requireNotNull(database.groups().get(farmId,subject.id)) { "Flock not available on this farm" }
    require(group.speciesCode=="poultry" && subject.speciesCode=="poultry")
    val today=Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
    val days=database.poultryFlockDays().forGroup(farmId,group.id)
    val vaccines=database.vaccinations().forGroup(farmId,group.id).filter { it.speciesCode=="poultry" }
    val poultryVaccines=database.lifecycle().vaccinationsForGroup(farmId,group.id)
    val biosecurity=database.lifecycle().walksForGroup(farmId,group.id)
    val protocols=database.lifecycle().packs(farmId).filter { it.speciesCode=="poultry" && it.status=="vet_accepted" }
    val latest=days.firstOrNull{it.occurredEpochDay<=today}
    val signals=buildList {
        if(latest==null || latest.occurredEpochDay<today) add(HealthReviewSignal("flock_daily","Update flock observations",
            "No flock-day record for this review date is present. Missing data is not zero mortality or normal health.",emptyList(),HealthReviewAction.RECORD_OBSERVATION))
        if(latest!=null && latest.dead>0) add(HealthReviewSignal("recorded_losses","Deaths were recorded",
            "${latest.dead} deaths were recorded on ${LocalDate.ofEpochDay(latest.occurredEpochDay)}. Review the actual daily record and current observations; this is not a disease diagnosis.",listOf(latest.id),HealthReviewAction.REVIEW_RECORDS))
        if(protocols.isEmpty()) add(HealthReviewSignal("flock_plan","Review the poultry health programme",
            "No vet-accepted poultry programme is recorded. Do not substitute goat or sheep schedules.",emptyList(),HealthReviewAction.REVIEW_PROTOCOLS))
    }
    val history=days.map { HealthEvidenceLine(it.id,"Flock day","${it.dead} deaths · ${it.culls} culls · ${it.eggs} eggs · ${BigDecimal.valueOf(it.feedGrams,3)} kg feed",it.occurredEpochDay) }+
        vaccines.map { HealthEvidenceLine(it.id,"Flock vaccination",it.formularyItemId,Instant.ofEpochMilli(it.occurredAtEpochMillis).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()) } +
        poultryVaccines.map { HealthEvidenceLine("poultry-vaccine:${it.id}","Poultry vaccination",it.formularyItemId,it.occurredEpochDay) } +
        biosecurity.map { HealthEvidenceLine("biosecurity:${it.id}","Flock biosecurity review",it.findings,it.occurredEpochDay) }
    return ProfileHealthModel(subject,group.name,true,now,signals,history.sortedByDescending{it.epochDay},emptyList(),days.size,null,vaccines.size+poultryVaccines.size,
        emptyList(),protocols,profileSpeciesFocus("poultry"))
}
