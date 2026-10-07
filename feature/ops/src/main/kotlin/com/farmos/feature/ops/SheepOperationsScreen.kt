package com.farmos.feature.ops

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.farmos.core.design.EidReaderAdapter
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.design.FarmVisualClass
import com.farmos.core.design.NoOpEidReaderAdapter
import com.farmos.core.design.runSuspendCatching
import java.time.LocalDate
import kotlinx.coroutines.launch

data class SheepOperationsActions(
    val onJoining: (groupId: String, day: String) -> Unit,
    val onScan: (animalId: String, result: String, day: String) -> Unit,
    val onLambing: (animalId: String, born: String, live: String, dead: String, day: String) -> Unit,
    val onMarking: (groupId: String, animalId: String, count: String, day: String) -> Unit,
    val onWeaning: (groupId: String, animalId: String, count: String, day: String) -> Unit,
    val onWool: (groupId: String, animalId: String, grams: String, day: String) -> Unit,
    val onShearing: (groupId: String, animalId: String, kind: String, grams: String, day: String) -> Unit,
    val onMicron: (groupId: String, animalId: String, tenths: String, day: String) -> Unit,
    val onFamacha: (animalId: String, score: String, day: String) -> Unit,
    val onDag: (animalId: String, score: String, day: String) -> Unit,
    val onFootrot: (animalId: String, score: String, day: String) -> Unit,
    val onFlystrike: (animalId: String, score: String, day: String) -> Unit,
    /** FOS-SHEEP-008 — (animalId, score tenths, day); scale is fixed to 1-5 for sheep. */
    val onSheepBcs: (String, String, String) -> Unit = { _, _, _ -> },
    val onIdentifier: (animalId: String, type: String, value: String, day: String) -> Unit,
    val onMovement: (animalId: String, direction: String, from: String, to: String, day: String) -> Unit,
    /** FOS-SHEEP-028 — (groupId, paddockId, day); starts a grazing session for the group. */
    val onPaddockAssign: (String, String, String) -> Unit = { _, _, _ -> },
    val onPedigree: (animalId: String, parentId: String, relation: String) -> Unit,
    /** FOS-SHEEP-005 — EID reader adapter; NoOp unless the host fits the reader path. */
    val eidReaderAdapter: EidReaderAdapter = NoOpEidReaderAdapter,
    /** Whether the user explicitly enabled the EID reader path. */
    val isEidReaderEnabled: Boolean = false,
    /** Explicit user opt-in for the EID reader path (host handles permission). */
    val onToggleEidReader: (Boolean) -> Unit = {},
    /** Assigns a scanned or typed EID value through the governed identifier command. */
    val onAssignEid: (animalId: String, value: String, day: String) -> Unit = { _, _, _ -> },
)

private enum class SheepOpsPage {
    HOME,
    JOINING,
    SCAN,
    LAMBING,
    MARKING,
    WEANING,
    WOOL,
    SHEARING,
    MICRON,
    FAMACHA,
    DAG,
    FOOTROT,
    FLYSTRIKE,
    BCS,
    IDENTIFIER,
    MOVEMENT,
    PADDOCK_ASSIGN,
    PEDIGREE,
    COI,
    MATE_COMPARE,
    HEALTH_SUMMARY,
    TIMELINE,
    WOOL_DASHBOARD,
    GROWTH_HISTORY,
    LAMBING_DUE,
    LAMB_PROFILE,
    SHEEP_REPORT,
    EID_SCAN,
}

