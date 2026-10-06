package com.farmos.app

import com.farmos.core.database.FarmOsDatabase
import com.farmos.domain.ops.DueDates
import com.farmos.domain.ops.DueSource
import com.farmos.domain.ops.GestationSpecies
import com.farmos.feature.ops.CattleCalvingDue
import com.farmos.feature.ops.CattleCalvingDueView
import com.farmos.feature.ops.CattleDueSource
import com.farmos.feature.ops.CattleIdentifierRow
import com.farmos.feature.ops.CattleLotCloseView
import com.farmos.feature.ops.CattleLotDaysView
import com.farmos.feature.ops.CattleLotPlacementView
import com.farmos.feature.ops.CattleLotView
import com.farmos.feature.ops.CattleMilkRow
import com.farmos.feature.ops.CattleMovementRow
import com.farmos.feature.ops.CattleObservationRow
import com.farmos.feature.ops.CattleRecords
import com.farmos.feature.ops.CattleSccRow
import com.farmos.feature.ops.CattleTimelineRow
import com.farmos.feature.ops.CattleWithdrawalRow
import com.farmos.feature.ops.WeightView
import com.farmos.feature.ops.cattleMovementDirection
import java.time.Instant
import java.time.ZoneOffset

/** Farm-scoped, read-only records for one cattle animal. Timeline order: newest day first, then record family. */
private fun litres(milli: Long): String = java.math.BigDecimal.valueOf(milli, 3).stripTrailingZeros().toPlainString() + " L"

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
    val movements = lifecycle.movementsForAnimal(farmId, animalId)
    val timeline = buildList {
        services.forEach { add(CattleTimelineRow(it.id, it.occurredEpochDay, "Service", it.method)) }
        pds.forEach { add(CattleTimelineRow(it.id, it.occurredEpochDay, "Pregnancy diagnosis", it.result)) }
        calvings.forEach { add(CattleTimelineRow(it.id, it.occurredEpochDay, "Calving", "${it.bornCount} born · ${it.liveCount} live · ${it.deadCount} dead")) }
        bcs.forEach { add(CattleTimelineRow(it.id, it.occurredEpochDay, "BCS", "${it.scoreTenths / 10}.${it.scoreTenths % 10} (${it.scale})")) }
        milk.forEach { add(CattleTimelineRow(it.id, it.occurredEpochDay, "Milk", litres(it.litresMilli))) }
        locomotion.forEach { add(CattleTimelineRow(it.id, it.occurredEpochDay, "Locomotion", "Score ${it.score}")) }
        scc.forEach { add(CattleTimelineRow(it.id, it.occurredEpochDay, "SCC", "%,d cells/mL".format(it.cellsPerMl))) }
        dryOffs.forEach { row ->
            add(CattleTimelineRow(row.id, row.occurredEpochDay, "Dry-off", row.expectedCalvingEpochDay?.let { "Recorded expected calving ${java.time.LocalDate.ofEpochDay(it)}" } ?: "Dried off"))
        }
        weanings.forEach { add(CattleTimelineRow(it.id, it.occurredEpochDay, "Weaning", it.weightGrams?.let { g -> "${java.math.BigDecimal.valueOf(g, 3).stripTrailingZeros().toPlainString()} kg" } ?: "Weaned")) }
        treatments.forEach { add(CattleTimelineRow(it.id, day(it.occurredAtEpochMillis), "Treatment", it.reason)) }
        observations.forEach { add(CattleTimelineRow(it.id, day(it.occurredAtEpochMillis), "Observation", (if (it.redFlag) "Red flag · " else "") + it.signs)) }
        movements.forEach {
            add(CattleTimelineRow(it.id, it.occurredEpochDay, "Movement", "${cattleMovementDirection(it.direction)} · from ${it.fromPlace ?: "not recorded"} · to ${it.toPlace ?: "not recorded"}"))
        }
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
        weights = database.measurements().history(farmId, animalId, "weight").map { WeightView(it.id, day(it.measuredAtEpochMillis), it.valueLong, it.unit) },
        movements = movements.map { CattleMovementRow(it.id, it.occurredEpochDay, it.direction, it.fromPlace, it.toPlace) },
        identifiers = lifecycle.identifiersForAnimal(farmId, animalId).map { CattleIdentifierRow(it.id, it.type, it.value, it.isActive, it.assignedEpochDay) },
    )
}

/**
 * Every active cow expected to calve (owner decision D-019): her latest service without a later calving,
 * minus cows diagnosed open since. A dry-off expected calving date wins; otherwise the service day plus
 * the farm's own cattle gestation period.
 */
internal suspend fun loadCattleCalvingDue(database: FarmOsDatabase, farmId: String): CattleCalvingDue {
    val period = database.gestationPeriod(farmId, GestationSpecies.CATTLE)
    val rows = database.lifecycle().cattleCalvingDue(farmId)
        // A cow diagnosed open since her service is not expected to calve from it.
        .filter { it.latestPdResult != "open" }
        .map { row ->
            val due = DueDates.estimate(row.serviceEpochDay, period, storedDueEpochDay = row.storedDueEpochDay)
            CattleCalvingDueView(
                animalId = row.animalId,
                tag = row.tag,
                name = row.name,
                serviceEpochDay = row.serviceEpochDay,
                method = row.method,
                pdResult = row.latestPdResult,
                source = if (due.source == DueSource.STORED) CattleDueSource.STORED else CattleDueSource.PREDICTED,
                earliestEpochDay = due.earliestEpochDay,
                typicalEpochDay = due.typicalEpochDay,
                latestEpochDay = due.latestEpochDay,
            )
        }
        .sortedWith(compareBy({ it.typicalEpochDay }, { it.tag }, { it.animalId }))
    return CattleCalvingDue(rows, period.typicalDays)
}

/** Every recorded feedlot row on the farm, grouped by lot (cattle group), newest first. */
internal suspend fun loadCattleLots(database: FarmOsDatabase, farmId: String): List<CattleLotView> {
    val lifecycle = database.lifecycle()
    val groupNames = database.groups().forFarm(farmId).associate { it.id to it.name }
    val placements = lifecycle.cattleLotPlacements(farmId).groupBy { it.groupId }
    val days = lifecycle.cattleDaysOnFeed(farmId).groupBy { it.groupId }
    val closeouts = lifecycle.cattleLotCloseouts(farmId).groupBy { it.groupId }
    return (placements.keys + days.keys + closeouts.keys).sorted().map { groupId ->
        CattleLotView(
            groupId = groupId,
            groupLabel = groupNames[groupId] ?: "Group not on this device",
            placements = placements[groupId].orEmpty().map { CattleLotPlacementView(it.id, it.headCount, it.placedEpochDay) },
            daysOnFeed = days[groupId].orEmpty().map { CattleLotDaysView(it.id, it.daysOnFeed, it.occurredEpochDay) },
            closeouts = closeouts[groupId].orEmpty().map { CattleLotCloseView(it.id, it.headOut, it.weightGrams, it.daysOnFeed, it.occurredEpochDay) },
        )
    }
}
