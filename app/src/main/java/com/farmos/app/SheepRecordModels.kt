package com.farmos.app

import com.farmos.core.database.FarmOsDatabase
import com.farmos.feature.ops.SheepAnimalRecords
import com.farmos.feature.ops.SheepTimelineRow
import com.farmos.feature.ops.SheepWithdrawalRow
import com.farmos.feature.ops.SheepWoolRecords
import com.farmos.feature.ops.SheepWoolRow
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset

private const val SHEEP_WOOL_LIMIT = 100

private fun kg(grams: Long): String = BigDecimal.valueOf(grams, 3).stripTrailingZeros().toPlainString() + " kg"

private fun micron(tenths: Int): String = BigDecimal.valueOf(tenths.toLong(), 1).stripTrailingZeros().toPlainString() + " µm"

/** Farm-scoped, read-only records for one sheep. Timeline: newest day first, then record family. */
internal suspend fun loadSheepRecords(database: FarmOsDatabase, farmId: String, animalId: String): SheepAnimalRecords {
    val lifecycle = database.lifecycle()
    fun day(epochMillis: Long) = Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
    val scans = lifecycle.sheepScansFor(farmId, animalId)
    val lambings = lifecycle.sheepLambingsFor(farmId, animalId)
    val dag = lifecycle.sheepDagFor(farmId, animalId)
    val footrot = lifecycle.sheepFootrotFor(farmId, animalId)
    val flystrike = lifecycle.sheepFlystrikeFor(farmId, animalId)
    val wool = lifecycle.sheepWoolFor(farmId, animalId)
    val shearing = lifecycle.sheepShearingFor(farmId, animalId)
    val micronTests = lifecycle.sheepMicronFor(farmId, animalId)
    val markings = lifecycle.sheepMarkingsFor(farmId, animalId)
    val weanings = lifecycle.sheepWeaningsFor(farmId, animalId)
    val famacha = database.famacha().forAnimal(farmId, animalId)
    val treatments = database.treatments().forAnimal(farmId, animalId)
    val observations = database.healthObservations().forAnimal(farmId, animalId)
    val timeline = buildList {
        scans.forEach { add(SheepTimelineRow(it.id, it.occurredEpochDay, "Pregnancy scan", it.result)) }
        lambings.forEach { add(SheepTimelineRow(it.id, it.occurredEpochDay, "Lambing", "${it.bornCount} born · ${it.liveCount} live · ${it.deadCount} dead")) }
        dag.forEach { add(SheepTimelineRow(it.id, it.occurredEpochDay, "Dag score", "Score ${it.score}")) }
        footrot.forEach { add(SheepTimelineRow(it.id, it.occurredEpochDay, "Footrot", "Score ${it.score}")) }
        flystrike.forEach { add(SheepTimelineRow(it.id, it.occurredEpochDay, "Flystrike", "Score ${it.score}" + (it.region?.let { r -> " · $r" } ?: ""))) }
        famacha.forEach { add(SheepTimelineRow(it.id, it.occurredEpochDay, "FAMACHA", "Score ${it.score}")) }
        wool.forEach { add(SheepTimelineRow(it.id, it.occurredEpochDay, "Fleece", kg(it.greasyGrams.toLong()))) }
        shearing.forEach { add(SheepTimelineRow(it.id, it.occurredEpochDay, "Shearing", it.kind + (it.greasyGrams?.let { g -> " · ${kg(g.toLong())}" } ?: ""))) }
        micronTests.forEach { add(SheepTimelineRow(it.id, it.occurredEpochDay, "Micron", micron(it.micronTenths))) }
        markings.forEach { add(SheepTimelineRow(it.id, it.occurredEpochDay, "Marking", "${it.markedCount} marked")) }
        weanings.forEach { add(SheepTimelineRow(it.id, it.occurredEpochDay, "Weaning", "${it.weanedCount} weaned")) }
        treatments.forEach { add(SheepTimelineRow(it.id, day(it.occurredAtEpochMillis), "Treatment", it.reason)) }
        observations.forEach { add(SheepTimelineRow(it.id, day(it.occurredAtEpochMillis), "Observation", (if (it.redFlag) "Red flag · " else "") + it.signs)) }
    }.sortedWith(compareByDescending<SheepTimelineRow> { it.epochDay }.thenBy { it.kind }.thenBy { it.id })
    return SheepAnimalRecords(
        withdrawals = lifecycle.withdrawalsForAnimal(farmId, animalId).map { SheepWithdrawalRow(it.id, it.product, it.windowKind, it.endsEpochDay) },
        treatmentCount = treatments.size,
        observationCount = observations.size,
        latestFamacha = famacha.firstOrNull()?.score,
        latestDag = dag.firstOrNull()?.score,
        latestFootrot = footrot.firstOrNull()?.score,
        latestFlystrike = flystrike.firstOrNull()?.let { "Score ${it.score}" + (it.region?.let { r -> " ($r)" } ?: "") },
        timeline = timeline,
    )
}

/** Farm-scoped wool records for the wool dashboard; subjects are sheep tags, mob ids or "Unassigned". */
internal suspend fun loadSheepWool(database: FarmOsDatabase, farmId: String): SheepWoolRecords {
    val lifecycle = database.lifecycle()
    val labels = mutableMapOf<String, String>()
    suspend fun subject(animalId: String?, groupId: String?): String = when {
        animalId != null -> labels.getOrPut(animalId) { database.animals().get(farmId, animalId)?.tag ?: "Sheep not on this device" }
        groupId != null -> "Mob $groupId"
        else -> "Unassigned"
    }
    val clips = lifecycle.sheepWoolClips(farmId, SHEEP_WOOL_LIMIT)
    return SheepWoolRecords(
        clips = clips.map { SheepWoolRow(it.id, it.occurredEpochDay, subject(it.animalId, it.groupId), kg(it.greasyGrams.toLong())) },
        clipGreasyGramsTotal = clips.sumOf { it.greasyGrams.toLong() },
        shearing = lifecycle.sheepShearingEvents(farmId, SHEEP_WOOL_LIMIT).map {
            SheepWoolRow(it.id, it.occurredEpochDay, subject(it.animalId, it.groupId), it.kind + (it.greasyGrams?.let { g -> " · ${kg(g.toLong())}" } ?: ""))
        },
        micron = lifecycle.sheepMicronTests(farmId, SHEEP_WOOL_LIMIT).map {
            SheepWoolRow(it.id, it.occurredEpochDay, subject(it.animalId, it.groupId), micron(it.micronTenths))
        },
    )
}
