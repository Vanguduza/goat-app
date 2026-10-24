package com.farmos.feature.rabbit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.farmos.core.design.FarmPrimaryButton as Button
import androidx.compose.material3.MaterialTheme
import com.farmos.core.design.FarmOutlinedTextField as OutlinedTextField
import androidx.compose.material3.Text
import com.farmos.core.design.FarmActionButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farmos.core.design.AnimalFarmCanvas
import com.farmos.core.design.AnimalFarmFamily
import com.farmos.core.design.AnimalFarmModuleHeader
import com.farmos.core.design.FarmEntitySelector
import com.farmos.core.design.FarmIllustratedSectionSurface
import com.farmos.core.design.FarmSectionTabs
import com.farmos.core.design.FarmMetricStrip
import com.farmos.core.design.FarmMetricValue
import com.farmos.core.design.FarmMetricBars
import com.farmos.core.design.FarmBarValue
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalRows
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.design.FarmSearchSelector
import com.farmos.core.design.FarmSelectionAtoms
import com.farmos.core.design.FarmSelectorOption
import com.farmos.core.design.FarmSelectorSearch
import com.farmos.core.design.FarmVisualClass
import com.farmos.core.design.FosDimens
import com.farmos.core.design.NoFarmSelectorSearch
import com.farmos.domain.rabbit.KudbatSemiIntensiveExcel
import com.farmos.domain.rabbit.RabbitNestBoxCycle
import java.time.LocalDate

private enum class RabbitPage {
    DASHBOARD,
    ANIMALS,
    REGISTER,
    CAGES,
    WAVE,
    PALPATION,
    KINDLING,
    FOSTER,
    WEAN,
    OUTCOME,
    NESTS,
    GI_STASIS,
    WEIGHT,
    CAGE_OCCUPANCY,
    CAGE_DETAIL,
    KINDLING_DUE,
    LITTER_PROFILE,
    KIT_CENSUS,
    PROFILE,
    PEDIGREE,
    COI,
    MATE_COMPARE,
    REPLACEMENT_COMPARE,
    SALE_ALLOCATION,
    RABBIT_REPORT,
}

