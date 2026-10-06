package com.farmos.feature.ops

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import com.farmos.core.design.FarmEntitySelector
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalRows
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.design.FarmSearchSelector
import com.farmos.core.design.FarmSelectionAtoms
import com.farmos.core.design.FarmSelectorOption
import com.farmos.core.design.FarmSelectorSearch
import com.farmos.core.design.FosDimens
import com.farmos.core.design.FarmVisualClass
import com.farmos.core.design.NoFarmSelectorSearch
import java.time.LocalDate
import java.time.ZoneId

private enum class HealthPage {
    DASHBOARD,
    OBSERVATIONS,
    RECORD_OBSERVATION,
    FORMULARY,
    TREATMENT,
    WITHDRAWALS,
    REFERENCES,
    PROTOCOLS,
    ACCEPT_PROTOCOL,
    EDIT_PROTOCOL,
    APPLY_PROTOCOL,
    VET_VISIT,
    LAB_RESULT,
    TREATMENT_RECORDS,
    TREATMENT_DETAIL,
    WITHDRAWAL_DETAIL,
    VET_VISITS,
    VET_VISIT_DETAIL,
    LAB_RESULTS,
    LAB_RESULT_DETAIL,
    FORMULARY_ITEM,
    PROTOCOL_PACK_DETAIL,
    TIMELINE,
    VACCINATION_SCHEDULE,
    VACCINATION_CAPTURE,
    VACCINATION_DETAIL,
    TODAY_ACTIONS,
    HEALTH_REPORT,
    REFERENCE_DETAIL,
    EMERGENCY,
}

/** One disease reference entry for FOS-HEALTH-027. Read-only; from the local disease catalog. */
data class HealthReferenceDetail(
    val code: String,
    val speciesCode: String,
    val displayName: String,
    val signs: String,
    val firstAid: String,
    val prevention: String,
    val vetClass: String,
    val redFlag: Boolean,
)

/** One recorded vaccination for the schedule, detail and report surfaces. Read-only view. */
data class HealthVaccinationView(
    val id: String,
    val subjectLabel: String,
    val speciesCode: String,
    val formularyLabel: String,
    val dose: String?,
    val method: String?,
    val occurredEpochDay: Long,
)

/**
 * A record-based vaccination review candidate. No protocol engine exists on device: "No vaccination
 * recorded" means no vaccination row for the animal; "review due" means its latest recorded
 * vaccination is older than 365 days. Advisory only; never a prescribed schedule.
 */
data class VaccinationDueView(
    val animalId: String,
    val label: String,
    val reason: String,
    val daysSinceLast: Long?,
)

/** Farm health aggregates for FOS-HEALTH-030. Null counts render as a dash, never as zero. */
data class HealthReportStats(
    val activeAnimalCount: Int?,
    val animalsEverVaccinated: Int?,
    val dueCandidateCount: Int,
    val recentVaccinationCount: Int,
    val treatmentCount: Int?,
    val observationCount: Int?,
    val activeWithdrawalCount: Int?,
)

