package com.farmos.feature.rabbit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
    CAGE_OCCUPANCY,
    CAGE_DETAIL,
    KINDLING_DUE,
    LITTER_PROFILE,
    KIT_CENSUS,
    PROFILE,
    PEDIGREE,
    COI,
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
    /** Rabbit pedigree and the kits' inbreeding (D-023); null hides both. */
    pedigree: RabbitPedigreePorts? = null,
) {
    var page by remember { mutableStateOf(RabbitPage.DASHBOARD) }
    var profileId by remember { mutableStateOf<String?>(null) }
    val home = { page = RabbitPage.DASHBOARD }
    when (page) {
        RabbitPage.DASHBOARD -> {
            RabbitDashboard(does, rabbitCount, cages, waves, availableBoxes, error, { page = it }, onBack, showPedigree = pedigree != null)
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
            val rabbit = rabbits.firstOrNull { it.animalId == profileId }
            if (rabbit == null) {
                LaunchedEffect(profileId) { page = RabbitPage.ANIMALS }
            } else {
                RabbitProfileScreen(rabbit, rabbitExit) { page = RabbitPage.ANIMALS }
            }
        }

        RabbitPage.PEDIGREE -> pedigree?.let { RabbitPedigreeScreen(it, busy, home) }

        RabbitPage.COI -> pedigree?.let { RabbitCoiScreen(it, busy, home) }

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
            RabbitRows("FOS-RABBIT-013", "Nest-box schedule", nestBoxes, "No nest boxes on this device", error, home)
        }

        RabbitPage.GI_STASIS -> {
            RabbitGiStasisScreen(searchRabbits, busy, error, onRecordGiStasis, home)
        }

        RabbitPage.CAGE_OCCUPANCY -> RabbitCageOccupancyScreen(records, home)
        RabbitPage.CAGE_DETAIL -> RabbitCageDetailScreen(records, home)
        RabbitPage.KINDLING_DUE -> RabbitKindlingDueScreen(records, today, home)
        RabbitPage.LITTER_PROFILE -> RabbitLitterProfileScreen(records, home)
        RabbitPage.KIT_CENSUS -> RabbitKitCensusScreen(records, home)
    }
}

/** FOS-RABBIT-001 — illustrated rabbitry dashboard. */
@Composable
private fun RabbitDashboard(
    animals: List<String>,
    rabbitCount: Int?,
    cages: List<String>,
    waves: List<String>,
    availableBoxes: Long,
    error: String?,
    onOpen: (RabbitPage) -> Unit,
    onBack: () -> Unit,
    showPedigree: Boolean = false,
) {
    AnimalFarmCanvas {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(FosDimens.SectionGap),
        ) {
            AnimalFarmModuleHeader(
                title = "Rabbitry",
                subtitle = "Waves, cages, nests and litters",
                family = AnimalFarmFamily.RABBIT,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RabbitMetric("Rabbits", rabbitCount ?: animals.size, Modifier.weight(1f))
                RabbitMetric("Cages", cages.size, Modifier.weight(1f))
                RabbitMetric("Waves", waves.size, Modifier.weight(1f))
                RabbitMetric("Boxes", availableBoxes.toInt(), Modifier.weight(1f))
            }
            RabbitDashboardAction("Breeding animals", "Does and bucks", { onOpen(RabbitPage.ANIMALS) })
            RabbitDashboardAction("Register rabbit", "Add a doe or buck", { onOpen(RabbitPage.REGISTER) })
            RabbitDashboardAction("Cages & nest boxes", "Housing, capacity and nest cycle", { onOpen(RabbitPage.CAGES) })
            RabbitDashboardAction("Breeding wave", "Mating schedule and generated due work", { onOpen(RabbitPage.WAVE) })
            RabbitDashboardAction("Palpation", "Record pregnant or open", { onOpen(RabbitPage.PALPATION) })
            RabbitDashboardAction("Kindling", "Record live and dead kits", { onOpen(RabbitPage.KINDLING) })
            RabbitDashboardAction("Foster kits", "Move kits between waves with timing acknowledgement", { onOpen(RabbitPage.FOSTER) })
            RabbitDashboardAction("Weaning", "Record kits leaving the litter", { onOpen(RabbitPage.WEAN) })
            RabbitDashboardAction("Mating outcome", "Record false pregnancy or outcome", { onOpen(RabbitPage.OUTCOME) })
            RabbitDashboardAction("Nest-box schedule", "Placement, occupancy and removal windows", { onOpen(RabbitPage.NESTS) })
            RabbitDashboardAction("GI-stasis red flag", "Flag signs and create vet-call work", { onOpen(RabbitPage.GI_STASIS) })
            RabbitDashboardAction("Cage occupancy", "Recorded capacity, nest boxes and waves per cage", { onOpen(RabbitPage.CAGE_OCCUPANCY) })
            RabbitDashboardAction("Cage detail", "Nest boxes and waves for one cage", { onOpen(RabbitPage.CAGE_DETAIL) })
            RabbitDashboardAction("Kindling due", "Waves with no kindling recorded yet", { onOpen(RabbitPage.KINDLING_DUE) })
            RabbitDashboardAction("Litter profiles", "Schedule, events and kits per wave", { onOpen(RabbitPage.LITTER_PROFILE) })
            RabbitDashboardAction("Kit census", "Individual kits by wave", { onOpen(RabbitPage.KIT_CENSUS) })
            if (showPedigree) {
                RabbitDashboardAction("Rabbit pedigree", "Sire and dam of each rabbit", { onOpen(RabbitPage.PEDIGREE) })
                RabbitDashboardAction("Inbreeding check", "Kits' inbreeding for a doe and buck", { onOpen(RabbitPage.COI) })
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = onBack) { Text("Farm home") }
        }
    }
}

@Composable
private fun RabbitMetric(
    label: String,
    value: Int,
    modifier: Modifier,
) {
    FarmIllustratedSectionSurface(modifier) {
        Text(value.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun RabbitDashboardAction(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    FarmIllustratedSectionSurface(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onClick) { Text("Open $title") }
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
    FarmOperationalPage("FOS-RABBIT-006", "Cages & nest boxes", "Rabbit housing and nest-box availability.", FarmVisualClass.I2, onBack) {
        FarmOperationalRows(cages, "No cages yet", "Create a cage before starting a breeding wave.")
        FarmOperationalSection(
            "Nest capacity",
        ) { Text("$availableBoxes available nest box(es)", style = MaterialTheme.typography.titleLarge) }
        FarmOperationalSection("Create cage") {
            OutlinedTextField(cageCode, {
                cageCode = it
            }, label = { Text("Cage code") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            Button(onClick = {
                onCreateCage(cageCode)
            }, enabled = !busy && cageCode.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Create cage") }
        }
        FarmOperationalSection("Add nest box") {
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
        FarmOperationalRows(nestBoxes, "No nest boxes yet", null)
        FarmOperationalSection("Nest-box cycle", "Available/sanitized → assigned → in cage → dirty → sanitize.") {
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
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

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
