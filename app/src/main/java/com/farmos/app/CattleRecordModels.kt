package com.farmos.app

import com.farmos.core.database.FarmOsDatabase
import com.farmos.feature.ops.CattleMilkRow
import com.farmos.feature.ops.CattleObservationRow
import com.farmos.feature.ops.CattleRecords
import com.farmos.feature.ops.CattleSccRow
import com.farmos.feature.ops.CattleTimelineRow
import com.farmos.feature.ops.CattleWithdrawalRow
import com.farmos.feature.ops.cattleLitres
import java.time.Instant
import java.time.ZoneOffset

/** Farm-scoped, read-only records for one cattle animal. Timeline order: newest day first, then record family. */
internal suspend fun loadCattleRecords(database: FarmOsDatabase, farmId: String, animalId: String): CattleRecords {
    val lifecycle = database.lifecycle()
    fun day(epochMillis: Long) = Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
    val services = lifecycle.cattleServicesFor(farmId, animalId)
    val pds = lifecycle.cattlePdsFor(farmId, animalId)
    val calvings = lifecycle.cattleCalvingsFor(farmId, animalId)
    val bcs = lifecycle.cattleBcsFor(farmId, animalId)
    val milk = lifecycle.cattleMilkFor(farmId, animalId)
    val locomotion = lifecycle.cattleLocomotionFor(farmId, animalId)
    val scc = lifecycle.cattleSccFor(farmId, animalId)
    val dryOffs = lifecycle.cattleDryOffsFor(farmId, animalId)
    val weanings = lifecycle.cattleWeaningsFor(farmId, animalId)
    val treatments = database.treatments().forAnimal(farmId, animalId)
    val observations = database.healthObservations().forAnimal(farmId, animalId)
    val timeline = buildList {
        services.forEach { add(CattleTimelineRow(it.id, it.occurredEpochDay, "Service", it.method)) }
        pds.forEach { add(CattleTimelineRow(it.id, it.occurredEpochDay, "Pregnancy diagnosis", it.result)) }
        calvings.forEach { add(CattleTimelineRow(it.id, it.occurredEpochDay, "Calving", "${it.bornCount} born · ${it.liveCount} live · ${it.deadCount} dead")) }
        bcs.forEach { add(CattleTimelineRow(it.id, it.occurredEpochDay, "BCS", "${it.scoreTenths / 10}.${it.scoreTenths % 10} (${it.scale})")) }
        milk.forEach { add(CattleTimelineRow(it.id, it.occurredEpochDay, "Milk", cattleLitres(it.litresMilli))) }
        locomotion.forEach { add(CattleTimelineRow(it.id, it.occurredEpochDay, "Locomotion", "Score ${it.score}")) }
        scc.forEach { add(CattleTimelineRow(it.id, it.occurredEpochDay, "SCC", "%,d cells/mL".format(it.cellsPerMl))) }
        dryOffs.forEach { row ->
            add(CattleTimelineRow(row.id, row.occurredEpochDay, "Dry-off", row.expectedCalvingEpochDay?.let { "Recorded expected calving ${java.time.LocalDate.ofEpochDay(it)}" } ?: "Dried off"))
        }
        weanings.forEach { add(CattleTimelineRow(it.id, it.occurredEpochDay, "Weaning", it.weightGrams?.let { g -> "${java.math.BigDecimal.valueOf(g, 3).stripTrailingZeros().toPlainString()} kg" } ?: "Weaned")) }
        treatments.forEach { add(CattleTimelineRow(it.id, day(it.occurredAtEpochMillis), "Treatment", it.reason)) }
        observations.forEach { add(CattleTimelineRow(it.id, day(it.occurredAtEpochMillis), "Observation", (if (it.redFlag) "Red flag · " else "") + it.signs)) }
    }.sortedWith(compareByDescending<CattleTimelineRow> { it.epochDay }.thenBy { it.kind }.thenBy { it.id })
    return CattleRecords(
        milk = milk.map { CattleMilkRow(it.id, it.occurredEpochDay, it.litresMilli) },
        scc = scc.map { CattleSccRow(it.id, it.occurredEpochDay, it.cellsPerMl, it.dimDays) },
        withdrawals = lifecycle.withdrawalsForAnimal(farmId, animalId).map { CattleWithdrawalRow(it.id, it.product, it.windowKind, it.endsEpochDay) },
        observations = observations.map { CattleObservationRow(it.id, day(it.occurredAtEpochMillis), it.signs, it.redFlag) },
        treatmentCount = treatments.size,
        latestBcs = bcs.firstOrNull()?.let { "${it.scoreTenths / 10}.${it.scoreTenths % 10} (${it.scale})" },
        latestLocomotion = locomotion.firstOrNull()?.score,
        timeline = timeline,
    )
}
