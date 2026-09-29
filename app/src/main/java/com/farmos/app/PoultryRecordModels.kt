package com.farmos.app

import com.farmos.core.database.FarmOsDatabase
import com.farmos.feature.ops.PoultryDayView
import com.farmos.feature.ops.PoultryFlockView
import com.farmos.feature.ops.PoultryHatchView
import com.farmos.feature.ops.PoultryHouseView
import com.farmos.feature.ops.PoultryPlacementView
import com.farmos.feature.ops.PoultryRecords
import com.farmos.feature.ops.PoultryVaccinationView
import com.farmos.feature.ops.PoultryWalkView

private const val POULTRY_RECORD_LIMIT = 500

/** Farm-scoped read model for the read-only poultry record pages. Nothing here writes. */
internal suspend fun loadPoultryRecords(database: FarmOsDatabase, farmId: String): PoultryRecords {
    val lifecycle = database.lifecycle()
    val houses = lifecycle.houses(farmId)
    val houseCodes = houses.associate { it.id to it.code }
    fun houseLabel(id: String?) = id?.let { houseCodes[it] ?: "House not on this device" }
    val placements = lifecycle.placements(farmId, POULTRY_RECORD_LIMIT)
    val vaccinations = lifecycle.vaccinations(farmId, POULTRY_RECORD_LIMIT)
    val products = mutableMapOf<String, String>()
    suspend fun product(id: String) = products.getOrPut(id) { database.formulary().get(farmId, id)?.productName ?: "Product not on this device" }
    val rawWalks = lifecycle.biosecurityWalks(farmId, POULTRY_RECORD_LIMIT)
    val walks = rawWalks.map { walk ->
        val subject = listOfNotNull(houseLabel(walk.houseId)?.let { "House $it" }, walk.groupId?.let { "Flock $it" }).joinToString(" · ").ifBlank { "Farm" }
        PoultryWalkView(walk.id, walk.occurredEpochDay, subject, walk.findings, walk.mixedSpecies)
    }
    val flocks = placements.groupBy { it.groupId }.map { (groupId, rows) ->
        PoultryFlockView(
            groupId = groupId,
            poultryKind = rows.first().poultryKindCode,
            houseLabel = rows.map { houseLabel(it.houseId) ?: "" }.distinct().joinToString(", "),
            placedHeads = rows.sumOf { it.headCount },
            firstPlacedEpochDay = rows.minOf { it.occurredEpochDay },
            days = database.poultryFlockDays().forGroup(farmId, groupId).map { PoultryDayView(it.occurredEpochDay, it.eggs, it.dead, it.culls, it.feedGrams) },
            vaccinations = vaccinations.filter { it.groupId == groupId }.map { PoultryVaccinationView(it.id, it.occurredEpochDay, product(it.formularyItemId)) },
        )
    }.sortedBy { it.groupId }
    return PoultryRecords(
        flocks = flocks,
        houses = houses.map { house ->
            PoultryHouseView(
                id = house.id,
                code = house.code,
                houseKind = house.kind,
                poultryKind = house.poultryKindCode,
                placements = placements.filter { it.houseId == house.id }.map { PoultryPlacementView(it.id, it.groupId, it.poultryKindCode, it.headCount, it.occurredEpochDay) },
                walks = rawWalks.filter { it.houseId == house.id }.map { walk ->
                    PoultryWalkView(walk.id, walk.occurredEpochDay, "House ${house.code}", walk.findings, walk.mixedSpecies)
                },
            )
        },
        hatches = lifecycle.hatches(farmId).map { hatch ->
            PoultryHatchView(
                id = hatch.id,
                poultryKind = hatch.poultryKindCode,
                setEpochDay = hatch.setEpochDay,
                eggsSet = hatch.eggsSet,
                incubationDays = hatch.incubationDays,
                status = hatch.status,
                location = listOfNotNull(houseLabel(hatch.houseId)?.let { "House $it" }, hatch.groupId?.let { "Flock $it" }).joinToString(" · ").ifBlank { "Not recorded" },
                fertile = hatch.fertile,
                infertile = hatch.infertile,
                midDead = hatch.midDead,
                hatched = hatch.hatched,
                culls = hatch.culls,
                placementGroupId = hatch.placementGroupId,
            )
        },
        walks = walks,
    )
}