/** Rabbit biology is wave/cage/litter-first; this is not a Goat layout with rabbit labels. */
@Composable
fun RabbitProgrammeScreen(
    cages: List<String>,
    waves: List<String>,
    availableBoxes: Long,
    busy: Boolean,
    error: String?,
    does: List<String>,
    onRegisterDoe: (tag: String, name: String?, sex: String) -> Unit,
    onCreateCage: (code: String) -> Unit,
    onCreateNestBox: (cageCode: String, boxCode: String) -> Unit,
    onCreateWave: (cageCode: String, doeCount: Int, matingDay: String) -> Unit,
    onPalpate: (waveId: String, result: String, day: String) -> Unit,
    onKindle: (waveId: String, live: String, dead: String, day: String) -> Unit,
    onFoster: (fromWaveId: String, toWaveId: String, kits: String, day: String, ackOutside: Boolean) -> Unit,
    nestBoxes: List<String> = emptyList(),
    onSetNestStatus: (boxId: String, status: String) -> Unit = { _, _ -> },
    onWean: (waveId: String, count: String, day: String) -> Unit = { _, _, _ -> },
    onRecordOutcome: (waveId: String, outcome: String, day: String) -> Unit = { _, _, _ -> },
    onRecordGiStasis: (animalId: String, signs: String, day: String) -> Unit = { _, _, _ -> },
    /** FOS-RABBIT-032 — record a governed rabbit weight. */
    onRecordWeight: (animalId: String, weightKg: String, day: String, notes: String) -> Unit = { _, _, _, _ -> },
    onDecideRetention: (kitId: String, decision: String, day: String) -> Unit = { _, _, _ -> },
    onAllocateSale: (kitId: String, targetWeightGrams: String, targetDay: String, purpose: String) -> Unit = { _, _, _, _ -> },
    onBack: () -> Unit,
    records: RabbitRecords = RabbitRecords(),
    today: LocalDate = LocalDate.now(),
    /** Exhaustive count of rabbits the [does] list can show; the list itself is bounded. */
    rabbitCount: Int? = null,
    /** This farm's breeding waves; every wave event command accepts only one of these. */
    waveOptions: List<FarmSelectorOption> = emptyList(),
    /** This farm's nest boxes with their current status; the next status is limited to the governed cycle. */
    nestBoxChoices: List<RabbitNestBoxChoice> = emptyList(),
    /** Whole-farm rabbit search for health subjects; never the capped presentation list. */
    searchRabbits: FarmSelectorSearch = NoFarmSelectorSearch,
    /** Registered does and bucks, each opening its profile; empty keeps the plain list. */
    rabbits: List<RabbitAnimalView> = emptyList(),
    /** A rabbit's exit record and reversal (D-022), shown on its profile. */
    rabbitExit: @Composable (RabbitAnimalView) -> Unit = {},
    /** Photos and documents on a rabbit's profile (D-015). */
    rabbitAttachments: @Composable (RabbitAnimalView) -> Unit = {},
    /** Rabbit pedigree and the kits' inbreeding (D-023); null hides both. */
    pedigree: RabbitPedigreePorts? = null,
    entryProfile: RabbitProfileEntry? = null,
    loading: Boolean = false,
) {
    var page by remember(entryProfile) { mutableStateOf(if (entryProfile == null) RabbitPage.DASHBOARD else RabbitPage.PROFILE) }
    var profileId by remember(entryProfile) { mutableStateOf(entryProfile?.animalId) }
    var dashboardSection by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("Breeding") }
    val home = { page = RabbitPage.DASHBOARD }
    when (page) {
        RabbitPage.DASHBOARD -> {
            RabbitDashboard(rabbitCount, error, { page = it }, onBack, dashboardSection, { dashboardSection = it }, showPedigree = pedigree != null, records = records, today = today, loading = loading)
        }

        RabbitPage.ANIMALS -> if (rabbits.isNotEmpty()) {
            RabbitAnimalsScreen(rabbits, rabbitCount, error, { profileId = it.animalId; page = RabbitPage.PROFILE }, home)
        } else {
            RabbitRows(
                "FOS-RABBIT-002",
                "Breeding animals",
                does,
                "No rabbits registered",
                error,
                home,
                note = rabbitCount?.takeIf { it > does.size }?.let { "Showing the first ${does.size} of $it rabbits by tag." },
            )
        }

        RabbitPage.PROFILE -> {
            val rabbit = rabbits.firstOrNull { it.animalId == profileId }?.takeIf { entryProfile == null || it.isDoe == entryProfile.isDoe }
            if (rabbit == null && entryProfile != null) {
                FarmOperationalPage(entryProfile.screenId, "Rabbit profile", "A selected rabbit on this farm.", onBack = onBack, backLabel = "Back") {
                    Text(if (loading) "Loading the selected rabbit" else error ?: "The selected rabbit is not available on this farm.")
                }
            } else if (rabbit == null) {
                LaunchedEffect(profileId) { page = RabbitPage.ANIMALS }
            } else {
                val profileBack: () -> Unit = { if (entryProfile != null) onBack() else page = RabbitPage.ANIMALS }
                RabbitProfileScreen(rabbit, rabbitExit, profileBack, rabbitAttachments,
                    backLabel = if (entryProfile != null) "Back" else "Breeding animals")
            }
        }

        RabbitPage.PEDIGREE -> pedigree?.let { RabbitPedigreeScreen(it, busy, home) }

        RabbitPage.COI -> pedigree?.let { RabbitCoiScreen(it, busy, home) }

        RabbitPage.MATE_COMPARE -> pedigree?.let { RabbitMateCompareScreen(it, busy, home) }

        RabbitPage.REGISTER -> {
            RabbitRegisterScreen(busy, error, onRegisterDoe, home)
        }

        RabbitPage.CAGES -> {
            RabbitCagesScreen(
                cages,
                nestBoxes,
                availableBoxes,
                busy,
                error,
                onCreateCage,
                onCreateNestBox,
                onSetNestStatus,
                home,
                nestBoxChoices,
            )
        }

        RabbitPage.WAVE -> {
            RabbitWaveScreen(waves, busy, error, onCreateWave, home)
        }

        RabbitPage.PALPATION -> {
            RabbitPalpationScreen(waveOptions, busy, error, onPalpate, home)
        }

        RabbitPage.KINDLING -> {
            RabbitKindlingScreen(waveOptions, busy, error, onKindle, home)
        }

        RabbitPage.FOSTER -> {
            RabbitFosterScreen(waveOptions, busy, error, onFoster, home)
        }

        RabbitPage.WEAN -> {
            RabbitWeanScreen(waveOptions, busy, error, onWean, home)
        }

        RabbitPage.OUTCOME -> {
            RabbitOutcomeScreen(waveOptions, busy, error, onRecordOutcome, home)
        }

        RabbitPage.NESTS -> {
            FarmOperationalPage("FOS-RABBIT-013", "Nest-box schedule", "", onBack = home) {
                RabbitNestCycle(records.cages.flatMap { it.nestBoxes })
                com.farmos.core.design.FarmActionButton(onClick = { page = RabbitPage.CAGES }, modifier = Modifier.fillMaxWidth()) { Text("Manage nest boxes") }
                RabbitCageRotationBoard(records, today)
                com.farmos.core.design.FarmActionButton(onClick = { page = RabbitPage.WAVE }, modifier = Modifier.fillMaxWidth()) { Text("Breeding wave") }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }

        RabbitPage.GI_STASIS -> {
            RabbitGiStasisScreen(searchRabbits, busy, error, onRecordGiStasis, home)
        }

        RabbitPage.WEIGHT -> {
            RabbitWeightScreen(searchRabbits, busy, error, onRecordWeight, home)
        }

        RabbitPage.CAGE_OCCUPANCY -> RabbitCageOccupancyScreen(records, home, today, { page = RabbitPage.CAGES })
        RabbitPage.CAGE_DETAIL -> RabbitCageDetailScreen(records, home)
        RabbitPage.KINDLING_DUE -> RabbitKindlingDueScreen(records, today, home)
        RabbitPage.LITTER_PROFILE -> RabbitLitterProfileScreen(records, home)
        RabbitPage.KIT_CENSUS -> RabbitKitCensusScreen(records, home)
        RabbitPage.REPLACEMENT_COMPARE -> RabbitReplacementCompareScreen(records, busy, error, onDecideRetention, home)
        RabbitPage.SALE_ALLOCATION -> RabbitSaleAllocationScreen(records, busy, error, onAllocateSale, home)
        RabbitPage.RABBIT_REPORT -> RabbitReportScreen(records, home)
    }
}

