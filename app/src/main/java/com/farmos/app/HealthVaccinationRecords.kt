package com.farmos.app

import com.farmos.core.database.DiseaseCatalogEntity
import com.farmos.core.design.FarmSelectorOption
import com.farmos.core.design.FarmSelectorSearch
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.feature.ops.HealthReadModel
import com.farmos.feature.ops.HealthReferenceDetail
import com.farmos.feature.ops.HealthReportStats
import com.farmos.feature.ops.HealthVaccinationView
import com.farmos.feature.ops.VaccinationDueView
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal data class HealthVaccinationRecords(
    val vaccinations: List<HealthVaccinationView> = emptyList(),
    val reviewCandidates: List<VaccinationDueView> = emptyList(),
    val groups: List<FarmSelectorOption> = emptyList(),
    val stats: HealthReportStats? = null,
)

internal fun healthReferenceDetail(row: DiseaseCatalogEntity) = HealthReferenceDetail(
    row.code, row.speciesCode, row.displayName, row.signs, row.firstAid, row.prevention, row.vetClass, row.redFlag,
)

/** A recorded date is evidence, never an inferred clinical due date. Group coverage is not assumed. */
internal fun vaccinationRecordReview(
    animalId: String,
    label: String,
    latestEpochMillis: Long?,
    today: LocalDate,
    zone: ZoneId,
): VaccinationDueView {
    val date = latestEpochMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
    return VaccinationDueView(
        animalId, label,
        date?.let { "Last individual record: $it · No next due date recorded" }
            ?: "No individual vaccination record · No next due date recorded",
        date?.let { today.toEpochDay() - it.toEpochDay() },
    )
}

/** Whole-farm roster paged to completion; the recent record list alone never determines coverage. */
internal suspend fun loadHealthVaccinations(
    ops: RoomOpsRepository,
    searchAnimals: FarmSelectorSearch,
    counts: HealthReadModel,
    zone: ZoneId,
    today: LocalDate = LocalDate.now(zone),
): HealthVaccinationRecords {
    val roster = mutableListOf<FarmSelectorOption>()
    var offset = 0
    while (true) {
        val page = searchAnimals.page("", offset, 200)
        roster += page.options
        if (!page.hasMore) break
        check(page.options.isNotEmpty()) { "Animal search returned an incomplete page" }
        offset += page.options.size
    }
    val labels = roster.associate { it.id to it.label }
    val activeIds = roster.filter { it.detail == "Active" || it.detail?.endsWith(" · Active") == true }.map { it.id }.distinct()
    val groups = ops.groups()
    val groupNames = groups.associate { it.id to it.name }
    val formularyNames = ops.approvedFormulary().associate { it.id to it.productName }
    val recent = ops.recentVaccinations()
    val views = recent.map { record ->
        val subject = record.animalId?.let { labels[it] ?: "Animal $it" }
            ?: record.groupId?.let { "Group ${groupNames[it] ?: it}" } ?: record.speciesCode
        HealthVaccinationView(
            record.id, subject, record.speciesCode, formularyNames[record.formularyItemId] ?: record.formularyItemId,
            record.dose, record.method, Instant.ofEpochMilli(record.occurredAtEpochMillis).atZone(zone).toLocalDate().toEpochDay(),
        )
    }
    var everVaccinated = 0
    val review = activeIds.map { animalId ->
        val latest = ops.vaccinationsForAnimal(animalId).maxOfOrNull { it.occurredAtEpochMillis }
        if (latest != null) everVaccinated++
        vaccinationRecordReview(animalId, labels[animalId] ?: animalId, latest, today, zone)
    }.sortedWith(compareBy<VaccinationDueView> { it.daysSinceLast != null }.thenByDescending { it.daysSinceLast ?: -1 })
    return HealthVaccinationRecords(
        views, review,
        groups.map { FarmSelectorOption(it.id, it.name, "${it.speciesCode} · ${it.headCount} head") },
        HealthReportStats(
            activeIds.size, everVaccinated, review.size, recent.size,
            counts.treatmentCount, counts.observationCount, counts.activeWithdrawalCount,
        ),
    )
}
