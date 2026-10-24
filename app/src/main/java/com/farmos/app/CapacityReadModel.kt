package com.farmos.app

import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.RabbitCageEntity
import com.farmos.core.database.RabbitWaveEntity
import com.farmos.core.design.FarmBarValue
import com.farmos.feature.ops.PaddockView
import com.farmos.feature.ops.PastureRecords
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

internal data class CapacityUsage(
    val id: String, val label: String, val unit: String?, val quantityMilli: Long,
    val records: Int, val firstEpochDay: Long, val latestEpochDay: Long, val onHandMilli: Long? = null,
) {
    val spanDays: Long get() = Math.addExact(Math.subtractExact(latestEpochDay, firstEpochDay), 1L)
    val dateRange: String get() = "${LocalDate.ofEpochDay(firstEpochDay)} to ${LocalDate.ofEpochDay(latestEpochDay)} · $spanDays-day recorded span"
    fun amount(milli: Long): String = if (unit == null) "$milli milli-units · unit unavailable" else capacityQuantity(BigDecimal.valueOf(milli, 3), unit)
    fun projection(days: Int): BigDecimal? = unit?.let {
        if (days !in 1..365 || spanDays <= 0) null else BigDecimal.valueOf(quantityMilli, 3)
            .multiply(BigDecimal.valueOf(days.toLong())).divide(BigDecimal.valueOf(spanDays), 3, RoundingMode.HALF_UP)
    }
}

internal data class CapacityCage(val cage: RabbitCageEntity, val plans: List<RabbitWaveEntity>, val plannedDoesToday: Long)
internal data class CapacityModel(
    val houses: List<FarmBarValue>, val cages: List<CapacityCage>, val pasture: PastureRecords,
    val stocking: List<FarmBarValue>, val feed: List<CapacityUsage>, val water: List<CapacityUsage>, val alerts: List<String>,
)
internal fun capacityQuantity(value: BigDecimal, unit: String) = value.stripTrailingZeros().toPlainString() + " " + unit

/** Exhaustive farm-owned queries. A plan is not occupancy, an issue is not measured intake. */
internal suspend fun loadCapacityModel(db: FarmOsDatabase, farm: String, today: LocalDate): CapacityModel {
    val houses = db.lifecycle().houses(farm)
    val placements = db.lifecycle().poultryPlacementTotals(farm).flatMap { db.lifecycle().placementsForGroup(farm, it.groupId) }
    val byHouse = placements.groupBy { it.houseId }
    val houseBars = houses.map { house ->
        val rows = byHouse[house.id].orEmpty()
        val head = rows.sumOf { it.headCount.toLong() }
        FarmBarValue(house.code, head.toDouble(), "$head head recorded in placements", "${house.poultryKindCode} · ${rows.size} placement events · capacity not recorded", "capacity-house:${house.id}")
    }
    val plans = db.rabbitProgramme().waves(farm)
    val cages = db.rabbitProgramme().cages(farm).map { cage ->
        val waves = plans.filter { it.cageId == cage.id }.sortedBy { it.matingEpochDay }
        CapacityCage(cage, waves, waves.filter { today.toEpochDay() in it.matingEpochDay..it.weanEpochDay }.sumOf { it.doeCount.toLong() })
    }
    val summaries = db.grazing().summaryByPaddock(farm).associateBy { it.paddockId }
    val paddocks = db.paddocks().active(farm).map { p ->
        val stats = summaries[p.id]
        PaddockView(p.id, p.code, p.displayName, p.areaM2, p.waterSource, p.shade, p.active,
            stats?.sessionCount ?: 0, stats?.openSessions ?: 0, stats?.openHeadCount ?: 0,
            stats?.latestEnteredEpochDay, stats?.latestExitedEpochDay)
    }
    val stocking = paddocks.mapNotNull { p ->
        val area = p.areaM2?.takeIf { it > 0 } ?: return@mapNotNull null
        val density = BigDecimal.valueOf(p.openHeadCount.toLong()).multiply(BigDecimal.valueOf(10_000))
            .divide(BigDecimal.valueOf(area.toLong()), 2, RoundingMode.HALF_UP)
        FarmBarValue(p.displayName, density.toDouble(), capacityQuantity(density, "head/ha"), "${p.openHeadCount} head recorded at entry · $area m²", "capacity-paddock:${p.id}")
    }
    val items = db.inventory().items(farm).associateBy { it.id }
    val feed = db.feedIssues().totalsByItem(farm).map { total ->
        val item = items[total.itemId]
        CapacityUsage(total.itemId, item?.name ?: total.itemId, item?.unit, total.quantityMilli,
            total.issueCount, total.firstEpochDay, total.latestEpochDay, item?.quantityMilli)
    }
    val water = db.water().totalsBySource(farm).map { total ->
        CapacityUsage("water:${total.source}", total.source, "L", total.litresMilli, total.recordCount, total.firstEpochDay, total.latestEpochDay)
    }
    val alerts = buildList {
        cages.filter { it.plannedDoesToday > it.cage.doeCapacity }.forEach {
            add("${it.cage.code}: ${it.plannedDoesToday} does scheduled today against ${it.cage.doeCapacity} recorded doe places. Review overlapping plans.")
        }
        items.values.filter { it.reorderMilli > 0 && it.quantityMilli <= it.reorderMilli }.forEach {
            add("${it.name}: ${capacityQuantity(BigDecimal.valueOf(it.quantityMilli, 3), it.unit)} on hand; at or below the reorder point.")
        }
    }
    return CapacityModel(houseBars, cages, PastureRecords(paddocks = paddocks), stocking, feed, water, alerts)
}
