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
import androidx.compose.ui.text.font.FontWeight
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalRows
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.design.FarmVisualClass
import java.time.LocalDate

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
}

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
) {
    var page by remember { mutableStateOf(entryPage.toHealthPage()) }
    val home = { page = HealthPage.DASHBOARD }
    val treatmentBack = if (entryPage == HealthEntryPage.TREATMENT) onBack else home
    val withdrawalBack = if (entryPage == HealthEntryPage.WITHDRAWALS) onBack else home
    val observationBack = if (entryPage == HealthEntryPage.RECORD_OBSERVATION) onBack else home
    val vetVisitBack = if (entryPage == HealthEntryPage.VET_VISIT) onBack else home
    val labResultBack = if (entryPage == HealthEntryPage.LAB_RESULT) onBack else home
    when (page) {
        HealthPage.DASHBOARD -> {
            HealthDashboard(rows, treatments, withdrawals, packs, error, { page = it }, onBack)
        }

        HealthPage.OBSERVATIONS -> {
            HealthRows("FOS-HEALTH-003", "Observations", rows, "No observations yet", error, home)
        }

        HealthPage.RECORD_OBSERVATION -> {
            RecordObservationScreen(busy, error, onRecord, observationBack)
        }

        HealthPage.FORMULARY -> {
            FormularyScreen(formulary, busy, error, onCreateFormulary, home)
        }

        HealthPage.TREATMENT -> {
            TreatmentScreen(formulary, treatments, busy, error, onRecordTreatment, treatmentBack)
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
            HealthRows("FOS-HEALTH-026", "Reference library", catalog, "No reference rows on this device", error, home)
        }

        HealthPage.PROTOCOLS -> {
            HealthRows("FOS-HEALTH-015", "Protocol packs", packs, "No accepted protocol packs", error, home)
        }

        HealthPage.ACCEPT_PROTOCOL -> {
            AcceptProtocolScreen(busy, error, onAcceptPack, home)
        }

        HealthPage.EDIT_PROTOCOL -> {
            ProtocolSlotScreen(busy, error, onAddPackSlot, home)
        }

        HealthPage.APPLY_PROTOCOL -> {
            ApplyProtocolScreen(busy, error, onApplyPack, home)
        }

        HealthPage.VET_VISIT -> {
            VetVisitScreen(busy, error, onRecordVetVisit, vetVisitBack)
        }

        HealthPage.LAB_RESULT -> {
            LabResultScreen(busy, error, onRecordLab, labResultBack)
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
    }

@Composable
private fun HealthDashboard(
    observations: List<String>,
    treatments: List<String>,
    withdrawals: List<String>,
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                HealthMetric("Observations", observations.size)
                HealthMetric("Treatments", treatments.size)
                HealthMetric("Withdrawals", withdrawals.size)
            }
        }
        FarmOperationalSection("Observe and review") {
            TextButton(onClick = { onOpen(HealthPage.OBSERVATIONS) }) { Text("Observation list") }
            Button(onClick = { onOpen(HealthPage.RECORD_OBSERVATION) }) { Text("Record observation") }
            TextButton(onClick = { onOpen(HealthPage.REFERENCES) }) { Text("Reference library") }
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
        FarmOperationalSection("Protocol packs") {
            Text("${packs.size} accepted pack(s)")
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
    value: Int,
) {
    androidx.compose.foundation.layout.Column {
        Text(value.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
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

@Composable
private fun RecordObservationScreen(
    busy: Boolean,
    error: String?,
    onRecord: (String, String, String, Boolean) -> Unit,
    onBack: () -> Unit,
) {
    var species by remember { mutableStateOf("goat") }
    var signs by remember { mutableStateOf("") }
    var firstAid by remember { mutableStateOf("") }
    FarmOperationalPage("FOS-HEALTH-004", "Record observation", "Record signs first; this is not a diagnosis.", onBack = onBack) {
        FarmOperationalSection("Observation") {
            OutlinedTextField(species, {
                species = it
            }, label = { Text("Species") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
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
            }, enabled = !busy && signs.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Save observation") }
            Button(onClick = {
                onRecord(species, signs, firstAid, true)
            }, enabled = !busy && signs.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Save as red flag") }
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
            OutlinedTextField(formularyId, {
                formularyId = it
            }, label = { Text("Formulary item id") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
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
            OutlinedTextField(packId, {
                packId = it
            }, label = { Text("Pack id") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
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
    busy: Boolean,
    error: String?,
    onApply: (String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var packId by remember { mutableStateOf("") }
    var animalId by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    FarmOperationalPage(
        "FOS-HEALTH-018",
        "Apply protocol pack",
        "Create the pack's due tasks for an animal from the selected anchor date.",
        FarmVisualClass.I4,
        onBack,
    ) {
        FarmOperationalSection("Apply pack") {
            OutlinedTextField(packId, {
                packId = it
            }, label = { Text("Pack id") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            OutlinedTextField(animalId, {
                animalId = it
            }, label = { Text("Animal id") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
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
    busy: Boolean,
    error: String?,
    onRecord: (String, String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var species by remember { mutableStateOf("goat") }
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
            OutlinedTextField(species, {
                species = it
            }, label = { Text("Species") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
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
                    !busy && reason.isNotBlank() && vet.isNotBlank() && runCatching { LocalDate.parse(day) }.isSuccess,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Record vet visit") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun LabResultScreen(
    busy: Boolean,
    error: String?,
    onRecord: (String, String, String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var animalId by remember { mutableStateOf("") }
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
            OutlinedTextField(animalId, {
                animalId = it
            }, label = { Text("Animal id") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
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