/** FOS-RABBIT-001 — illustrated rabbitry dashboard. */
@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun RabbitDashboard(
    rabbitCount: Int?,
    error: String?,
    onOpen: (RabbitPage) -> Unit,
    onBack: () -> Unit,
    section: String,
    onSection: (String) -> Unit,
    showPedigree: Boolean = false,
    records: RabbitRecords = RabbitRecords(),
    today: LocalDate = LocalDate.now(),
    loading: Boolean = false,
) {
    val actions = when (section) {
        "Animals" -> listOf(
            "Breeding animals" to RabbitPage.ANIMALS,
            "Register rabbit" to RabbitPage.REGISTER,
            "Record weight" to RabbitPage.WEIGHT,
            "GI-stasis red flag" to RabbitPage.GI_STASIS,
        )
        "Housing" -> listOf(
            "Cages & nest boxes" to RabbitPage.CAGES,
            "Nest-box schedule" to RabbitPage.NESTS,
            "Cage occupancy" to RabbitPage.CAGE_OCCUPANCY,
            "Cage detail" to RabbitPage.CAGE_DETAIL,
        )
        "Records" -> listOf(
            "Kit census" to RabbitPage.KIT_CENSUS,
            "Replacement compare" to RabbitPage.REPLACEMENT_COMPARE,
            "Sale allocation" to RabbitPage.SALE_ALLOCATION,
            "Rabbit report" to RabbitPage.RABBIT_REPORT,
        ) + if (showPedigree) listOf(
            "Rabbit pedigree" to RabbitPage.PEDIGREE,
            "Inbreeding check" to RabbitPage.COI,
            "Compare bucks" to RabbitPage.MATE_COMPARE,
        ) else emptyList()
        else -> listOf(
            "Breeding wave" to RabbitPage.WAVE,
            "Palpation" to RabbitPage.PALPATION,
            "Kindling" to RabbitPage.KINDLING,
            "Foster kits" to RabbitPage.FOSTER,
            "Weaning" to RabbitPage.WEAN,
            "Mating outcome" to RabbitPage.OUTCOME,
            "Kindling due" to RabbitPage.KINDLING_DUE,
            "Litter profiles" to RabbitPage.LITTER_PROFILE,
        )
    }
    AnimalFarmCanvas {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(com.farmos.core.design.AnimalFarmHomeMetrics.pageInset),
            verticalArrangement = Arrangement.spacedBy(FosDimens.IntraCardGap),
        ) {
            com.farmos.core.design.FarmBackButton("Farm home", onBack)
            AnimalFarmModuleHeader("Rabbitry", "", family = AnimalFarmFamily.RABBIT)
            if (loading) Text("Loading rabbitry…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else {
                Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RabbitMetric("Rabbits", rabbitCount, Modifier.weight(1f).fillMaxHeight())
                    RabbitMetric("Ready nests", records.cages.flatMap { it.nestBoxes }.count { it.status in setOf("available", "sanitized") }, Modifier.weight(1f).fillMaxHeight())
                }
                RabbitWorkflowOverview(records, today, { onOpen(RabbitPage.LITTER_PROFILE) }, { onOpen(RabbitPage.CAGE_OCCUPANCY) })
            }
            // Reveal navigation after the initial projection so the overview cannot
            // move an already active action under a user's tap.
            if (!loading) {
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Breeding", "Housing", "Animals", "Records").forEach { name ->
                        com.farmos.core.design.FarmFilterChip(name, name == section, { onSection(name) }, Modifier.testTag("rabbit-section:$name"))
                    }
                }
                actions.forEach { (label, target) ->
                    com.farmos.core.design.AnimalFarmQuickAction(label, { onOpen(target) }, Modifier.testTag("rabbit-action:${target.name}"))
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}


