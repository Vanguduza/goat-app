package com.farmos.app

import com.farmos.core.database.FarmOsDatabase
import com.farmos.feature.ops.FormularyItemView
import com.farmos.feature.ops.HealthLabResultView
import com.farmos.feature.ops.HealthReadModel
import com.farmos.feature.ops.HealthTimelineEntry
import com.farmos.feature.ops.HealthTimelineKind
import com.farmos.feature.ops.HealthTimelineStream
import com.farmos.feature.ops.HealthTreatmentView
import com.farmos.feature.ops.HealthVetVisitView
import com.farmos.feature.ops.HealthWithdrawalView
import com.farmos.feature.ops.ProtocolPackView
import com.farmos.feature.ops.ProtocolSlotView
import com.farmos.feature.ops.mergeHealthTimeline
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal const val HEALTH_RECORD_LIMIT = 200

/**
 * Farm-scoped read model for the read-only health record pages. Nothing here writes or prescribes.
 * Lists are the latest [HEALTH_RECORD_LIMIT] rows; the counts are exhaustive farm-scoped
 * aggregates, and names come from chunked farm-scoped bulk lookups.
 */
internal suspend fun loadHealthReadModel(
    database: FarmOsDatabase,
    farmId: String,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): HealthReadModel {
    val lifecycle = database.lifecycle()
    val treatmentRows = database.treatments().recent(farmId, HEALTH_RECORD_LIMIT)
    val vetVisitRows = lifecycle.vetVisits(farmId, HEALTH_RECORD_LIMIT)
    val labRows = lifecycle.labResults(farmId, HEALTH_RECORD_LIMIT)
    val observationRows = database.healthObservations().recent(farmId, HEALTH_RECORD_LIMIT)
    val animalIds = (treatmentRows.map { it.animalId } + vetVisitRows.map { it.animalId } + labRows.map { it.animalId } + observationRows.map { it.animalId })
        .filterNotNull().distinct()
    val labels = animalIds.chunked(LOOKUP_CHUNK).flatMap { database.animals().getMany(farmId, it) }.associate { animal ->
        animal.id to (animal.name?.takeIf { it.isNotBlank() }?.let { "${animal.tag} · $it" } ?: animal.tag)
    }
    fun subject(animalId: String?): String? = animalId?.let { labels[it] ?: "Animal not on this device" }
    val products = treatmentRows.map { it.formularyItemId }.distinct().chunked(LOOKUP_CHUNK)
        .flatMap { database.formulary().getMany(farmId, it) }
        .associate { it.id to it.productName }
    fun day(epochMillis: Long): Long = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate().toEpochDay()
    val observationCount = database.healthObservations().count(farmId)
    val treatmentCount = database.treatments().count(farmId)
    val vetVisitCount = lifecycle.vetVisitCount(farmId)
    val labResultCount = lifecycle.labResultCount(farmId)
    val timeline = mergeHealthTimeline(
        listOf(
            HealthTimelineStream(
                observationRows.map {
                    HealthTimelineEntry(
                        HealthTimelineKind.OBSERVATION, it.id, day(it.occurredAtEpochMillis), subject(it.animalId), it.speciesCode,
                        if (it.redFlag) "${it.signs} · Red flag" else it.signs,
                    )
                },
                observationRows.size >= HEALTH_RECORD_LIMIT,
            ),
            HealthTimelineStream(
                treatmentRows.map {
                    HealthTimelineEntry(
                        HealthTimelineKind.TREATMENT, it.id, day(it.occurredAtEpochMillis), subject(it.animalId), it.speciesCode,
                        "${products[it.formularyItemId] ?: "Product not on this device"} · ${it.reason}",
                    )
                },
                treatmentRows.size >= HEALTH_RECORD_LIMIT,
            ),
            HealthTimelineStream(
                vetVisitRows.map {
                    HealthTimelineEntry(HealthTimelineKind.VET_VISIT, it.id, it.occurredEpochDay, subject(it.animalId), it.speciesCode, "${it.reason} · ${it.attendingVet}")
                },
                vetVisitRows.size >= HEALTH_RECORD_LIMIT,
            ),
            HealthTimelineStream(
                labRows.map {
                    HealthTimelineEntry(HealthTimelineKind.LAB_RESULT, it.id, it.occurredEpochDay, subject(it.animalId), null, "${it.testName}: ${it.resultText}")
                },
                labRows.size >= HEALTH_RECORD_LIMIT,
            ),
        ),
        totalCount = observationCount + treatmentCount + vetVisitCount + labResultCount,
    )
    return HealthReadModel(
        treatments = treatmentRows.map { row ->
            HealthTreatmentView(
                id = row.id,
                speciesCode = row.speciesCode,
                subjectLabel = subject(row.animalId),
                productName = products[row.formularyItemId],
                reason = row.reason,
                meatWithdrawalDays = row.meatWithdrawalDays,
                milkWithdrawalDays = row.milkWithdrawalDays,
                eggWithdrawalDays = row.eggWithdrawalDays,
                occurredAtEpochMillis = row.occurredAtEpochMillis,
            )
        },
        withdrawals = lifecycle.withdrawals(farmId, HEALTH_RECORD_LIMIT).map {
            HealthWithdrawalView(it.id, it.treatmentId, it.product, it.windowKind, it.endsEpochDay)
        },
        vetVisits = vetVisitRows.map {
            HealthVetVisitView(it.id, it.speciesCode, subject(it.animalId), it.reason, it.attendingVet, it.occurredEpochDay)
        },
        labResults = labRows.map {
            HealthLabResultView(it.id, subject(it.animalId), it.testName, it.resultText, it.cellsPerMl, it.occurredEpochDay)
        },
        observationCount = observationCount,
        treatmentCount = treatmentCount,
        activeWithdrawalCount = lifecycle.activeWithdrawalCount(farmId, today.toEpochDay()),
        vetVisitCount = vetVisitCount,
        labResultCount = labResultCount,
        formulary = loadFormularyViews(database, farmId),
        packs = loadProtocolPackViews(database, farmId),
        timeline = timeline,
    )
}

/** Every formulary item on the farm with the exhaustive count of treatments recorded against it. */
private suspend fun loadFormularyViews(database: FarmOsDatabase, farmId: String): List<FormularyItemView> {
    val counts = database.treatments().countsByFormularyItem(farmId).associate { it.key to it.count }
    return database.formulary().forFarm(farmId).map {
        FormularyItemView(it.id, it.productName, it.speciesCode, it.vetClass, it.meatWithdrawalDays, it.milkWithdrawalDays, it.eggWithdrawalDays, it.vetApproved, counts[it.id] ?: 0)
    }
}

/** Every protocol pack on the farm with its recorded slots and exhaustive application count. */
private suspend fun loadProtocolPackViews(database: FarmOsDatabase, farmId: String): List<ProtocolPackView> {
    val lifecycle = database.lifecycle()
    val slots = lifecycle.packSlots(farmId).groupBy { it.packId }
    val applications = lifecycle.packApplicationCounts(farmId).associate { it.key to it.count }
    return lifecycle.packs(farmId).map { pack ->
        ProtocolPackView(
            id = pack.id,
            name = pack.name,
            speciesCode = pack.speciesCode,
            status = pack.status,
            acceptedByVet = pack.acceptedByVet,
            slots = slots[pack.id].orEmpty().map { ProtocolSlotView(it.id, it.slotCode, it.title, it.offsetDays, it.fromEvent, it.isCore) },
            applicationCount = applications[pack.id] ?: 0,
        )
    }
}