@Composable
fun SheepOperationsScreen(
    selectedAnimalId: String?,
    busy: Boolean,
    error: String?,
    actions: SheepOperationsActions,
    onBack: () -> Unit,
    loadRecords: suspend (String) -> SheepAnimalRecords = { SheepAnimalRecords() },
    loadWool: suspend () -> SheepWoolRecords = { SheepWoolRecords() },
    today: LocalDate = LocalDate.now(),
    loadLambingDue: suspend () -> SheepLambingDue = { SheepLambingDue(emptyList(), emptyList(), 147) },
    /** FOS-SHEEP-015 — lamb birth profile for the selected animal. */
    loadLambProfile: suspend (String) -> SheepLambProfile = { SheepLambProfile() },
    /** FOS-SHEEP-032 — farm sheep aggregates. */
    loadSheepReport: suspend () -> SheepFlockReport = { SheepFlockReport() },
    /** FOS-SHEEP-028 — (groupId, groupName) options for paddock assignment. */
    loadSheepGroups: suspend () -> List<SheepGroupOption> = { emptyList() },
    /** FOS-SHEEP-028 — (paddockId, paddockName) options for paddock assignment. */
    loadPaddocks: suspend () -> List<SheepPaddockOption> = { emptyList() },
) {
    var page by remember { mutableStateOf(SheepOpsPage.HOME) }
    val home = { page = SheepOpsPage.HOME }
    when (page) {
        SheepOpsPage.HOME -> {
            SheepOpsHome(selectedAnimalId, error, { page = it }, onBack)
        }

        SheepOpsPage.JOINING -> {
            SheepJoiningScreen(busy, error, actions.onJoining, home)
        }

        SheepOpsPage.SCAN -> {
            SheepScanScreen(selectedAnimalId, busy, error, actions.onScan, home)
        }

        SheepOpsPage.LAMBING -> {
            SheepLambingScreen(selectedAnimalId, busy, error, actions.onLambing, home)
        }

        SheepOpsPage.MARKING -> {
            SheepGroupAnimalCountScreen(
                "FOS-SHEEP-016",
                "Lamb marking",
                "Marked count",
                selectedAnimalId,
                busy,
                error,
                actions.onMarking,
                home,
            )
        }

        SheepOpsPage.WEANING -> {
            SheepGroupAnimalCountScreen(
                "FOS-SHEEP-017",
                "Weaning",
                "Weaned count",
                selectedAnimalId,
                busy,
                error,
                actions.onWeaning,
                home,
            )
        }

        SheepOpsPage.WOOL -> {
            SheepWoolScreen(selectedAnimalId, busy, error, actions.onWool, home)
        }

        SheepOpsPage.SHEARING -> {
            SheepShearingScreen(selectedAnimalId, busy, error, actions.onShearing, home)
        }

        SheepOpsPage.MICRON -> {
            SheepMicronScreen(selectedAnimalId, busy, error, actions.onMicron, home)
        }

        SheepOpsPage.FAMACHA -> {
            SheepScoreScreen(
                "FOS-SHEEP-009",
                "FAMACHA",
                "Score 1–5 from the approved card. This is not a diagnosis.",
                selectedAnimalId,
                busy,
                error,
                actions.onFamacha,
                home,
                FarmVisualClass.I4,
            )
        }

        SheepOpsPage.DAG -> {
            SheepScoreScreen(
                "FOS-SHEEP-022",
                "Dag score",
                "Record the observed dag score.",
                selectedAnimalId,
                busy,
                error,
                actions.onDag,
                home,
            )
        }

        SheepOpsPage.FOOTROT -> {
            SheepScoreScreen(
                "FOS-SHEEP-023",
                "Footrot",
                "Record the observed score and escalate according to farm protocol.",
                selectedAnimalId,
                busy,
                error,
                actions.onFootrot,
                home,
                FarmVisualClass.I4,
            )
        }

        SheepOpsPage.FLYSTRIKE -> {
            SheepScoreScreen(
                "FOS-SHEEP-024",
                "Flystrike",
                "Record the observed score and escalate according to farm protocol.",
                selectedAnimalId,
                busy,
                error,
                actions.onFlystrike,
                home,
                FarmVisualClass.I4,
            )
        }

        SheepOpsPage.IDENTIFIER -> {
            SheepIdentifierScreen(selectedAnimalId, busy, error, actions.onIdentifier, home)
        }

        SheepOpsPage.MOVEMENT -> {
            SheepMovementScreen(selectedAnimalId, busy, error, actions.onMovement, home)
        }

        SheepOpsPage.BCS -> {
            SheepBcsScreen(selectedAnimalId, busy, error, actions.onSheepBcs, home)
        }

        SheepOpsPage.PADDOCK_ASSIGN -> {
            SheepPaddockAssignScreen(busy, error, loadSheepGroups, loadPaddocks, actions.onPaddockAssign, home)
        }

        SheepOpsPage.LAMB_PROFILE -> {
            SheepLambProfileScreen(selectedAnimalId, loadLambProfile, home)
        }

        SheepOpsPage.SHEEP_REPORT -> {
            SheepFlockReportScreen(loadSheepReport, home)
        }

        SheepOpsPage.PEDIGREE -> {
            SheepPedigreeScreen(selectedAnimalId, busy, error, actions.onPedigree, home)
        }

        SheepOpsPage.COI -> OpsCoiAnalysisPage("Ewe", "Ram", "lambs", busy, home)
        SheepOpsPage.MATE_COMPARE -> OpsMateComparePage("Ewe", "Ram", "lambs", busy, home)

        SheepOpsPage.HEALTH_SUMMARY -> SheepHealthSummaryScreen(selectedAnimalId, today, loadRecords, home)
        SheepOpsPage.TIMELINE -> SheepTimelineScreen(selectedAnimalId, loadRecords, home)
        SheepOpsPage.GROWTH_HISTORY -> SheepGrowthHistoryScreen(selectedAnimalId, loadRecords, home)
        SheepOpsPage.WOOL_DASHBOARD -> SheepWoolDashboardScreen(loadWool, home)
        SheepOpsPage.LAMBING_DUE -> SheepLambingDueScreen(loadLambingDue, today, home)
        SheepOpsPage.EID_SCAN -> SheepEidScanScreen(
            selectedAnimalId = selectedAnimalId,
            adapter = actions.eidReaderAdapter,
            adapterEnabled = actions.isEidReaderEnabled,
            onToggleAdapter = actions.onToggleEidReader,
            onAssignEid = actions.onAssignEid,
            today = today,
            onBack = home,
        )
    }
}