@Composable
private fun RabbitMetric(
    label: String,
    value: Int?,
    modifier: Modifier,
) {
    FarmIllustratedSectionSurface(modifier) {
        Text(value?.toString() ?: "—", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun RabbitRows(
    screenId: String,
    title: String,
    rows: List<String>,
    empty: String,
    error: String?,
    onBack: () -> Unit,
    note: String? = null,
) {
    FarmOperationalPage(screenId, title, "Rabbitry records on this device.", onBack = onBack) {
        note?.let { Text(it, modifier = Modifier.testTag("rabbit-list-bounded")) }
        FarmOperationalRows(rows, empty, "Add a record from the rabbitry dashboard.")
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun RabbitRegisterScreen(
    busy: Boolean,
    error: String?,
    onRegister: (String, String?, String) -> Unit,
    onBack: () -> Unit,
) {
    var tag by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var sex by remember { mutableStateOf("FEMALE") }
    FarmOperationalPage("FOS-RABBIT-005", "Register rabbit", "Register a breeding doe or buck.", onBack = onBack) {
        FarmOperationalSection("Identity") {
            OutlinedTextField(
                tag,
                { tag = it },
                label = { Text("Tag") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                singleLine = true,
            )
            OutlinedTextField(name, {
                name = it
            }, label = { Text("Name (optional)") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { sex = "FEMALE" }, enabled = !busy) { Text(if (sex == "FEMALE") "Doe · selected" else "Doe") }
                TextButton(onClick = { sex = "MALE" }, enabled = !busy) { Text(if (sex == "MALE") "Buck · selected" else "Buck") }
            }
            Button(onClick = {
                onRegister(
                    tag,
                    name.trim().ifBlank {
                        null
                    },
                    sex,
                )
            }, enabled = !busy && tag.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Register rabbit") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

/** FOS-RABBIT-014 — nest placement: add a nest box and assign it through the nest-box cycle (root tag FOS-RABBIT-006). */
/** FOS-RABBIT-015 — nest removal: cycle a box in-cage → dirty → sanitized/available (root tag FOS-RABBIT-006). */
@Composable
private fun RabbitCagesScreen(
    cages: List<String>,
    nestBoxes: List<String>,
    availableBoxes: Long,
    busy: Boolean,
    error: String?,
    onCreateCage: (String) -> Unit,
    onCreateNestBox: (String, String) -> Unit,
    onSetNestStatus: (String, String) -> Unit,
    onBack: () -> Unit,
    nestBoxChoices: List<RabbitNestBoxChoice>,
) {
    var cageCode by remember { mutableStateOf("") }
    var boxCode by remember { mutableStateOf("") }
    var boxId by remember { mutableStateOf("") }
    var boxStatus by remember { mutableStateOf("") }
    var housingSection by rememberSaveable { mutableStateOf("Cages") }
    FarmOperationalPage("FOS-RABBIT-006", "Cages & nest boxes", "", FarmVisualClass.I2, onBack) {
        FarmSectionTabs(listOf("Cages", "Nest boxes", "Nest-box cycle"), housingSection, { housingSection = it })
        if (housingSection == "Cages") FarmOperationalRows(cages, "No cages yet", "Create a cage before starting a breeding wave.")
        if (housingSection != "Cages") FarmOperationalSection(
            "Nest capacity",
        ) { Text("$availableBoxes available nest box(es)", style = MaterialTheme.typography.titleLarge) }
        if (housingSection == "Cages") FarmOperationalSection("Create cage") {
            OutlinedTextField(cageCode, {
                cageCode = it
            }, label = { Text("Cage code") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            Button(onClick = {
                onCreateCage(cageCode)
            }, enabled = !busy && cageCode.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Create cage") }
        }
        if (housingSection == "Nest boxes") FarmOperationalSection("Add nest box") {
            OutlinedTextField(cageCode, {
                cageCode = it
            }, label = { Text("Cage code") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            OutlinedTextField(boxCode, {
                boxCode = it
            }, label = { Text("Nest box code") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            Button(onClick = {
                onCreateNestBox(cageCode, boxCode)
            }, enabled = !busy && cageCode.isNotBlank() && boxCode.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Add nest box",
                )
            }
        }
        if (housingSection == "Nest boxes") FarmOperationalRows(nestBoxes, "No nest boxes yet", null)
        if (housingSection == "Nest-box cycle") {
        RabbitNestCycle(nestBoxChoices.map { RabbitNestBoxView(it.id, it.code, it.status) })
        FarmOperationalSection("Update nest box") {
            FarmEntitySelector(
                atomTag = "rabbit-nest-box-selector",
                title = "Nest box",
                options = nestBoxChoices.map { FarmSelectorOption(it.id, it.code, nestStatusLabel(it.status)) },
                selectedId = boxId.ifBlank { null },
                onSelect = {
                    boxId = it
                    boxStatus = ""
                },
                emptyText = "Add a nest box first.",
                enabled = !busy,
            )
            val current = nestBoxChoices.firstOrNull { it.id == boxId }?.status
            if (current != null) {
                FarmEntitySelector(
                    atomTag = "rabbit-nest-status-selector",
                    title = "Next status",
                    options = RabbitNestBoxCycle.nextStatuses(current).map { FarmSelectorOption(it, nestStatusLabel(it)) },
                    selectedId = boxStatus.ifBlank { null },
                    onSelect = { boxStatus = it },
                    emptyText = "This box has no allowed next status.",
                    enabled = !busy,
                )
            }
            Button(onClick = {
                onSetNestStatus(boxId, boxStatus)
            }, enabled = !busy && boxId.isNotBlank() && boxStatus.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Set nest status") }
        }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

/** FOS-RABBIT-010 — mating: breeding waves are created with a mating date and doe count (root tag FOS-RABBIT-009). */
@Composable
private fun RabbitWaveScreen(
    waves: List<String>,
    busy: Boolean,
    error: String?,
    onCreate: (String, Int, String) -> Unit,
    onBack: () -> Unit,
) {
    var cage by remember { mutableStateOf("") }
    var does by remember { mutableStateOf("11") }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    FarmOperationalPage(
        "FOS-RABBIT-009",
        "Breeding wave",
        "Semi-intensive timing generates nest, kindling, rebreed and weaning work.",
        FarmVisualClass.I2,
        onBack,
    ) {
        FarmOperationalRows(waves, "No breeding waves yet", null)
        FarmOperationalSection(
            "Create wave",
            "Nest +${KudbatSemiIntensiveExcel.NEST_IN_DAYS_AFTER_MATING} d · kindling +${KudbatSemiIntensiveExcel.KINDLING_DAYS_AFTER_MATING} d · rebreed +${KudbatSemiIntensiveExcel.REBREED_DAYS_AFTER_MATING} d",
        ) {
            OutlinedTextField(cage, {
                cage = it
            }, label = { Text("Cage code") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            OutlinedTextField(does, {
                does = it
            }, label = { Text("Doe count") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            OutlinedTextField(day, {
                day = it
            }, label = { Text("Mating date") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            Button(
                onClick = { onCreate(cage, does.toIntOrNull() ?: 0, day) },
                enabled =
                    !busy && cage.isNotBlank() && runCatching { LocalDate.parse(day) }.isSuccess,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Create breeding wave") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun RabbitPalpationScreen(
    waveOptions: List<FarmSelectorOption>,
    busy: Boolean,
    error: String?,
    onRecord: (String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var wave by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("pregnant") }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    RabbitEventPage(
        "FOS-RABBIT-011",
        "Palpation",
        "Record pregnant or open; this is a farm record, not a diagnosis.",
        busy,
        error,
        onBack,
    ) {
        RabbitWaveSelector(waveOptions, "Breeding wave", wave, busy) { wave = it }
        OutlinedTextField(result, {
            result = it
        }, label = { Text("Result") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        OutlinedTextField(
            day,
            { day = it },
            label = { Text("Date") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            singleLine = true,
        )
        Button(onClick = {
            onRecord(wave, result, day)
        }, enabled = !busy && wave.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Record palpation") }
    }
}

/** FOS-RABBIT-021 — kit mortality: kindling records live and dead kit counts (root tag FOS-RABBIT-017). */
@Composable
private fun RabbitKindlingScreen(
    waveOptions: List<FarmSelectorOption>,
    busy: Boolean,
    error: String?,
    onRecord: (String, String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var wave by remember { mutableStateOf("") }
    var live by remember { mutableStateOf("") }
    var dead by remember { mutableStateOf("0") }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    RabbitEventPage(
        "FOS-RABBIT-017",
        "Record kindling",
        "Record live and dead kit counts clearly.",
        busy,
        error,
        onBack,
        FarmVisualClass.I4,
    ) {
        RabbitWaveSelector(waveOptions, "Breeding wave", wave, busy) { wave = it }
        OutlinedTextField(live, {
            live = it
        }, label = { Text("Live kits") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        OutlinedTextField(dead, {
            dead = it
        }, label = { Text("Dead kits") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        OutlinedTextField(day, {
            day = it
        }, label = { Text("Kindling date") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        Button(onClick = {
            onRecord(wave, live, dead, day)
        }, enabled = !busy && wave.isNotBlank() && live.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Record kindling") }
    }
}

@Composable
private fun RabbitFosterScreen(
    waveOptions: List<FarmSelectorOption>,
    busy: Boolean,
    error: String?,
    onRecord: (String, String, String, String, Boolean) -> Unit,
    onBack: () -> Unit,
) {
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    var kits by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    var ack by remember { mutableStateOf(false) }
    RabbitEventPage(
        "FOS-RABBIT-020",
        "Foster kits",
        "Fostering after the governed window requires explicit acknowledgement.",
        busy,
        error,
        onBack,
    ) {
        RabbitWaveSelector(waveOptions, "From wave", from, busy, key = "from") { from = it }
        RabbitWaveSelector(waveOptions, "To wave", to, busy, key = "to") { to = it }
        OutlinedTextField(kits, {
            kits = it
        }, label = { Text("Kit count") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        OutlinedTextField(day, {
            day = it
        }, label = { Text("Foster date") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        TextButton(onClick = {
            ack = !ack
        }, enabled = !busy) { Text(if (ack) "Outside-window acknowledgement recorded" else "Acknowledge outside 3-day window") }
        Button(
            onClick = { onRecord(from, to, kits, day, ack) },
            enabled =
                !busy && from.isNotBlank() && to.isNotBlank() && kits.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Record foster") }
    }
}

@Composable
private fun RabbitWeanScreen(
    waveOptions: List<FarmSelectorOption>,
    busy: Boolean,
    error: String?,
    onRecord: (String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var wave by remember { mutableStateOf("") }
    var count by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    RabbitEventPage("FOS-RABBIT-022", "Weaning", "Record kits weaned from a breeding wave.", busy, error, onBack) {
        RabbitWaveSelector(waveOptions, "Breeding wave", wave, busy) { wave = it }
        OutlinedTextField(count, {
            count = it
        }, label = { Text("Weaned kits") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        OutlinedTextField(day, {
            day = it
        }, label = { Text("Weaning date") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        Button(onClick = {
            onRecord(wave, count, day)
        }, enabled = !busy && wave.isNotBlank() && count.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Record weaning") }
    }
}

@Composable
private fun RabbitOutcomeScreen(
    waveOptions: List<FarmSelectorOption>,
    busy: Boolean,
    error: String?,
    onRecord: (String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var wave by remember { mutableStateOf("") }
    var outcome by remember { mutableStateOf("false_pregnancy") }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    RabbitEventPage("FOS-RABBIT-012", "Pregnancy status", "Record the breeding-wave outcome.", busy, error, onBack) {
        RabbitWaveSelector(waveOptions, "Breeding wave", wave, busy) { wave = it }
        OutlinedTextField(outcome, {
            outcome = it
        }, label = { Text("Outcome") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        OutlinedTextField(day, {
            day = it
        }, label = { Text("Outcome date") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        Button(onClick = {
            onRecord(wave, outcome, day)
        }, enabled = !busy && wave.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Record outcome") }
    }
}

/** FOS-RABBIT-033 — rabbit health: records signs observed on a rabbit and creates vet-call work (root tag FOS-RABBIT-034). */
@Composable
private fun RabbitGiStasisScreen(
    searchRabbits: FarmSelectorSearch,
    busy: Boolean,
    error: String?,
    onRecord: (String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var animal by remember { mutableStateOf<FarmSelectorOption?>(null) }
    var signs by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    RabbitEventPage(
        "FOS-RABBIT-034",
        "GI-stasis red flag",
        "Record signs and create vet-call work. Farm OS does not diagnose GI stasis.",
        busy,
        error,
        onBack,
        FarmVisualClass.I4,
    ) {
        FarmSearchSelector(
            atomTag = FarmSelectionAtoms.ANIMAL_SELECTOR,
            title = "Rabbit",
            search = searchRabbits,
            selected = animal,
            onSelect = { animal = it },
            emptyText = "No rabbits match on this device",
            enabled = !busy,
        )
        OutlinedTextField(signs, { signs = it }, label = { Text("Signs observed") }, modifier = Modifier.fillMaxWidth(), enabled = !busy)
        OutlinedTextField(
            day,
            { day = it },
            label = { Text("Flag date") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            singleLine = true,
        )
        Button(onClick = {
            onRecord(animal?.id.orEmpty(), signs, day)
        }, enabled = !busy && animal != null && signs.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text("Flag and create vet-call task")
        }
    }
}

/** FOS-RABBIT-032 — governed rabbit weighing: positive, sane and never in the future. */
@Composable
private fun RabbitWeightScreen(
    searchRabbits: FarmSelectorSearch,
    busy: Boolean,
    error: String?,
    onRecord: (String, String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var animal by remember { mutableStateOf<FarmSelectorOption?>(null) }
    var weightKg by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    var notes by remember { mutableStateOf("") }
    RabbitEventPage(
        "FOS-RABBIT-032",
        "Record weight",
        "Weigh a rabbit. The record is governed: the rabbit must be registered, the weight positive and sane, the weighing date never in the future.",
        busy,
        error,
        onBack,
    ) {
        FarmSearchSelector(
            atomTag = FarmSelectionAtoms.ANIMAL_SELECTOR,
            title = "Rabbit",
            search = searchRabbits,
            selected = animal,
            onSelect = { animal = it },
            emptyText = "No rabbits match on this device",
            enabled = !busy,
        )
        OutlinedTextField(
            weightKg,
            { weightKg = it },
            label = { Text("Weight (kg)") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            singleLine = true,
        )
        OutlinedTextField(
            day,
            { day = it },
            label = { Text("Weighing date") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            singleLine = true,
        )
        OutlinedTextField(notes, { notes = it }, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth(), enabled = !busy)
        Button(onClick = {
            onRecord(animal?.id.orEmpty(), weightKg, day, notes)
        }, enabled = !busy && animal != null && weightKg.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text("Record weight")
        }
    }
}

@Composable
private fun RabbitEventPage(
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

/** Breeding-wave selector over this farm's waves, so a wave event never carries a mistyped id. */
@Composable
private fun RabbitWaveSelector(
    waveOptions: List<FarmSelectorOption>,
    title: String,
    selected: String,
    busy: Boolean,
    key: String = "wave",
    onSelect: (String) -> Unit,
) {
    FarmEntitySelector(
        atomTag = "rabbit-wave-selector:$key",
        title = title,
        options = waveOptions,
        selectedId = selected.ifBlank { null },
        onSelect = onSelect,
        emptyText = "Create a breeding wave first.",
        enabled = !busy,
    )
}

/** One nest box as the cycle selector sees it: its id, code and current status. */
data class RabbitNestBoxChoice(val id: String, val code: String, val status: String)

private fun nestStatusLabel(status: String): String = status.replace('_', ' ').replaceFirstChar { it.uppercase() }

/**
 * FOS-RABBIT-025 — Replacement Candidate Compare: compare young rabbits side by side
 * for replacement selection, then record keep/cull retention decisions via the
 * governed DecideRabbitRetention command.
 */
@Composable
private fun RabbitReplacementCompareScreen(
    records: RabbitRecords,
    busy: Boolean,
    error: String?,
    onDecideRetention: (kitId: String, decision: String, day: String) -> Unit,
    onBack: () -> Unit,
) {
    val candidates = records.kits.filter { it.status == "active" && it.retention != "culled" }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    val compared = candidates.filter { it.id in selected }.take(4)
    FarmOperationalPage("FOS-RABBIT-025", "Replacement compare", "Compare young rabbits for replacement selection.", onBack = onBack) {
        FarmOperationalSection("Candidates") {
            if (candidates.isEmpty()) Text("No active kits to compare.")
            candidates.forEach { kit ->
                val checked = kit.id in selected
                TextButton(
                    onClick = {
                        selected = if (checked) selected - kit.id else (selected + kit.id).take(4).toSet()
                    },
                    enabled = !busy,
                ) { Text("${if (checked) "[x]" else "[ ]"} ${kit.label} · ${kit.sex} · ${kit.status} · retention ${kit.retention}") }
            }
        }
        if (compared.isNotEmpty()) {
            FarmOperationalSection("Side by side") {
                compared.forEach { kit ->
                    Text("${kit.label}: sex ${kit.sex}, status ${kit.status}, retention ${kit.retention}, ear tag ${kit.earTag ?: "—"}")
                }
                OutlinedTextField(day, {
                    day = it
                }, label = { Text("Decision date") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { compared.forEach { onDecideRetention(it.id, "keep", day) } },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    ) { Text("Keep selected") }
                    Button(
                        onClick = { compared.forEach { onDecideRetention(it.id, "cull", day) } },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    ) { Text("Cull selected") }
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

/**
 * FOS-RABBIT-030 — Sale Allocation: allocate rabbits to a planned sale via the
 * governed RecordRabbitMarketPlan command (purpose "sale").
 */
@Composable
private fun RabbitSaleAllocationScreen(
    records: RabbitRecords,
    busy: Boolean,
    error: String?,
    onAllocate: (kitId: String, targetWeightGrams: String, targetDay: String, purpose: String) -> Unit,
    onBack: () -> Unit,
) {
    val candidates = records.kits.filter { it.status == "active" }
    var selected by remember { mutableStateOf("") }
    var targetWeight by remember { mutableStateOf("") }
    var targetDay by remember { mutableStateOf(LocalDate.now().toString()) }
    FarmOperationalPage("FOS-RABBIT-030", "Sale allocation", "Allocate rabbits to a planned sale.", onBack = onBack) {
        FarmOperationalSection("Allocate") {
            FarmEntitySelector(
                atomTag = "rabbit-sale-kit-selector",
                title = "Rabbit",
                options = candidates.map { FarmSelectorOption(it.id, it.label, "${it.sex} · ${it.status}") },
                selectedId = selected.ifBlank { null },
                onSelect = { selected = it },
                emptyText = "No active kits to allocate.",
                enabled = !busy,
            )
            OutlinedTextField(targetWeight, {
                targetWeight = it
            }, label = { Text("Target weight (g)") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            OutlinedTextField(targetDay, {
                targetDay = it
            }, label = { Text("Target sale date") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            Button(
                onClick = { onAllocate(selected, targetWeight, targetDay, "sale") },
                enabled = !busy && selected.isNotBlank() && targetWeight.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Allocate to sale") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

/**
 * FOS-RABBIT-036 — Rabbit Report: farm-scoped rabbitry totals computed from local
 * records only.
 *
 * FOS-RABBIT-032 — Rabbit Weight is implemented: the `rabbit_weights` table, the
 * RecordRabbitWeight command with validator, the repository handler journaling
 * `rabbit.record_weight.v1`, and the replay applier all exist; the latest weighing per
 * rabbit is shown below from the same read model.
 */
@Composable
private fun RabbitReportScreen(
    records: RabbitRecords,
    onBack: () -> Unit,
) {
    val activeKits = records.kits.count { it.status == "active" }
    FarmOperationalPage("FOS-RABBIT-036", "Rabbit report", "Rabbitry totals for this farm.", onBack = onBack) {
        FarmMetricStrip(listOf(
            FarmMetricValue("Cages", records.cages.size.toString()),
            FarmMetricValue("Breeding waves", records.waves.size.toString()),
            FarmMetricValue("Active kits", activeKits.toString()),
        ))
        FarmMetricBars(
            "Kits by wave",
            records.waves.map { wave ->
                val count = records.kits.count { it.waveId == wave.id }
                FarmBarValue("Cage ${wave.cageLabel} · ${LocalDate.ofEpochDay(wave.matingEpochDay)}", count.toDouble(), count.toString())
            },
            unit = "Individual kits recorded",
        )
        FarmOperationalSection("Totals") {
            Text("Cages: ${records.cages.size}")
            Text("Waves: ${records.waves.size}")
            Text("Kits: ${records.kits.size} (${activeKits} active)")
        }
        FarmOperationalSection("Per wave") {
            if (records.waves.isEmpty()) Text("No waves recorded yet.")
            records.waves.forEach { wave ->
                val kits = records.kits.count { it.waveId == wave.id }
                Text("Wave ${wave.id}: $kits kits")
            }
        }
        FarmOperationalSection("Latest weights") {
            val latest = records.weights.distinctBy { it.animalId }
            if (latest.isEmpty()) Text("No weights recorded yet.")
            latest.forEach { weight ->
                Text(
                    "${weight.animalLabel}: ${weight.weightKg} kg · ${LocalDate.ofEpochDay(weight.weighedAtEpochMillis / 86_400_000L)}" +
                        (weight.notes?.let { " · $it" } ?: ""),
                    modifier = Modifier.testTag("rabbit-weight:${weight.id}"),
                )
            }
        }
    }
}
