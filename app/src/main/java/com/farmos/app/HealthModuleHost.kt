package com.farmos.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.farmos.core.design.FarmSelectorOption
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
import com.farmos.feature.ops.HealthReferenceDetail
import com.farmos.feature.ops.HealthEntryPage
import com.farmos.feature.ops.HealthObservationScreen
import com.farmos.feature.ops.HealthReportStats
import com.farmos.feature.ops.HealthVaccinationView
import com.farmos.feature.ops.VaccinationDueView
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.launch

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
    var observations by remember(farmId) { mutableStateOf(emptyList<String>()) }
    var catalog by remember(farmId) { mutableStateOf(emptyList<String>()) }
    var treatments by remember(farmId) { mutableStateOf(emptyList<String>()) }
    var formulary by remember(farmId) { mutableStateOf(emptyList<String>()) }
    var formularyOptions by remember(farmId) { mutableStateOf(emptyList<FarmSelectorOption>()) }
    var acceptedPacks by remember(farmId) { mutableStateOf(emptyList<FarmSelectorOption>()) }
    var packs by remember(farmId) { mutableStateOf(emptyList<String>()) }
    var withdrawals by remember(farmId) { mutableStateOf(emptyList<String>()) }
    var vaccinationViews by remember(farmId) { mutableStateOf(emptyList<HealthVaccinationView>()) }
    var dueViews by remember(farmId) { mutableStateOf(emptyList<VaccinationDueView>()) }
    var groupOptions by remember(farmId) { mutableStateOf(emptyList<FarmSelectorOption>()) }
    var reportStats by remember(farmId) { mutableStateOf<HealthReportStats?>(null) }
    var referenceDetails by remember(farmId) { mutableStateOf(emptyList<HealthReferenceDetail>()) }
    var redFlagObservations by remember(farmId) { mutableStateOf(emptyList<String>()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val zone = ZoneId.systemDefault()
    fun epochDay(millis: Long): Long = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay()

    suspend fun refreshVaccinations() {
        val formularyNames = ops.approvedFormulary().associate { it.id to it.productName }
        // Whole-farm roster, paged to completion; never a capped presentation list.
        val roster = mutableListOf<FarmSelectorOption>()
        var offset = 0
        while (true) {
            val page = searchAnimals.page("", offset, 200)
            roster += page.options
            if (!page.hasMore) break
            offset += page.options.size
        }
        val labels = roster.associate { it.id to it.label }
        val activeIds = roster
            .filter { option -> option.detail?.let { it == "Active" || it.endsWith(" · Active") } == true }
            .map { it.id }
        val groups = ops.groups()
        val groupNames = groups.associate { it.id to it.name }
        groupOptions = groups.map { group ->
            FarmSelectorOption(group.id, group.name, "${group.speciesCode.replaceFirstChar { it.uppercase() }} · ${group.headCount} head")
        }
        val recent = ops.recentVaccinations()
        vaccinationViews = recent.map { record ->
            val subject = when {
                record.animalId != null -> labels[record.animalId] ?: "Animal ${record.animalId}"
                record.groupId != null -> groupNames[record.groupId]?.let { "Group $it" } ?: "Group ${record.groupId}"
                else -> record.speciesCode.replaceFirstChar { it.uppercase() }
            }
            HealthVaccinationView(
                id = record.id,
                subjectLabel = subject,
                speciesCode = record.speciesCode,
                formularyLabel = formularyNames[record.formularyItemId] ?: record.formularyItemId,
                dose = record.dose,
                method = record.method,
                occurredEpochDay = epochDay(record.occurredAtEpochMillis),
            )
        }
        // Record-based review candidates: no protocol engine exists on device. "No vaccination
        // recorded" or a latest record older than 365 days. Advisory only.
        val today = LocalDate.now(zone).toEpochDay()
        var everVaccinated = 0
        dueViews = activeIds.mapNotNull { animalId ->
            val history = ops.vaccinationsForAnimal(animalId)
            val latest = history.maxByOrNull { it.occurredAtEpochMillis }
            if (latest != null) everVaccinated++
            val daysSince = latest?.let { today - epochDay(it.occurredAtEpochMillis) }
            when {
                latest == null -> VaccinationDueView(animalId, labels[animalId] ?: animalId, "No vaccination recorded", null)
                daysSince != null && daysSince > 365 ->
                    VaccinationDueView(animalId, labels[animalId] ?: animalId, "Last vaccination $daysSince days ago — review due", daysSince)
                else -> null
            }
        }.sortedWith(compareBy<VaccinationDueView> { it.daysSinceLast != null }.thenByDescending { it.daysSinceLast ?: -1 })
        reportStats = HealthReportStats(
            activeAnimalCount = activeIds.size,
            animalsEverVaccinated = everVaccinated,
            dueCandidateCount = dueViews.size,
            recentVaccinationCount = recent.size,
            treatmentCount = readModel.treatmentCount,
            observationCount = readModel.observationCount,
            activeWithdrawalCount = readModel.activeWithdrawalCount,
        )
    }

    suspend fun refresh() {
        observations = ops.recentObservations().map { row -> "${row.speciesCode} · ${row.signs}" }
        redFlagObservations = ops.recentObservations().filter { it.redFlag }
            .map { row -> "${row.speciesCode} · ${row.signs}" }
        val diseaseRows = ops.diseases()
        catalog = diseaseRows.map { row -> "${row.speciesCode} · ${row.displayName} · ${row.firstAid}" }
        referenceDetails = diseaseRows.map { row ->
            HealthReferenceDetail(
                code = row.code,
                speciesCode = row.speciesCode,
                displayName = row.displayName,
                signs = row.signs,
                firstAid = row.firstAid,
                prevention = row.prevention,
                vetClass = row.vetClass,
                redFlag = row.redFlag,
            )
        }
        val approved = ops.approvedFormulary()
        formulary = approved.map { row -> "${row.id} · ${row.productName} · ${row.speciesCode} · ${row.vetClass}" }
        formularyOptions = approved.map { row -> FarmSelectorOption(row.id, row.productName, "${row.speciesCode} · ${row.vetClass}") }
        treatments = ops.recentTreatments().map { row ->
            "${row.speciesCode} · ${row.reason} · formulary ${row.formularyItemId}"
        }
        val packRows = ops.packs()
        packs = packRows.map { row ->
            "${row.speciesCode} · ${row.name} · ${row.status} · ${row.acceptedByVet.orEmpty()}"
        }
        acceptedPacks = packRows.filter { it.status == "vet_accepted" }
            .map { row -> FarmSelectorOption(row.id, row.name, "${row.speciesCode} · accepted by ${row.acceptedByVet.orEmpty()}") }
        withdrawals = ops.withdrawals().map { row ->
            "${row.windowKind} · ${row.product} ends day ${row.endsEpochDay}"
        }
        readModel = loadReadModel()
        refreshVaccinations()
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
        formularyOptions = formularyOptions,
        speciesCodes = FarmSpeciesCodes.ALL,
        acceptedPacks = acceptedPacks,
        searchAnimals = searchAnimals,
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
        vaccinations = vaccinationViews,
        dueCandidates = dueViews,
        groupOptions = groupOptions,
        reportStats = reportStats,
        referenceDetails = referenceDetails,
        redFlagObservations = redFlagObservations,
    )
}