@Composable
private fun SheepOpsHome(
    selectedId: String?,
    error: String?,
    onOpen: (SheepOpsPage) -> Unit,
    onBack: () -> Unit,
) {
    FarmOperationalPage(
        screenId = "FOS-SHEEP-010",
        title = "Joining & flock operations",
        subtitle = "Breeding, lambing, wool and field health records.",
        visualClass = FarmVisualClass.I2,
        onBack = onBack,
    ) {
        FarmOperationalSection("Selected sheep") { Text(selectedId ?: "No individual selected — group workflows remain available.") }
        FarmOperationalSection("Reproduction") {
            SheepNav("Record joining") { onOpen(SheepOpsPage.JOINING) }
            SheepNav("Pregnancy scan") { onOpen(SheepOpsPage.SCAN) }
            SheepNav("Lambing due") { onOpen(SheepOpsPage.LAMBING_DUE) }
            SheepNav("Record lambing") { onOpen(SheepOpsPage.LAMBING) }
            SheepNav("Lamb marking") { onOpen(SheepOpsPage.MARKING) }
            SheepNav("EID scan") { onOpen(SheepOpsPage.EID_SCAN) }
            SheepNav("Weaning") { onOpen(SheepOpsPage.WEANING) }
        }
        FarmOperationalSection("Wool") {
            SheepNav("Fleece record") { onOpen(SheepOpsPage.WOOL) }
            SheepNav("Shearing") { onOpen(SheepOpsPage.SHEARING) }
            SheepNav("Micron / fibre result") { onOpen(SheepOpsPage.MICRON) }
            SheepNav("Wool dashboard") { onOpen(SheepOpsPage.WOOL_DASHBOARD) }
        }
        FarmOperationalSection("Sheep records") {
            SheepNav("Lamb profile") { onOpen(SheepOpsPage.LAMB_PROFILE) }
            SheepNav("Sheep report") { onOpen(SheepOpsPage.SHEEP_REPORT) }
            SheepNav("Health summary") { onOpen(SheepOpsPage.HEALTH_SUMMARY) }
            SheepNav("Timeline") { onOpen(SheepOpsPage.TIMELINE) }
            SheepNav("Growth history") { onOpen(SheepOpsPage.GROWTH_HISTORY) }
        }
        FarmOperationalSection("Field health") {
            SheepNav("FAMACHA") { onOpen(SheepOpsPage.FAMACHA) }
            SheepNav("Body condition score") { onOpen(SheepOpsPage.BCS) }
            SheepNav("Dag score") { onOpen(SheepOpsPage.DAG) }
            SheepNav("Footrot") { onOpen(SheepOpsPage.FOOTROT) }
            SheepNav("Flystrike") { onOpen(SheepOpsPage.FLYSTRIKE) }
        }
        FarmOperationalSection("Grazing") {
            SheepNav("Paddock assignment") { onOpen(SheepOpsPage.PADDOCK_ASSIGN) }
        }
        FarmOperationalSection("Identity & traceability") {
            SheepNav("Official identifier") { onOpen(SheepOpsPage.IDENTIFIER) }
            SheepNav("Movement") { onOpen(SheepOpsPage.MOVEMENT) }
            SheepNav("Pedigree link") { onOpen(SheepOpsPage.PEDIGREE) }
            SheepNav("Inbreeding check") { onOpen(SheepOpsPage.COI) }
            SheepNav("Compare rams") { onOpen(SheepOpsPage.MATE_COMPARE) }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun SheepNav(
    label: String,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label) }
}

@Composable
private fun SheepJoiningScreen(
    busy: Boolean,
    error: String?,
    onRecord: (String, String) -> Unit,
    onBack: () -> Unit,
) {
    var groupId by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    SheepFormPage(
        "FOS-SHEEP-011",
        "Record joining",
        "Joining creates the governed scan, pre-lamb, paddock and lambing tasks.",
        busy,
        error,
        onBack,
    ) {
        OpsGroupPicker("Sheep mob", groupId, busy) { groupId = it }
        Field(day, { day = it }, "Joining start", busy)
        Button(onClick = {
            onRecord(groupId, day)
        }, enabled = !busy && groupId.isNotBlank() && validDate(day), modifier = Modifier.fillMaxWidth()) { Text("Record joining") }
    }
}

@Composable
private fun SheepScanScreen(
    selectedId: String?,
    busy: Boolean,
    error: String?,
    onRecord: (String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var animalId by remember(selectedId) { mutableStateOf(selectedId.orEmpty()) }
    var result by remember { mutableStateOf("single") }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    SheepFormPage("FOS-SHEEP-012", "Pregnancy scan", "Result must be dry, single, twin or triplet.", busy, error, onBack) {
        OpsAnimalPicker("Ewe", animalId, OpsAnimalFilter.FEMALE, busy) { animalId = it }
        Field(result, { result = it }, "Scan result", busy)
        Field(day, { day = it }, "Scan date", busy)
        Button(onClick = {
            onRecord(animalId, result, day)
        }, enabled = !busy && animalId.isNotBlank() && validDate(day), modifier = Modifier.fillMaxWidth()) { Text("Record scan") }
    }
}

@Composable
private fun SheepLambingScreen(
    selectedId: String?,
    busy: Boolean,
    error: String?,
    onRecord: (String, String, String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var animalId by remember(selectedId) { mutableStateOf(selectedId.orEmpty()) }
    var born by remember { mutableStateOf("") }
    var live by remember { mutableStateOf("") }
    var dead by remember { mutableStateOf("0") }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    SheepFormPage("FOS-SHEEP-014", "Record lambing", "Live plus dead must equal born.", busy, error, onBack, FarmVisualClass.I4) {
        OpsAnimalPicker("Ewe", animalId, OpsAnimalFilter.FEMALE, busy) { animalId = it }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            CompactField(born, { born = it }, "Born", busy, Modifier.weight(1f))
            CompactField(live, { live = it }, "Live", busy, Modifier.weight(1f))
            CompactField(dead, { dead = it }, "Dead", busy, Modifier.weight(1f))
        }
        Field(day, { day = it }, "Lambing date", busy)
        Button(
            onClick = { onRecord(animalId, born, live, dead, day) },
            enabled =
                !busy && animalId.isNotBlank() && born.isNotBlank() && live.isNotBlank() && validDate(day),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Record lambing") }
    }
}

@Composable
private fun SheepGroupAnimalCountScreen(
    screenId: String,
    title: String,
    countLabel: String,
    selectedId: String?,
    busy: Boolean,
    error: String?,
    onRecord: (String, String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var groupId by remember { mutableStateOf("") }
    var animalId by remember(selectedId) { mutableStateOf(selectedId.orEmpty()) }
    var count by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    SheepFormPage(screenId, title, "Use a mob or an individual sheep plus the recorded count.", busy, error, onBack) {
        Field(groupId, { groupId = it }, "Mob id (optional)", busy)
        OpsAnimalPicker("Animal (optional)", animalId, OpsAnimalFilter.ANY, busy, optional = true) { animalId = it }
        Field(count, { count = it }, countLabel, busy)
        Field(day, { day = it }, "Date", busy)
        Button(
            onClick = { onRecord(groupId, animalId, count, day) },
            enabled =
                !busy && (groupId.isNotBlank() || animalId.isNotBlank()) && count.isNotBlank() && validDate(day),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Record ${title.lowercase()}") }
    }
}

@Composable
private fun SheepWoolScreen(
    selectedId: String?,
    busy: Boolean,
    error: String?,
    onRecord: (String, String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var groupId by remember { mutableStateOf("") }
    var animalId by remember(selectedId) { mutableStateOf(selectedId.orEmpty()) }
    var grams by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    SheepFormPage("FOS-SHEEP-020", "Fleece record", "Record greasy fleece weight in grams for a sheep or mob.", busy, error, onBack) {
        Field(groupId, { groupId = it }, "Mob id (optional)", busy)
        Field(animalId, { animalId = it }, "Sheep id (optional)", busy)
        Field(grams, { grams = it }, "Greasy grams", busy)
        Field(day, { day = it }, "Clip date", busy)
        Button(
            onClick = { onRecord(groupId, animalId, grams, day) },
            enabled =
                !busy && (groupId.isNotBlank() || animalId.isNotBlank()) && grams.isNotBlank() && validDate(day),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Record fleece") }
    }
}

@Composable
private fun SheepShearingScreen(
    selectedId: String?,
    busy: Boolean,
    error: String?,
    onRecord: (String, String, String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var groupId by remember { mutableStateOf("") }
    var animalId by remember(selectedId) { mutableStateOf(selectedId.orEmpty()) }
    var kind by remember { mutableStateOf("shearing") }
    var grams by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    SheepFormPage("FOS-SHEEP-019", "Shearing", "Kinds are shearing, crutching or classing.", busy, error, onBack) {
        Field(groupId, { groupId = it }, "Mob id (optional)", busy)
        Field(animalId, { animalId = it }, "Sheep id (optional)", busy)
        Field(kind, { kind = it }, "Kind", busy)
        Field(grams, { grams = it }, "Greasy grams (optional)", busy)
        Field(day, { day = it }, "Date", busy)
        Button(
            onClick = { onRecord(groupId, animalId, kind, grams, day) },
            enabled =
                !busy && (groupId.isNotBlank() || animalId.isNotBlank()) && validDate(day),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Record shearing") }
    }
}

@Composable
private fun SheepMicronScreen(
    selectedId: String?,
    busy: Boolean,
    error: String?,
    onRecord: (String, String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var groupId by remember { mutableStateOf("") }
    var animalId by remember(selectedId) { mutableStateOf(selectedId.orEmpty()) }
    var tenths by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    SheepFormPage("FOS-SHEEP-021", "Micron / fibre result", "Store micron as tenths from 80 to 500.", busy, error, onBack) {
        Field(groupId, { groupId = it }, "Mob id (optional)", busy)
        Field(animalId, { animalId = it }, "Sheep id (optional)", busy)
        Field(tenths, { tenths = it }, "Micron tenths", busy)
        Field(day, { day = it }, "Result date", busy)
        Button(
            onClick = { onRecord(groupId, animalId, tenths, day) },
            enabled =
                !busy && (groupId.isNotBlank() || animalId.isNotBlank()) && tenths.isNotBlank() && validDate(day),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Record micron result") }
    }
}

@Composable
private fun SheepScoreScreen(
    screenId: String,
    title: String,
    subtitle: String,
    selectedId: String?,
    busy: Boolean,
    error: String?,
    onRecord: (String, String, String) -> Unit,
    onBack: () -> Unit,
    visualClass: FarmVisualClass = FarmVisualClass.I3,
) {
    var animalId by remember(selectedId) { mutableStateOf(selectedId.orEmpty()) }
    var score by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    SheepFormPage(screenId, title, subtitle, busy, error, onBack, visualClass) {
        OpsAnimalPicker("Sheep", animalId, OpsAnimalFilter.ANY, busy) { animalId = it }
        Field(score, { score = it }, "Score", busy)
        Field(day, { day = it }, "Date", busy)
        Button(
            onClick = { onRecord(animalId, score, day) },
            enabled =
                !busy && animalId.isNotBlank() && score.isNotBlank() && validDate(day),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Record $title") }
    }
}

@Composable
private fun SheepIdentifierScreen(
    selectedId: String?,
    busy: Boolean,
    error: String?,
    onRecord: (String, String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var animalId by remember(selectedId) { mutableStateOf(selectedId.orEmpty()) }
    var type by remember { mutableStateOf("ear_tag") }
    var value by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    SheepFormPage("FOS-SHEEP-029", "Official identifier", "Bind an approved identifier type to this sheep.", busy, error, onBack) {
        OpsAnimalPicker("Sheep", animalId, OpsAnimalFilter.ANY, busy) { animalId = it }
        Field(type, { type = it }, "Identifier type", busy)
        Field(value, { value = it }, "Identifier value", busy)
        Field(day, {
            day =
                it
        }, "Date", busy)
        Button(
            onClick = { onRecord(animalId, type, value, day) },
            enabled =
                !busy && animalId.isNotBlank() && value.isNotBlank() && validDate(day),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Assign identifier") }
    }
}

@Composable
private fun SheepMovementScreen(
    selectedId: String?,
    busy: Boolean,
    error: String?,
    onRecord: (String, String, String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var animalId by remember(selectedId) { mutableStateOf(selectedId.orEmpty()) }
    var direction by remember { mutableStateOf("on") }
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    SheepFormPage("FOS-SHEEP-027", "Movement", "Record on, off or transfer movement and places.", busy, error, onBack) {
        OpsAnimalPicker("Sheep", animalId, OpsAnimalFilter.ANY, busy) { animalId = it }
        Field(direction, { direction = it }, "Direction", busy)
        Field(from, { from = it }, "From place", busy)
        Field(to, { to = it }, "To place", busy)
        Field(day, { day = it }, "Date", busy)
        Button(onClick = {
            onRecord(animalId, direction, from, to, day)
        }, enabled = !busy && animalId.isNotBlank() && validDate(day), modifier = Modifier.fillMaxWidth()) { Text("Record movement") }
    }
}

@Composable
private fun SheepPedigreeScreen(
    selectedId: String?,
    busy: Boolean,
    error: String?,
    onRecord: (String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var animalId by remember(selectedId) { mutableStateOf(selectedId.orEmpty()) }
    var parentId by remember { mutableStateOf("") }
    var relation by remember { mutableStateOf("sire") }
    SheepFormPage(
        "FOS-SHEEP-026",
        "Pedigree",
        "Record parentage. Inbreeding check uses it; genetic merit is not calculated.",
        busy,
        error,
        onBack,
    ) {
        OpsAnimalPicker("Sheep", animalId, OpsAnimalFilter.ANY, busy) { animalId = it }
        OpsAnimalPicker("Parent", parentId, OpsAnimalFilter.ANY, busy, parent = true) { parentId = it }
        Field(relation, {
            relation =
                it
        }, "Relation", busy)
        Button(onClick = {
            onRecord(animalId, parentId, relation)
        }, enabled = !busy && animalId.isNotBlank() && parentId.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Link parent") }
    }
}

/** FOS-SHEEP-008 — sheep body condition score on the fixed 1-5 scale, stored as tenths. */
@Composable
private fun SheepBcsScreen(
    selectedId: String?,
    busy: Boolean,
    error: String?,
    onRecord: (String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var animalId by remember(selectedId) { mutableStateOf(selectedId.orEmpty()) }
    var score by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    SheepFormPage(
        "FOS-SHEEP-008",
        "Body condition score",
        "Sheep use the 1-5 scale; the score is stored as tenths (e.g. 3.0).",
        busy,
        error,
        onBack,
    ) {
        OpsAnimalPicker("Sheep", animalId, OpsAnimalFilter.ANY, busy) { animalId = it }
        Field(score, { score = it }, "Score (1.0-5.0)", busy)
        Field(day, { day = it }, "Date", busy)
        Button(
            onClick = { onRecord(animalId, score, day) },
            enabled = !busy && animalId.isNotBlank() && score.isNotBlank() && validDate(day),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Record BCS") }
    }
}

/** FOS-SHEEP-028 — paddock assignment: start a grazing session for a sheep group in a paddock. */
@Composable
private fun SheepPaddockAssignScreen(
    busy: Boolean,
    error: String?,
    loadGroups: suspend () -> List<SheepGroupOption>,
    loadPaddocks: suspend () -> List<SheepPaddockOption>,
    onAssign: (String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var groups by remember { mutableStateOf(emptyList<SheepGroupOption>()) }
    var paddocks by remember { mutableStateOf(emptyList<SheepPaddockOption>()) }
    var groupId by remember { mutableStateOf("") }
    var paddockId by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    LaunchedEffect(Unit) {
        runCatching {
            groups = loadGroups()
            paddocks = loadPaddocks()
        }
    }
    SheepFormPage(
        "FOS-SHEEP-028",
        "Paddock assignment",
        "Move a sheep group into a paddock by starting a grazing session.",
        busy,
        error,
        onBack,
    ) {
        if (groups.isEmpty()) {
            Text("No sheep groups on this device.", color = MaterialTheme.colorScheme.error)
        } else {
            Field(groupId, { groupId = it }, "Group ID", busy)
        }
        if (paddocks.isEmpty()) {
            Text("No paddocks on this device.", color = MaterialTheme.colorScheme.error)
        } else {
            Field(paddockId, { paddockId = it }, "Paddock ID", busy)
        }
        Field(day, { day = it }, "Date", busy)
        Button(
            onClick = { onAssign(groupId, paddockId, day) },
            enabled = !busy && groupId.isNotBlank() && paddockId.isNotBlank() && validDate(day),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Assign to paddock") }
    }
}

@Composable
private fun SheepFormPage(
    screenId: String,
    title: String,
    subtitle: String,
    busy: Boolean,
    error: String?,
    onBack: () -> Unit,
    visualClass: FarmVisualClass = FarmVisualClass.I3,
    content: @Composable () -> Unit,
) {
    FarmOperationalPage(screenId, title, subtitle, visualClass, onBack) {
        FarmOperationalSection("Record") { content() }
        if (busy) Text("Saving on this device…", color = MaterialTheme.colorScheme.primary)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun Field(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    busy: Boolean,
) {
    OutlinedTextField(value, onValue, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
}

@Composable
private fun CompactField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    busy: Boolean,
    modifier: Modifier,
) {
    OutlinedTextField(value, onValue, label = { Text(label) }, modifier = modifier, enabled = !busy, singleLine = true)
}

private fun validDate(value: String): Boolean = runCatching { LocalDate.parse(value) }.isSuccess

/**
 * FOS-SHEEP-005 — EID Scan.
 *
 * GENUINE GAP — not implemented: there is no RFID/EID hardware adapter in this build (no tag
 * reader discovery, scan, or EID-ingest path), so a scan UI would be a fake.
 * Required piece: a Farm OS-owned RFID reader adapter behind the hardware boundary, with a
 * governed EID-ingest command. This screen fails closed.
 */
/**
 * FOS-SHEEP-005 — EID scan: optional bounded reader adapter.
 *
 * Manual identifier entry is the primary path and always works. The reader path is
 * disabled by default and only runs after explicit user opt-in. A scanned tag is
 * advisory: it fills the entry field and the user confirms it through the exact same
 * governed assign-identifier command (with farm-scoped uniqueness validation) as a
 * typed value. Hardware never has authority over domain truth.
 */
@Composable
internal fun SheepEidScanScreen(
    selectedAnimalId: String?,
    adapter: EidReaderAdapter = NoOpEidReaderAdapter,
    adapterEnabled: Boolean = false,
    onToggleAdapter: (Boolean) -> Unit = {},
    onAssignEid: (animalId: String, value: String, day: String) -> Unit = { _, _, _ -> },
    today: LocalDate = LocalDate.now(),
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var eidValue by remember { mutableStateOf("") }
    var note by remember { mutableStateOf<String?>(null) }
    var reading by remember { mutableStateOf(false) }
    FarmOperationalPage(
        screenId = "FOS-SHEEP-005",
        title = "EID scan",
        subtitle = "Electronic identification, manual-first.",
        onBack = onBack,
        backLabel = "Sheep",
    ) {
        FarmOperationalSection("Manual EID entry (primary)") {
            Text("Always available — no reader needed. The value is validated and checked for farm-wide uniqueness before it is recorded.")
            OutlinedTextField(
                value = eidValue,
                onValueChange = { eidValue = it },
                label = { Text("EID tag value") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = {
                val animalId = selectedAnimalId
                when {
                    animalId == null -> note = "Select a sheep first: the EID records against the selected animal."
                    eidValue.isBlank() -> note = "Enter an EID tag value first."
                    else -> {
                        note = null
                        onAssignEid(animalId, eidValue.trim(), today.toString())
                        eidValue = ""
                    }
                }
            }) { Text("Assign EID") }
            if (selectedAnimalId == null) {
                Text("No individual selected — group workflows remain available elsewhere.")
            }
        }
        FarmOperationalSection("EID reader (optional)") {
            Text(
                "Disabled by default. A scanned tag only fills the entry field above; " +
                    "you review and confirm it exactly like a typed value. No tag is shown rather than a fabricated scan.",
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Enable EID reader")
                Switch(checked = adapterEnabled, onCheckedChange = onToggleAdapter)
            }
            if (adapterEnabled) {
                note?.let { Text(it) }
                Button(
                    onClick = {
                        reading = true
                        note = null
                        scope.launch {
                            runSuspendCatching { adapter.readTag().getOrThrow() }
                                .onSuccess { tag ->
                                    eidValue = tag.rawValue
                                    note = "Tag read (${tag.kind}). Review the value above, then Assign EID."
                                }
                                .onFailure { note = "Read failed: ${it.message}" }
                            reading = false
                        }
                    },
                    enabled = !reading,
                ) { Text(if (reading) "Reading…" else "Read tag") }
            }
        }
    }
}
