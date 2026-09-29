package com.farmos.app

import com.farmos.core.database.FarmOsDatabase
import com.farmos.feature.ops.HealthLabResultView
import com.farmos.feature.ops.HealthReadModel
import com.farmos.feature.ops.HealthTreatmentView
import com.farmos.feature.ops.HealthVetVisitView
import com.farmos.feature.ops.HealthWithdrawalView

internal const val HEALTH_RECORD_LIMIT = 200

/** Farm-scoped read model for the read-only health record pages. Nothing here writes or prescribes. */
internal suspend fun loadHealthReadModel(database: FarmOsDatabase, farmId: String): HealthReadModel {
    val labels = mutableMapOf<String, String?>()
    suspend fun subject(animalId: String?): String? {
        if (animalId == null) return null
        return labels.getOrPut(animalId) {
            database.animals().get(farmId, animalId)?.let { animal ->
                animal.name?.takeIf { it.isNotBlank() }?.let { "${animal.tag} · $it" } ?: animal.tag
            } ?: "Animal not on this device"
        }
    }
    val products = mutableMapOf<String, String?>()
    val treatments = database.treatments().recent(farmId, HEALTH_RECORD_LIMIT).map { row ->
        HealthTreatmentView(
            id = row.id,
            speciesCode = row.speciesCode,
            subjectLabel = subject(row.animalId),
            productName = products.getOrPut(row.formularyItemId) { database.formulary().get(farmId, row.formularyItemId)?.productName },
            reason = row.reason,
            meatWithdrawalDays = row.meatWithdrawalDays,
            milkWithdrawalDays = row.milkWithdrawalDays,
            eggWithdrawalDays = row.eggWithdrawalDays,
            occurredAtEpochMillis = row.occurredAtEpochMillis,
        )
    }
    return HealthReadModel(
        treatments = treatments,
        withdrawals = database.lifecycle().withdrawals(farmId, HEALTH_RECORD_LIMIT).map {
            HealthWithdrawalView(it.id, it.treatmentId, it.product, it.windowKind, it.endsEpochDay)
        },
        vetVisits = database.lifecycle().vetVisits(farmId, HEALTH_RECORD_LIMIT).map {
            HealthVetVisitView(it.id, it.speciesCode, subject(it.animalId), it.reason, it.attendingVet, it.occurredEpochDay)
        },
        labResults = database.lifecycle().labResults(farmId, HEALTH_RECORD_LIMIT).map {
            HealthLabResultView(it.id, subject(it.animalId), it.testName, it.resultText, it.cellsPerMl, it.occurredEpochDay)
        },
    )
}