/** Governed Health family. Advisory/recording only; this surface does not prescribe dose or diagnose. */
@Composable
fun HealthObservationScreen(
    rows: List<String>,
    catalog: List<String>,
    treatments: List<String>,
    formulary: List<String> = emptyList(),
    busy: Boolean,
    error: String?,
    onRecord: (species: String, signs: String, firstAid: String, redFlag: Boolean) -> Unit,
    onCreateFormulary: (product: String, species: String, vetClass: String, meatDays: Int?) -> Unit,
    onRecordTreatment: (species: String, formularyItemId: String, reason: String) -> Unit,
    packs: List<String> = emptyList(),
    withdrawals: List<String> = emptyList(),
    onAcceptPack: (species: String, name: String, vet: String) -> Unit = { _, _, _ -> },
    onRecordVetVisit: (species: String, reason: String, vet: String, day: String) -> Unit = { _, _, _, _ -> },
    onRecordLab: (animalId: String, testName: String, resultText: String, cells: String, day: String) -> Unit = { _, _, _, _, _ -> },
    onAddPackSlot: (packId: String, slotCode: String, title: String, offset: String, fromEvent: String) -> Unit = { _, _, _, _, _ -> },
    onApplyPack: (packId: String, animalId: String, day: String) -> Unit = { _, _, _ -> },
    onBack: () -> Unit,
    entryPage: HealthEntryPage = HealthEntryPage.DASHBOARD,
    readModel: HealthReadModel = HealthReadModel(),
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
    /** Vet-approved formulary items; a treatment references one of these, never a typed product. */
    formularyOptions: List<FarmSelectorOption> = emptyList(),
    /** Governed farm species codes; observation and vet visit accept only these server-side. */
    speciesCodes: List<String> = emptyList(),
    /** Vet-accepted protocol packs; adding a slot and applying a pack accept only these. */
    acceptedPacks: List<FarmSelectorOption> = emptyList(),
    /** Whole-farm animal search across species for health subjects; never a capped presentation list. */
    searchAnimals: FarmSelectorSearch = NoFarmSelectorSearch,
    /** Recorded vaccinations (latest first) for the schedule and report surfaces. */
    vaccinations: List<HealthVaccinationView> = emptyList(),
    /** Record-based vaccination review candidates for the schedule and today surfaces. */
    dueCandidates: List<VaccinationDueView> = emptyList(),
    /** Animal groups for the vaccination capture target picker. */
    groupOptions: List<FarmSelectorOption> = emptyList(),
    /** Aggregates for the health report; null until loaded. */
    reportStats: HealthReportStats? = null,
    /** Records a vaccination through the governed command handler; never a direct Room write. */
    onRecordVaccination: (species: String, animalId: String?, groupId: String?, formularyItemId: String, dose: String?, method: String?, day: String) -> Unit =
        { _, _, _, _, _, _, _ -> },
    /** Structured disease reference entries for FOS-HEALTH-027; parallels the catalog strings. */
    referenceDetails: List<HealthReferenceDetail> = emptyList(),
    /** Red-flag observation summaries for FOS-HEALTH-028; empty when none are recorded. */
    redFlagObservations: List<String> = emptyList(),
) {
    var page by remember { mutableStateOf(entryPage.toHealthPage()) }
    var backStack by remember { mutableStateOf(emptyList<HealthPage>()) }
    var selectedRecordId by remember { mutableStateOf<String?>(null) }
    val home = {
        backStack = emptyList()
        page = HealthPage.DASHBOARD
    }
    fun openRecord(next: HealthPage, id: String? = selectedRecordId) {
        backStack = backStack + page
        selectedRecordId = id
        page = next
    }
    val recordBack = {
        page = backStack.lastOrNull() ?: HealthPage.DASHBOARD
        backStack = backStack.dropLast(1)
    }
    val treatmentBack = if (entryPage == HealthEntryPage.TREATMENT) onBack else home
    val withdrawalBack = if (entryPage == HealthEntryPage.WITHDRAWALS) onBack else home
    val observationBack = if (entryPage == HealthEntryPage.RECORD_OBSERVATION) onBack else home
    val vetVisitBack = if (entryPage == HealthEntryPage.VET_VISIT) onBack else home
    val labResultBack = if (entryPage == HealthEntryPage.LAB_RESULT) onBack else home
    val formularyBack = if (entryPage == HealthEntryPage.FORMULARY) onBack else home
    when (page) {
        HealthPage.DASHBOARD -> {
            HealthDashboard(readModel, packs, error, { openRecord(it) }, onBack)
        }

        HealthPage.TREATMENT_RECORDS -> HealthTreatmentListScreen(readModel, zone, { openRecord(HealthPage.TREATMENT_DETAIL, it) }, recordBack)
        HealthPage.TREATMENT_DETAIL -> HealthTreatmentDetailScreen(
            readModel,
            selectedRecordId,
            today,
            zone,
            { openRecord(HealthPage.WITHDRAWAL_DETAIL, it) },
            recordBack,
        )
        HealthPage.WITHDRAWAL_DETAIL -> HealthWithdrawalDetailScreen(readModel, selectedRecordId, today, { openRecord(HealthPage.TREATMENT_DETAIL, it) }, recordBack)
        HealthPage.VET_VISITS -> HealthVetVisitListScreen(readModel, { openRecord(HealthPage.VET_VISIT_DETAIL, it) }, recordBack)
        HealthPage.VET_VISIT_DETAIL -> HealthVetVisitDetailScreen(readModel, selectedRecordId, recordBack)
        HealthPage.LAB_RESULTS -> HealthLabResultListScreen(readModel, { openRecord(HealthPage.LAB_RESULT_DETAIL, it) }, recordBack)
        HealthPage.LAB_RESULT_DETAIL -> HealthLabResultDetailScreen(readModel, selectedRecordId, recordBack)
        HealthPage.FORMULARY_ITEM -> FormularyItemScreen(readModel.formulary, recordBack)
        HealthPage.PROTOCOL_PACK_DETAIL -> ProtocolPackDetailScreen(readModel.packs, recordBack)
        HealthPage.TIMELINE -> HealthTimelineScreen(readModel.timeline, recordBack)

        HealthPage.OBSERVATIONS -> {
            HealthRows("FOS-HEALTH-003", "Observations", rows, "No observations yet", error, home)
        }

        HealthPage.RECORD_OBSERVATION -> {
            RecordObservationScreen(speciesCodes, busy, error, onRecord, observationBack)
        }

        HealthPage.FORMULARY -> {
            FormularyScreen(formulary, busy, error, onCreateFormulary, formularyBack)
        }

        HealthPage.TREATMENT -> {
            TreatmentScreen(formulary, formularyOptions, treatments, busy, error, onRecordTreatment, treatmentBack)
        }

        HealthPage.WITHDRAWALS -> {
            HealthRows(
                "FOS-HEALTH-009",
                "Withdrawal dashboard",
                withdrawals,
                "No open withdrawal windows",
                error,
                withdrawalBack,
                FarmVisualClass.I4,
            )
        }

        HealthPage.REFERENCES -> {
            HealthReferenceListScreen(
                referenceDetails,
                catalog,
                { openRecord(HealthPage.REFERENCE_DETAIL, it) },
                home,
            )
        }

        HealthPage.REFERENCE_DETAIL -> {
            HealthReferenceDetailScreen(referenceDetails, selectedRecordId, recordBack)
        }

        HealthPage.EMERGENCY -> {
            HealthEmergencyScreen(redFlagObservations, home)
        }

        HealthPage.PROTOCOLS -> {
            HealthRows("FOS-HEALTH-015", "Protocol packs", packs, "No accepted protocol packs", error, home)
        }

        HealthPage.ACCEPT_PROTOCOL -> {
            AcceptProtocolScreen(busy, error, onAcceptPack, home)
        }

        HealthPage.EDIT_PROTOCOL -> {
            ProtocolSlotScreen(acceptedPacks, busy, error, onAddPackSlot, home)
        }

        HealthPage.APPLY_PROTOCOL -> {
            ApplyProtocolScreen(acceptedPacks, searchAnimals, busy, error, onApplyPack, home)
        }

        HealthPage.VET_VISIT -> {
            VetVisitScreen(speciesCodes, busy, error, onRecordVetVisit, vetVisitBack)
        }

        HealthPage.LAB_RESULT -> {
            LabResultScreen(searchAnimals, busy, error, onRecordLab, labResultBack)
        }

        HealthPage.VACCINATION_SCHEDULE -> {
            VaccinationScheduleScreen(
                vaccinations,
                dueCandidates,
                { openRecord(HealthPage.VACCINATION_DETAIL, it) },
                { openRecord(HealthPage.VACCINATION_CAPTURE) },
                recordBack,
            )
        }

        HealthPage.VACCINATION_CAPTURE -> {
            VaccinationCaptureScreen(speciesCodes, formularyOptions, groupOptions, searchAnimals, busy, error, onRecordVaccination, home)
        }

        HealthPage.VACCINATION_DETAIL -> {
            VaccinationDetailScreen(vaccinations, selectedRecordId, recordBack)
        }

        HealthPage.TODAY_ACTIONS -> {
            TodayHealthActionsScreen(
                dueCandidates,
                readModel,
                { openRecord(HealthPage.VACCINATION_SCHEDULE) },
                { openRecord(HealthPage.VACCINATION_CAPTURE) },
                home,
            )
        }

        HealthPage.HEALTH_REPORT -> {
            HealthReportScreen(reportStats, readModel, vaccinations, home)
        }
    }
}

