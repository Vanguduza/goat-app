package com.farmos.app

import com.farmos.core.design.FarmSelectorOption
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.feature.ops.HealthReferenceDetail

internal data class HealthModuleResources(
    val observations: List<String> = emptyList(),
    val treatments: List<String> = emptyList(),
    val catalog: List<String> = emptyList(),
    val formulary: List<String> = emptyList(),
    val formularyOptions: List<FarmSelectorOption> = emptyList(),
    val acceptedPacks: List<FarmSelectorOption> = emptyList(),
    val packs: List<String> = emptyList(),
    val withdrawals: List<String> = emptyList(),
    val referenceDetails: List<HealthReferenceDetail> = emptyList(),
    val redFlagObservations: List<String> = emptyList(),
)

/** Reads supporting catalog and current health resources; no business writes originate here. */
internal suspend fun loadHealthModuleResources(ops: RoomOpsRepository): HealthModuleResources {
        val observationRows = ops.recentObservations()
        val observations = observationRows.map { row -> "${row.speciesCode} · ${row.signs}" }
        val redFlagObservations = observationRows.filter { it.redFlag }
            .map { row -> "${row.speciesCode} · ${row.signs}" }
        val diseaseRows = ops.diseases()
        val catalog = diseaseRows.map { row -> "${row.speciesCode} · ${row.displayName} · ${row.firstAid}" }
        val referenceDetails = diseaseRows.map(::healthReferenceDetail)
        val approved = ops.approvedFormulary()
        val formulary = approved.map { row -> "${row.id} · ${row.productName} · ${row.speciesCode} · ${row.vetClass}" }
        val formularyOptions = approved.map { row -> FarmSelectorOption(row.id, row.productName, "${row.speciesCode} · ${row.vetClass}") }
        val treatments = ops.recentTreatments().map { row ->
            "${row.speciesCode} · ${row.reason} · formulary ${row.formularyItemId}"
        }
        val packRows = ops.packs()
        val packs = packRows.map { row ->
            "${row.speciesCode} · ${row.name} · ${row.status} · ${row.acceptedByVet.orEmpty()}"
        }
        val acceptedPacks = packRows.filter { it.status == "vet_accepted" }
            .map { row -> FarmSelectorOption(row.id, row.name, "${row.speciesCode} · accepted by ${row.acceptedByVet.orEmpty()}") }
        val withdrawals = ops.withdrawals().map { row ->
            "${row.windowKind} · ${row.product} ends day ${row.endsEpochDay}"
        }
    return HealthModuleResources(observations, treatments, catalog, formulary, formularyOptions, acceptedPacks, packs, withdrawals, referenceDetails, redFlagObservations)
}
