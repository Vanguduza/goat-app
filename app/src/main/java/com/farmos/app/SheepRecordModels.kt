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

/** Bulk id lookups are chunked below SQLite's 999 bound-variable limit on older Android releases. */
internal const val LOOKUP_CHUNK = 500

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

/**
 * Farm-scoped wool records for the wool dashboard; subjects are sheep tags, mob ids or "Unassigned".
 * Row lists are the latest [SHEEP_WOOL_LIMIT] per family; the greasy-weight total and the counts are
 * exhaustive farm-scoped aggregates, never sums of the bounded rows.
 */
internal suspend fun loadSheepWool(database: FarmOsDatabase, farmId: String): SheepWoolRecords {
    val lifecycle = database.lifecycle()
    val clips = lifecycle.sheepWoolClips(farmId, SHEEP_WOOL_LIMIT)
    val shearing = lifecycle.sheepShearingEvents(farmId, SHEEP_WOOL_LIMIT)
    val micronTests = lifecycle.sheepMicronTests(farmId, SHEEP_WOOL_LIMIT)
    val animalIds = (clips.map { it.animalId } + shearing.map { it.animalId } + micronTests.map { it.animalId }).filterNotNull().distinct()
    val tags = animalIds.chunked(LOOKUP_CHUNK).flatMap { database.animals().getMany(farmId, it) }.associate { it.id to it.tag }
    fun subject(animalId: String?, groupId: String?): String = when {
        animalId != null -> tags[animalId] ?: "Sheep not on this device"
        groupId != null -> "Mob $groupId"
        else -> "Unassigned"
    }
    return SheepWoolRecords(
        clips = clips.map { SheepWoolRow(it.id, it.occurredEpochDay, subject(it.animalId, it.groupId), kg(it.greasyGrams.toLong())) },
        clipGreasyGramsTotal = lifecycle.sheepWoolGreasyGramsTotal(farmId),
        shearing = shearing.map {
            SheepWoolRow(it.id, it.occurredEpochDay, subject(it.animalId, it.groupId), it.kind + (it.greasyGrams?.let { g -> " · ${kg(g.toLong())}" } ?: ""))
        },
        micron = micronTests.map { SheepWoolRow(it.id, it.occurredEpochDay, subject(it.animalId, it.groupId), micron(it.micronTenths)) },
        clipCount = lifecycle.sheepWoolClipCount(farmId),
        shearingCount = lifecycle.sheepShearingEventCount(farmId),
        micronCount = lifecycle.sheepMicronTestCount(farmId),
    )
}
