package com.farmos.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.farmos.core.design.FarmSelectorSearch
import com.farmos.core.design.NoFarmSelectorSearch
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.ops.AcceptHealthPack
import com.farmos.domain.ops.AddHealthPackSlot
import com.farmos.domain.ops.ApplyHealthPack
import com.farmos.domain.ops.CreateFormularyItem
import com.farmos.domain.ops.FarmSpeciesCodes
import com.farmos.domain.ops.RecordHealthObservation
import com.farmos.domain.ops.RecordHealthTreatment
import com.farmos.domain.ops.RecordHealthVaccination
import com.farmos.domain.ops.RecordLabResult
import com.farmos.domain.ops.RecordVetVisit
import com.farmos.feature.ops.HealthReadModel
import com.farmos.feature.ops.HealthEntryPage
import com.farmos.feature.ops.HealthObservationScreen
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import com.farmos.core.design.runSuspendCatching

@Composable
fun HealthModuleHost(
    farmId: String,
    ops: RoomOpsRepository,
    newContext: () -> LocalCommandContext,
    enqueueSync: () -> Unit,
    onBack: () -> Unit,
    entryPage: HealthEntryPage = HealthEntryPage.DASHBOARD,
    loadReadModel: suspend () -> HealthReadModel = { HealthReadModel() },
    searchAnimals: FarmSelectorSearch = NoFarmSelectorSearch,
) {
    val scope = rememberCoroutineScope()
    var readModel by remember(farmId) { mutableStateOf(HealthReadModel()) }
    var resources by remember(farmId) { mutableStateOf(HealthModuleResources()) }
    var vaccinationData by remember(farmId) { mutableStateOf(HealthVaccinationRecords()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val zone = ZoneId.systemDefault()
    suspend fun refresh() {
        resources = loadHealthModuleResources(ops)
        readModel = loadReadModel()
        vaccinationData = loadHealthVaccinations(ops, searchAnimals, readModel, zone)
    }

    fun runWrite(block: suspend () -> Unit) = launchCommittedModuleWrite(
        scope = scope,
        write = block,
        isBusy = { busy },
        setBusy = { busy = it },
        setError = { error = it },
        enqueueSync = enqueueSync,
        refresh = ::refresh,
    )

    LaunchedEffect(farmId) {
        runSuspendCatching { refresh() }
            .onFailure { error = it.message }
    }

    HealthObservationScreen(
        rows = resources.observations,
        catalog = resources.catalog,
        treatments = resources.treatments,
        formulary = resources.formulary,
        formularyOptions = resources.formularyOptions,
        speciesCodes = FarmSpeciesCodes.ALL,
        acceptedPacks = resources.acceptedPacks,
        searchAnimals = searchAnimals,
        packs = resources.packs,
        withdrawals = resources.withdrawals,
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
        onRecordVaccination = { species, animalId, groupId, formularyItemId, dose, method, day ->
            runWrite {
                ops.recordVaccination(
                    RecordHealthVaccination(
                        vaccinationId = UUID.randomUUID().toString(),
                        speciesCode = species,
                        formularyItemId = formularyItemId,
                        animalId = animalId?.trim()?.ifBlank { null },
                        groupId = groupId?.trim()?.ifBlank { null },
                        dose = dose?.trim()?.ifBlank { null },
                        method = method?.trim()?.ifBlank { null },
                        occurredAtEpochMillis = LocalDate.parse(day).atStartOfDay(zone).toInstant().toEpochMilli(),
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
        entryPage = entryPage,
        readModel = readModel,
        vaccinations = vaccinationData.vaccinations,
        dueCandidates = vaccinationData.reviewCandidates,
        groupOptions = vaccinationData.groups,
        reportStats = vaccinationData.stats,
        referenceDetails = resources.referenceDetails,
        redFlagObservations = resources.redFlagObservations,
    )
}