private fun HealthEntryPage.toHealthPage(): HealthPage =
    when (this) {
        HealthEntryPage.DASHBOARD -> HealthPage.DASHBOARD
        HealthEntryPage.TREATMENT -> HealthPage.TREATMENT
        HealthEntryPage.WITHDRAWALS -> HealthPage.WITHDRAWALS
        HealthEntryPage.RECORD_OBSERVATION -> HealthPage.RECORD_OBSERVATION
        HealthEntryPage.VET_VISIT -> HealthPage.VET_VISIT
        HealthEntryPage.LAB_RESULT -> HealthPage.LAB_RESULT
        HealthEntryPage.FORMULARY -> HealthPage.FORMULARY
    }

/**
 * FOS-HEALTH-001 — health dashboard. FOS-HEALTH-002 — today health actions: a dedicated page
 * (HealthPage.TODAY_ACTIONS) plus the compact "Today health picture" summary section below.
 */
@Composable
private fun HealthDashboard(
    counts: HealthReadModel,
    packs: List<String>,
    error: String?,
    onOpen: (HealthPage) -> Unit,
    onBack: () -> Unit,
) {
    FarmOperationalPage(
        "FOS-HEALTH-001",
        "Health",
        "Observations, treatments and withdrawals on this device.",
        FarmVisualClass.I2,
        onBack,
    ) {
        FarmOperationalSection("Today health picture") {
            Row(Modifier.fillMaxWidth().testTag("farm-screen:FOS-HEALTH-002"), horizontalArrangement = Arrangement.SpaceBetween) {
                // Exhaustive farm-scoped counts; shown as a dash until loaded, never as a capped list size.
                HealthMetric("Observations", counts.observationCount)
                HealthMetric("Treatments", counts.treatmentCount)
                HealthMetric("Active withdrawals", counts.activeWithdrawalCount)
            }
        }
        FarmOperationalSection("Observe and review") {
            TextButton(onClick = { onOpen(HealthPage.OBSERVATIONS) }) { Text("Observation list") }
            Button(onClick = { onOpen(HealthPage.RECORD_OBSERVATION) }) { Text("Record observation") }
            TextButton(onClick = { onOpen(HealthPage.REFERENCES) }) { Text("Reference library") }
            TextButton(onClick = { onOpen(HealthPage.EMERGENCY) }) { Text("Emergency / red flag") }
            Button(onClick = { onOpen(HealthPage.TODAY_ACTIONS) }) { Text("Today health actions") }
        }
        FarmOperationalSection(
            "Clinical records",
            "Treatment capture requires a vet-approved formulary item. Farm OS does not prescribe a dose.",
        ) {
            TextButton(onClick = { onOpen(HealthPage.FORMULARY) }) { Text("Vet-approved formulary") }
            TextButton(onClick = { onOpen(HealthPage.TREATMENT) }) { Text("Record treatment") }
            TextButton(onClick = { onOpen(HealthPage.WITHDRAWALS) }) { Text("Withdrawal windows") }
            TextButton(onClick = { onOpen(HealthPage.VET_VISIT) }) { Text("Record vet visit") }
            TextButton(onClick = { onOpen(HealthPage.LAB_RESULT) }) { Text("Record lab result") }
        }
        FarmOperationalSection("Health records", "Recorded history on this device. Records only; no diagnosis or dosing.") {
            TextButton(onClick = { onOpen(HealthPage.TREATMENT_RECORDS) }) { Text("Treatment records") }
            TextButton(onClick = { onOpen(HealthPage.VACCINATION_SCHEDULE) }) { Text("Vaccination schedule") }
            TextButton(onClick = { onOpen(HealthPage.VET_VISITS) }) { Text("Vet visits") }
            TextButton(onClick = { onOpen(HealthPage.LAB_RESULTS) }) { Text("Lab results") }
            TextButton(onClick = { onOpen(HealthPage.FORMULARY_ITEM) }) { Text("Formulary items") }
            TextButton(onClick = { onOpen(HealthPage.PROTOCOL_PACK_DETAIL) }) { Text("Protocol pack detail") }
            TextButton(onClick = { onOpen(HealthPage.TIMELINE) }) { Text("Health timeline") }
            TextButton(onClick = { onOpen(HealthPage.HEALTH_REPORT) }) { Text("Health report") }
        }
        FarmOperationalSection("Protocol packs") {
            Text("${packs.size} protocol pack(s) on this device")
            TextButton(onClick = { onOpen(HealthPage.PROTOCOLS) }) { Text("View protocol packs") }
            TextButton(onClick = { onOpen(HealthPage.ACCEPT_PROTOCOL) }) { Text("Accept protocol pack") }
            TextButton(onClick = { onOpen(HealthPage.EDIT_PROTOCOL) }) { Text("Add protocol slot") }
            TextButton(onClick = { onOpen(HealthPage.APPLY_PROTOCOL) }) { Text("Apply protocol pack") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun HealthMetric(
    label: String,
    value: Int?,
) {
    androidx.compose.foundation.layout.Column(Modifier.testTag("health-metric:$label")) {
        Text(value?.toString() ?: "—", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun HealthRows(
    screenId: String,
    title: String,
    rows: List<String>,
    empty: String,
    error: String?,
    onBack: () -> Unit,
    visualClass: FarmVisualClass = FarmVisualClass.I3,
) {
    FarmOperationalPage(screenId, title, "Recorded farm health information.", visualClass, onBack) {
        FarmOperationalRows(rows, empty, "Records captured on this device will appear here.")
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

/**
 * FOS-HEALTH-026 — reference library list. Each row opens FOS-HEALTH-027 for the full entry.
 */
@Composable
private fun HealthReferenceListScreen(
    details: List<HealthReferenceDetail>,
    fallbackRows: List<String>,
    onOpenDetail: (String) -> Unit,
    onBack: () -> Unit,
) {
    FarmOperationalPage(
        "FOS-HEALTH-026",
        "Reference library",
        "Disease reference entries on this device.",
        FarmVisualClass.I3,
        onBack,
    ) {
        if (details.isEmpty()) {
            FarmOperationalRows(fallbackRows, "No reference rows on this device", "Records captured on this device will appear here.")
        } else {
            details.forEach { detail ->
                TextButton(
                    onClick = { onOpenDetail(detail.code) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        detail.speciesCode.replaceFirstChar { it.uppercase() } + " · " + detail.displayName +
                            if (detail.redFlag) " · red flag" else "",
                    )
                }
            }
        }
    }
}

/** FOS-HEALTH-027 — reference detail: the full catalog entry for one disease reference. */
@Composable
private fun HealthReferenceDetailScreen(
    details: List<HealthReferenceDetail>,
    code: String?,
    onBack: () -> Unit,
) {
    val detail = details.firstOrNull { it.code == code }
    FarmOperationalPage(
        "FOS-HEALTH-027",
        "Reference detail",
        detail?.displayName ?: "Reference",
        FarmVisualClass.I3,
        onBack,
    ) {
        if (detail == null) {
            Text("The reference entry is not on this device.")
            return@FarmOperationalPage
        }
        FarmOperationalSection("Entry") {
            Text(detail.speciesCode.replaceFirstChar { it.uppercase() } + " · " + detail.displayName)
            Text("Vet class: ${detail.vetClass}")
            if (detail.redFlag) Text("Red-flag condition: treat as urgent and call the vet.", color = MaterialTheme.colorScheme.error)
        }
        FarmOperationalSection("Signs") {
            Text(detail.signs.ifBlank { "No signs recorded." })
        }
        FarmOperationalSection("First aid") {
            Text(detail.firstAid.ifBlank { "No first aid recorded." })
        }
        FarmOperationalSection("Prevention") {
            Text(detail.prevention.ifBlank { "No prevention recorded." })
        }
    }
}

/**
 * FOS-HEALTH-028 — emergency / red flag: the farm's recorded red-flag observations with triage
 * guidance. Advisory and recording only; this surface does not diagnose or prescribe.
 */
@Composable
private fun HealthEmergencyScreen(
    redFlagObservations: List<String>,
    onBack: () -> Unit,
) {
    FarmOperationalPage(
        "FOS-HEALTH-028",
        "Emergency / red flag",
        "Urgent health observations on this farm.",
        FarmVisualClass.I3,
        onBack,
    ) {
        FarmOperationalSection("What to do") {
            Text(
                "Isolate the animal if safe to do so, keep it calm and warm, and call the vet. " +
                    "Record what you see; Farm OS does not diagnose.",
            )
        }
        FarmOperationalSection("Red-flag observations") {
            if (redFlagObservations.isEmpty()) {
                Text("No red-flag observations recorded on this device.")
            } else {
                redFlagObservations.forEach { row ->
                    Text(row, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun RecordObservationScreen(
    speciesCodes: List<String>,
    busy: Boolean,
    error: String?,
    onRecord: (String, String, String, Boolean) -> Unit,
    onBack: () -> Unit,
) {
    var species by remember { mutableStateOf(speciesCodes.firstOrNull().orEmpty()) }
    var signs by remember { mutableStateOf("") }
    var firstAid by remember { mutableStateOf("") }
    FarmOperationalPage("FOS-HEALTH-004", "Record observation", "Record signs first; this is not a diagnosis.", onBack = onBack) {
        FarmOperationalSection("Observation") {
            FarmSpeciesSelector(speciesCodes, species, busy) { species = it }
            OutlinedTextField(
                signs,
                { signs = it },
                label = { Text("Signs observed") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
            )
            OutlinedTextField(
                firstAid,
                { firstAid = it },
                label = { Text("First aid applied") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
            )
            Button(onClick = {
                onRecord(species, signs, firstAid, false)
            }, enabled = !busy && species.isNotBlank() && signs.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Save observation") }
            Button(onClick = {
                onRecord(species, signs, firstAid, true)
            }, enabled = !busy && species.isNotBlank() && signs.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Save as red flag") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun FormularyScreen(
    formulary: List<String>,
    busy: Boolean,
    error: String?,
    onCreate: (String, String, String, Int?) -> Unit,
    onBack: () -> Unit,
) {
    var product by remember { mutableStateOf("") }
    var species by remember { mutableStateOf("goat") }
    var vetClass by remember { mutableStateOf("vaccine") }
    var meatDays by remember { mutableStateOf("") }
    FarmOperationalPage("FOS-HEALTH-013", "Formulary", "Only vet-approved products can be used by treatment records.", onBack = onBack) {
        FarmOperationalRows(formulary, "No approved formulary items", "Add an approved product before recording treatment.")
        FarmOperationalSection("Add approved item") {
            OutlinedTextField(product, {
                product = it
            }, label = { Text("Product") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            OutlinedTextField(species, {
                species = it
            }, label = { Text("Species") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            OutlinedTextField(vetClass, {
                vetClass = it
            }, label = { Text("Vet class") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            OutlinedTextField(meatDays, {
                meatDays = it
            }, label = { Text("Meat withdrawal days") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            Button(
                onClick = { onCreate(product, species, vetClass, meatDays.toIntOrNull()) },
                enabled =
                    !busy && product.isNotBlank() && species.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Add approved item") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun TreatmentScreen(
    formulary: List<String>,
    formularyOptions: List<FarmSelectorOption>,
    treatments: List<String>,
    busy: Boolean,
    error: String?,
    onRecord: (String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var species by remember { mutableStateOf("goat") }
    var formularyId by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    FarmOperationalPage(
        "FOS-HEALTH-007",
        "Record treatment",
        "Use a vet-approved formulary item. Farm OS does not calculate or prescribe dose.",
        FarmVisualClass.I4,
        onBack,
    ) {
        FarmOperationalRows(formulary, "No approved formulary items", "Treatment cannot be recorded without an approved formulary item.")
        FarmOperationalSection("Treatment record") {
            OutlinedTextField(species, {
                species = it
            }, label = { Text("Species") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            FarmEntitySelector(
                atomTag = HEALTH_FORMULARY_SELECTOR,
                title = "Vet-approved formulary item",
                options = formularyOptions,
                selectedId = formularyId.ifBlank { null },
                onSelect = { formularyId = it },
                emptyText = "No approved formulary items",
                enabled = !busy,
            )
            OutlinedTextField(reason, { reason = it }, label = { Text("Reason") }, modifier = Modifier.fillMaxWidth(), enabled = !busy)
            Button(onClick = {
                onRecord(species, formularyId, reason)
            }, enabled = !busy && formularyId.isNotBlank() && reason.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Record treatment",
                )
            }
        }
        if (treatments.isNotEmpty()) FarmOperationalRows(treatments, "", null)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun AcceptProtocolScreen(
    busy: Boolean,
    error: String?,
    onAccept: (String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var species by remember { mutableStateOf("goat") }
    var name by remember { mutableStateOf("") }
    var vet by remember { mutableStateOf("") }
    FarmOperationalPage(
        "FOS-HEALTH-017",
        "Accept protocol pack",
        "A protocol is accepted only with the attending vet named.",
        FarmVisualClass.I4,
        onBack,
    ) {
        FarmOperationalSection("Protocol authority") {
            OutlinedTextField(species, {
                species = it
            }, label = { Text("Species") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            OutlinedTextField(name, {
                name = it
            }, label = { Text("Pack name") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            OutlinedTextField(vet, {
                vet = it
            }, label = { Text("Attending vet") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            Button(onClick = {
                onAccept(species, name, vet)
            }, enabled = !busy && name.isNotBlank() && vet.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Accept protocol pack",
                )
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun ProtocolSlotScreen(
    acceptedPacks: List<FarmSelectorOption>,
    busy: Boolean,
    error: String?,
    onAdd: (String, String, String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var packId by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var offset by remember { mutableStateOf("0") }
    var anchor by remember { mutableStateOf("apply_date") }
    FarmOperationalPage(
        "FOS-HEALTH-019",
        "Protocol slot editor",
        "Slots create due work from a governed anchor; they are not medication doses.",
        onBack = onBack,
    ) {
        FarmOperationalSection("Schedule slot") {
            FarmEntitySelector(
                atomTag = HEALTH_PACK_SELECTOR,
                title = "Vet-accepted protocol pack",
                options = acceptedPacks,
                selectedId = packId.ifBlank { null },
                onSelect = { packId = it },
                emptyText = "No vet-accepted protocol packs. Accept one first.",
                enabled = !busy,
            )
            OutlinedTextField(code, {
                code = it
            }, label = { Text("Slot code") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            OutlinedTextField(
                title,
                { title = it },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                singleLine = true,
            )
            OutlinedTextField(offset, {
                offset = it
            }, label = { Text("Offset days") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            OutlinedTextField(anchor, {
                anchor = it
            }, label = { Text("Anchor") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            Button(
                onClick = { onAdd(packId, code, title, offset, anchor) },
                enabled =
                    !busy && packId.isNotBlank() && code.isNotBlank() && title.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Add protocol slot") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun ApplyProtocolScreen(
    acceptedPacks: List<FarmSelectorOption>,
    searchAnimals: FarmSelectorSearch,
    busy: Boolean,
    error: String?,
    onApply: (String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var packId by remember { mutableStateOf("") }
    var animal by remember { mutableStateOf<FarmSelectorOption?>(null) }
    val animalId = animal?.id.orEmpty()
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    FarmOperationalPage(
        "FOS-HEALTH-018",
        "Apply protocol pack",
        "Create the pack's due tasks for an animal from the selected anchor date.",
        FarmVisualClass.I4,
        onBack,
    ) {
        FarmOperationalSection("Apply pack") {
            FarmEntitySelector(
                atomTag = HEALTH_PACK_SELECTOR,
                title = "Vet-accepted protocol pack",
                options = acceptedPacks,
                selectedId = packId.ifBlank { null },
                onSelect = { packId = it },
                emptyText = "No vet-accepted protocol packs. Accept one first.",
                enabled = !busy,
            )
            FarmSearchSelector(
                atomTag = FarmSelectionAtoms.ANIMAL_SELECTOR,
                title = "Animal",
                search = searchAnimals,
                selected = animal,
                onSelect = { animal = it },
                emptyText = "No animals match on this device",
                enabled = !busy,
            )
            OutlinedTextField(day, {
                day = it
            }, label = { Text("Anchor date") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            Button(
                onClick = { onApply(packId, animalId, day) },
                enabled =
                    !busy && packId.isNotBlank() && animalId.isNotBlank() && runCatching { LocalDate.parse(day) }.isSuccess,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Apply pack") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun VetVisitScreen(
    speciesCodes: List<String>,
    busy: Boolean,
    error: String?,
    onRecord: (String, String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var species by remember { mutableStateOf(speciesCodes.firstOrNull().orEmpty()) }
    var reason by remember { mutableStateOf("") }
    var vet by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    FarmOperationalPage(
        "FOS-HEALTH-021",
        "Record vet visit",
        "Record professional attendance without converting it into a diagnosis.",
        FarmVisualClass.I4,
        onBack,
    ) {
        FarmOperationalSection("Visit") {
            FarmSpeciesSelector(speciesCodes, species, busy) { species = it }
            OutlinedTextField(reason, { reason = it }, label = { Text("Reason") }, modifier = Modifier.fillMaxWidth(), enabled = !busy)
            OutlinedTextField(vet, {
                vet = it
            }, label = { Text("Attending vet") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            OutlinedTextField(
                day,
                { day = it },
                label = { Text("Visit date") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                singleLine = true,
            )
            Button(
                onClick = { onRecord(species, reason, vet, day) },
                enabled =
                    !busy && species.isNotBlank() && reason.isNotBlank() && vet.isNotBlank() && runCatching { LocalDate.parse(day) }.isSuccess,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Record vet visit") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun LabResultScreen(
    searchAnimals: FarmSelectorSearch,
    busy: Boolean,
    error: String?,
    onRecord: (String, String, String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var animal by remember { mutableStateOf<FarmSelectorOption?>(null) }
    val animalId = animal?.id.orEmpty()
    var test by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var cells by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    FarmOperationalPage(
        "FOS-HEALTH-024",
        "Record lab result",
        "A lab result is recorded evidence, not a Farm OS diagnosis.",
        FarmVisualClass.I4,
        onBack,
    ) {
        FarmOperationalSection("Laboratory result") {
            FarmSearchSelector(
                atomTag = FarmSelectionAtoms.ANIMAL_SELECTOR,
                title = "Animal",
                search = searchAnimals,
                selected = animal,
                onSelect = { animal = it },
                emptyText = "No animals match on this device",
                enabled = !busy,
            )
            OutlinedTextField(
                test,
                { test = it },
                label = { Text("Lab test") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                singleLine = true,
            )
            OutlinedTextField(result, { result = it }, label = { Text("Result") }, modifier = Modifier.fillMaxWidth(), enabled = !busy)
            OutlinedTextField(cells, {
                cells = it
            }, label = { Text("Cells/ml (optional)") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            OutlinedTextField(day, {
                day = it
            }, label = { Text("Result date") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            Button(
                onClick = { onRecord(animalId, test, result, cells, day) },
                enabled =
                    !busy && animalId.isNotBlank() && test.isNotBlank() && result.isNotBlank() &&
                        runCatching {
                            LocalDate.parse(
                                day,
                            )
                        }.isSuccess,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Record lab result") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

private const val HEALTH_FORMULARY_SELECTOR = "health-formulary-selector"
private const val HEALTH_PACK_SELECTOR = "health-pack-selector"
private const val HEALTH_GROUP_SELECTOR = "health-group-selector"

/**
 * FOS-HEALTH-011 — vaccination schedule: recorded vaccinations (latest first) and record-based
 * review candidates. Tapping a recorded row opens the case detail; recording starts a new capture.
 */
@Composable
private fun VaccinationScheduleScreen(
    vaccinations: List<HealthVaccinationView>,
    dueCandidates: List<VaccinationDueView>,
    onOpenDetail: (String) -> Unit,
    onCapture: () -> Unit,
    onBack: () -> Unit,
) {
    FarmOperationalPage(
        "FOS-HEALTH-011",
        "Vaccination schedule",
        "Recorded vaccinations and record-based review candidates on this device.",
        FarmVisualClass.I3,
        onBack,
    ) {
        FarmOperationalSection(
            "Review candidates",
            "Advisory only: animals with no recorded vaccination, or a latest record older than 365 days. Not a protocol schedule.",
        ) {
            if (dueCandidates.isEmpty()) {
                Text("No review candidates on this device")
            } else {
                dueCandidates.forEach { candidate ->
                    TextButton(onClick = onCapture, modifier = Modifier.fillMaxWidth()) {
                        Text("${candidate.label} — ${candidate.reason}")
                    }
                }
            }
        }
        FarmOperationalSection("Recorded vaccinations") {
            Button(onClick = onCapture, modifier = Modifier.fillMaxWidth()) { Text("Record vaccination") }
            if (vaccinations.isEmpty()) {
                Text("No vaccinations recorded on this device")
            } else {
                vaccinations.forEach { record ->
                    TextButton(onClick = { onOpenDetail(record.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text("${record.subjectLabel} · ${record.formularyLabel} · ${LocalDate.ofEpochDay(record.occurredEpochDay)}")
                    }
                }
            }
        }
    }
}

/**
 * FOS-HEALTH-012 — vaccination capture: records a vaccination against a vet-approved formulary
 * item for exactly one target — an individual animal or a group. Never a free-typed product.
 */
@Composable
private fun VaccinationCaptureScreen(
    speciesCodes: List<String>,
    formularyOptions: List<FarmSelectorOption>,
    groupOptions: List<FarmSelectorOption>,
    searchAnimals: FarmSelectorSearch,
    busy: Boolean,
    error: String?,
    onRecord: (String, String?, String?, String, String?, String?, String) -> Unit,
    onBack: () -> Unit,
) {
    var species by remember { mutableStateOf(speciesCodes.firstOrNull().orEmpty()) }
    var useGroup by remember { mutableStateOf(false) }
    var animal by remember { mutableStateOf<FarmSelectorOption?>(null) }
    var groupId by remember { mutableStateOf("") }
    var formularyId by remember { mutableStateOf("") }
    var dose by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    val dayOk = runCatching { LocalDate.parse(day) }.isSuccess
    val targetOk = if (useGroup) groupId.isNotBlank() else animal != null
    FarmOperationalPage(
        "FOS-HEALTH-012",
        "Record vaccination",
        "A vaccination references a vet-approved formulary item and exactly one target. Farm OS does not prescribe dose.",
        FarmVisualClass.I4,
        onBack,
    ) {
        FarmOperationalSection("Vaccination") {
            FarmSpeciesSelector(speciesCodes, species, busy) { species = it }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FosDimens.Grid)) {
                TextButton(onClick = { useGroup = false }, enabled = !busy) { Text("Individual animal") }
                TextButton(onClick = { useGroup = true }, enabled = !busy) { Text("Group") }
            }
            if (useGroup) {
                FarmEntitySelector(
                    atomTag = HEALTH_GROUP_SELECTOR,
                    title = "Animal group",
                    options = groupOptions,
                    selectedId = groupId.ifBlank { null },
                    onSelect = { groupId = it },
                    emptyText = "No animal groups on this farm",
                    enabled = !busy,
                )
            } else {
                FarmSearchSelector(
                    atomTag = FarmSelectionAtoms.ANIMAL_SELECTOR,
                    title = "Animal",
                    search = searchAnimals,
                    selected = animal,
                    onSelect = { animal = it },
                    emptyText = "No animals match on this device",
                    enabled = !busy,
                )
            }
            FarmEntitySelector(
                atomTag = HEALTH_FORMULARY_SELECTOR,
                title = "Vaccine (vet-approved formulary item)",
                options = formularyOptions,
                selectedId = formularyId.ifBlank { null },
                onSelect = { formularyId = it },
                emptyText = "No approved formulary items. Add one first.",
                enabled = !busy,
            )
            OutlinedTextField(dose, { dose = it }, label = { Text("Dose (optional)") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            OutlinedTextField(method, { method = it }, label = { Text("Method (optional)") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            OutlinedTextField(day, { day = it }, label = { Text("Vaccination date") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            Button(
                onClick = {
                    onRecord(species, animal?.id, groupId.ifBlank { null }, formularyId, dose.ifBlank { null }, method.ifBlank { null }, day)
                },
                enabled = !busy && species.isNotBlank() && targetOk && formularyId.isNotBlank() && dayOk,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Record vaccination") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

/**
 * FOS-HEALTH-005 — health case detail: one recorded vaccination with subject, vaccine and date.
 * Read-only recorded evidence. The record carries no recorder identity in the domain model.
 */
@Composable
private fun VaccinationDetailScreen(
    vaccinations: List<HealthVaccinationView>,
    recordId: String?,
    onBack: () -> Unit,
) {
    val record = vaccinations.firstOrNull { it.id == recordId }
    FarmOperationalPage(
        "FOS-HEALTH-005",
        "Vaccination record",
        "Recorded evidence; read-only.",
        FarmVisualClass.I3,
        onBack,
    ) {
        if (record == null) {
            Text("Record not found on this device")
        } else {
            FarmOperationalSection("Case") {
                Text("Subject: ${record.subjectLabel}")
                Text("Species: ${record.speciesCode}")
                Text("Vaccine: ${record.formularyLabel}")
                Text("Date: ${LocalDate.ofEpochDay(record.occurredEpochDay)}")
                record.dose?.let { Text("Dose: $it") }
                record.method?.let { Text("Method: $it") }
            }
        }
    }
}

/**
 * FOS-HEALTH-002 — today health actions: record-based vaccination review candidates and pending
 * health work (active withdrawals, recorded observations and treatments), one surface.
 */
@Composable
private fun TodayHealthActionsScreen(
    dueCandidates: List<VaccinationDueView>,
    counts: HealthReadModel,
    onOpenSchedule: () -> Unit,
    onCapture: () -> Unit,
    onBack: () -> Unit,
) {
    FarmOperationalPage(
        "FOS-HEALTH-002",
        "Today health actions",
        "Vaccination review candidates and pending health work for today.",
        FarmVisualClass.I2,
        onBack,
    ) {
        FarmOperationalSection("Vaccination review") {
            if (dueCandidates.isEmpty()) {
                Text("No vaccination review candidates today")
            } else {
                dueCandidates.take(20).forEach { candidate ->
                    Text("${candidate.label} — ${candidate.reason}")
                }
                if (dueCandidates.size > 20) Text("…and ${dueCandidates.size - 20} more on the schedule")
            }
            TextButton(onClick = onOpenSchedule) { Text("Open vaccination schedule") }
            Button(onClick = onCapture, modifier = Modifier.fillMaxWidth()) { Text("Record vaccination") }
        }
        FarmOperationalSection("Pending health work") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                HealthMetric("Active withdrawals", counts.activeWithdrawalCount)
                HealthMetric("Observations", counts.observationCount)
                HealthMetric("Treatments", counts.treatmentCount)
            }
        }
    }
}

/**
 * FOS-HEALTH-030 — health report: farm health summary computed from records on this device only.
 * Counts render as a dash until loaded; a bounded list is never presented as complete.
 */
@Composable
private fun HealthReportScreen(
    stats: HealthReportStats?,
    counts: HealthReadModel,
    vaccinations: List<HealthVaccinationView>,
    onBack: () -> Unit,
) {
    FarmOperationalPage(
        "FOS-HEALTH-030",
        "Health report",
        "Farm health summary from records on this device.",
        FarmVisualClass.I2,
        onBack,
    ) {
        FarmOperationalSection("Vaccination coverage") {
            val s = stats
            if (s == null) {
                Text("Loading…")
            } else {
                val coverage =
                    if (s.activeAnimalCount != null && s.activeAnimalCount > 0 && s.animalsEverVaccinated != null) {
                        "${s.animalsEverVaccinated} of ${s.activeAnimalCount} active animals have a vaccination record"
                    } else {
                        "No active animals on this farm"
                    }
                Text(coverage)
                Text("${s.dueCandidateCount} review candidate(s)")
                Text("${s.recentVaccinationCount} recent vaccination record(s) shown below")
            }
        }
        FarmOperationalSection("Recent vaccinations") {
            if (vaccinations.isEmpty()) {
                Text("No vaccinations recorded on this device")
            } else {
                vaccinations.forEach { record ->
                    Text("${record.subjectLabel} · ${record.formularyLabel} · ${LocalDate.ofEpochDay(record.occurredEpochDay)}")
                }
            }
        }
        FarmOperationalSection("Recorded activity") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                HealthMetric("Observations", counts.observationCount)
                HealthMetric("Treatments", counts.treatmentCount)
                HealthMetric("Active withdrawals", counts.activeWithdrawalCount)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                HealthMetric("Vet visits", counts.vetVisitCount)
                HealthMetric("Lab results", counts.labResultCount)
            }
        }
    }
}

/** FOS-ATOM-003 over the governed farm species, so a species-checked command never carries a typo. */
@Composable
private fun FarmSpeciesSelector(speciesCodes: List<String>, selected: String, busy: Boolean, onSelect: (String) -> Unit) {
    FarmEntitySelector(
        atomTag = FarmSelectionAtoms.SPECIES_SELECTOR,
        title = "Species",
        options = speciesCodes.map { code -> FarmSelectorOption(code, code.replaceFirstChar { it.uppercase() }) },
        selectedId = selected.ifBlank { null },
        onSelect = onSelect,
        emptyText = "No species available.",
        enabled = !busy,
    )
}
