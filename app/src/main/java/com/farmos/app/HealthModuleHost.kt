package com.farmos.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.ops.AcceptHealthPack
import com.farmos.domain.ops.AddHealthPackSlot
import com.farmos.domain.ops.ApplyHealthPack
import com.farmos.domain.ops.CreateFormularyItem
import com.farmos.domain.ops.RecordHealthObservation
import com.farmos.domain.ops.RecordHealthTreatment
import com.farmos.domain.ops.RecordLabResult
import com.farmos.domain.ops.RecordVetVisit
import com.farmos.feature.ops.HealthObservationScreen
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.launch

@Composable
fun HealthModuleHost(
    farmId: String,
    ops: RoomOpsRepository,
    newContext: () -> LocalCommandContext,
    enqueueSync: () -> Unit,
    onBack: () -> Unit,
    openTreatment: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    var observations by remember(farmId) { mutableStateOf(emptyList<String>()) }
    var catalog by remember(farmId) { mutableStateOf(emptyList<String>()) }
    var treatments by remember(farmId) { mutableStateOf(emptyList<String>()) }
    var formulary by remember(farmId) { mutableStateOf(emptyList<String>()) }
    var packs by remember(farmId) { mutableStateOf(emptyList<String>()) }
    var withdrawals by remember(farmId) { mutableStateOf(emptyList<String>()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        observations = ops.recentObservations().map { row -> "${row.speciesCode} · ${row.signs}" }
        catalog = ops.diseases().map { row -> "${row.speciesCode} · ${row.displayName} · ${row.firstAid}" }
        formulary = ops.approvedFormulary().map { row -> "${row.id} · ${row.productName} · ${row.speciesCode} · ${row.vetClass}" }
        treatments = ops.recentTreatments().map { row ->
            "${row.speciesCode} · ${row.reason} · formulary ${row.formularyItemId}"
        }
        packs = ops.packs().map { row ->
            "${row.speciesCode} · ${row.name} · ${row.status} · ${row.acceptedByVet.orEmpty()}"
        }
        withdrawals = ops.withdrawals().map { row ->
            "${row.windowKind} · ${row.product} ends day ${row.endsEpochDay}"
        }
    }

    fun runWrite(block: suspend () -> Unit) {
        scope.launch {
            busy = true
            error = null
            runCatching {
                block()
                refresh()
            }.onSuccess {
                enqueueSync()
            }.onFailure { failure ->
                error = failure.message
            }
            busy = false
        }
    }

    LaunchedEffect(farmId) {
        runCatching { refresh() }
            .onFailure { error = it.message }
    }

    HealthObservationScreen(
        rows = observations,
        catalog = catalog,
        treatments = treatments,
        formulary = formulary,
        packs = packs,
        withdrawals = withdrawals,
        busy = busy,
        error = error,
        onRecord = { species, signs, firstAid, redFlag ->
            runWrite {
                ops.recordObservation(
                    RecordHealthObservation(
                        observationId = UUID.randomUUID().toString(),
                        speciesCode = species,
                        signs = signs,
                        firstAidApplied = firstAid.trim().ifBlank { null },
                        redFlag = redFlag,
                        occurredAtEpochMillis = System.currentTimeMillis(),
                    ),
                    newContext(),
                )
            }
        },
        onCreateFormulary = { product, species, vetClass, meatDays ->
            runWrite {
                ops.createFormulary(
                    CreateFormularyItem(
                        itemId = UUID.randomUUID().toString(),
                        productName = product,
                        speciesCode = species,
                        vetClass = vetClass,
                        meatWithdrawalDays = meatDays,
                        vetApproved = true,
                    ),
                    newContext(),
                )
            }
        },
        onRecordTreatment = { species, formularyItemId, reason ->
            runWrite {
                ops.recordTreatment(
                    RecordHealthTreatment(
                        treatmentId = UUID.randomUUID().toString(),
                        speciesCode = species,
                        formularyItemId = formularyItemId,
                        reason = reason,
                        occurredAtEpochMillis = System.currentTimeMillis(),
                    ),
                    newContext(),
                )
            }
        },
        onAcceptPack = { species, name, vet ->
            runWrite {
                ops.acceptPack(
                    AcceptHealthPack(UUID.randomUUID().toString(), species, name, vet),
                    newContext(),
                )
            }
        },
        onRecordVetVisit = { species, reason, vet, day ->
            runWrite {
                ops.recordVetVisit(
                    RecordVetVisit(
                        visitId = UUID.randomUUID().toString(),
                        speciesCode = species,
                        reason = reason,
                        attendingVet = vet,
                        occurredEpochDay = LocalDate.parse(day).toEpochDay(),
                    ),
                    newContext(),
                )
            }
        },
        onRecordLab = { animalId, testName, resultText, cells, day ->
            runWrite {
                ops.recordLab(
                    RecordLabResult(
                        resultId = UUID.randomUUID().toString(),
                        animalId = animalId.trim().ifBlank { null },
                        testName = testName,
                        resultText = resultText,
                        cellsPerMl = cells.toIntOrNull(),
                        occurredEpochDay = LocalDate.parse(day).toEpochDay(),
                    ),
                    newContext(),
                )
            }
        },
        onAddPackSlot = { packId, slotCode, title, offset, fromEvent ->
            runWrite {
                ops.addPackSlot(
                    AddHealthPackSlot(
                        slotId = UUID.randomUUID().toString(),
                        packId = packId,
                        slotCode = slotCode,
                        title = title,
                        offsetDays = offset.toIntOrNull() ?: 0,
                        fromEvent = fromEvent,
                    ),
                    newContext(),
                )
            }
        },
        onApplyPack = { packId, animalId, day ->
            runWrite {
                ops.applyPack(
                    ApplyHealthPack(
                        applyId = UUID.randomUUID().toString(),
                        packId = packId,
                        animalId = animalId.trim().ifBlank { null },
                        anchorEpochDay = LocalDate.parse(day).toEpochDay(),
                    ),
                    newContext(),
                )
            }
        },
        onBack = onBack,
        openTreatment = openTreatment,
    )
}
