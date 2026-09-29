package com.farmos.app

import com.farmos.core.database.FarmOsDatabase
import com.farmos.feature.rabbit.RabbitCageView
import com.farmos.feature.rabbit.RabbitKitView
import com.farmos.feature.rabbit.RabbitNestBoxView
import com.farmos.feature.rabbit.RabbitRecords
import com.farmos.feature.rabbit.RabbitWaveEventView
import com.farmos.feature.rabbit.RabbitWaveView

private const val RABBIT_RECORD_LIMIT = 500

/** Farm-scoped read model for the read-only rabbitry record pages. Nothing here writes. */
internal suspend fun loadRabbitRecords(database: FarmOsDatabase, farmId: String): RabbitRecords {
    val programme = database.rabbitProgramme()
    val lifecycle = database.lifecycle()
    val cages = programme.cages(farmId)
    val cageCodes = cages.associate { it.id to it.code }
    val boxes = programme.boxes(farmId)
    val waves = programme.waves(farmId)
    val kindlings = lifecycle.rabbitKindlings(farmId, RABBIT_RECORD_LIMIT)
    val events = buildList {
        lifecycle.rabbitPalpations(farmId, RABBIT_RECORD_LIMIT).forEach {
            add(it.waveId to RabbitWaveEventView(it.id, it.occurredEpochDay, "Palpation", it.result))
        }
        kindlings.forEach {
            add(it.waveId to RabbitWaveEventView(it.id, it.occurredEpochDay, "Kindling", "${it.liveCount} live · ${it.deadCount} dead"))
        }
        lifecycle.rabbitFosters(farmId, RABBIT_RECORD_LIMIT).forEach {
            val window = if (it.withinWindow) "" else " · outside window, acknowledged"
            add(it.fromWaveId to RabbitWaveEventView("${it.id}:out", it.occurredEpochDay, "Foster out", "${it.kitCount} kits$window"))
            add(it.toWaveId to RabbitWaveEventView("${it.id}:in", it.occurredEpochDay, "Foster in", "${it.kitCount} kits$window"))
        }
        lifecycle.rabbitWeans(farmId, RABBIT_RECORD_LIMIT).forEach {
            add(it.waveId to RabbitWaveEventView(it.id, it.occurredEpochDay, "Weaning", "${it.weanedCount} weaned"))
        }
        lifecycle.rabbitMatingOutcomes(farmId, RABBIT_RECORD_LIMIT).forEach {
            add(it.waveId to RabbitWaveEventView(it.id, it.occurredEpochDay, "Outcome", it.outcome))
        }
    }.groupBy({ it.first }, { it.second })
    val kindledWaves = lifecycle.rabbitKindledWaveIds(farmId).toSet()
    return RabbitRecords(
        cages = cages.map { cage ->
            RabbitCageView(
                id = cage.id,
                code = cage.code,
                doeCapacity = cage.doeCapacity,
                nestBoxes = boxes.filter { it.cageId == cage.id }.map { RabbitNestBoxView(it.id, it.code, it.status) },
                waveIds = waves.filter { it.cageId == cage.id }.map { it.id },
            )
        },
        waves = waves.map { wave ->
            RabbitWaveView(
                id = wave.id,
                cageLabel = cageCodes[wave.cageId] ?: "not on this device",
                doeCount = wave.doeCount,
                matingEpochDay = wave.matingEpochDay,
                nestInEpochDay = wave.nestInEpochDay,
                kindlingEpochDay = wave.kindlingEpochDay,
                nestOutEpochDay = wave.nestOutEpochDay,
                rebreedEpochDay = wave.rebreedEpochDay,
                weanEpochDay = wave.weanEpochDay,
                events = events[wave.id].orEmpty().sortedWith(compareByDescending<RabbitWaveEventView> { it.epochDay }.thenBy { it.id }),
                kindlingRecorded = wave.id in kindledWaves,
            )
        },
        kits = lifecycle.kits(farmId).map { RabbitKitView(it.id, it.waveId, it.tempLabel, it.sex, it.status, it.retention, it.earTag) },
    )
}
