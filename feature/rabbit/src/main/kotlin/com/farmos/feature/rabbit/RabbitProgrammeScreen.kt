package com.farmos.feature.rabbit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farmos.core.design.FarmIllustratedSectionSurface
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalRows
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.design.FarmPastoralBackdrop
import com.farmos.core.design.FarmSpeciesVisual
import com.farmos.core.design.FarmStorySurface
import com.farmos.core.design.FarmVisualClass
import com.farmos.core.design.FosDimens
import com.farmos.domain.rabbit.KudbatSemiIntensiveExcel
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
) {
    var page by remember { mutableStateOf(RabbitPage.DASHBOARD) }
    val home = { page = RabbitPage.DASHBOARD }
    when (page) {
        RabbitPage.DASHBOARD -> {
            RabbitDashboard(does, cages, waves, availableBoxes, error, { page = it }, onBack)
        }

        RabbitPage.ANIMALS -> {
            RabbitRows("FOS-RABBIT-002", "Breeding animals", does, "No rabbits registered", error, home)
        }

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
            )
        }

        RabbitPage.WAVE -> {
            RabbitWaveScreen(waves, busy, error, onCreateWave, home)
        }

        RabbitPage.PALPATION -> {
            RabbitPalpationScreen(busy, error, onPalpate, home)
        }

        RabbitPage.KINDLING -> {
            RabbitKindlingScreen(busy, error, onKindle, home)
        }

        RabbitPage.FOSTER -> {
            RabbitFosterScreen(busy, error, onFoster, home)
        }

        RabbitPage.WEAN -> {
            RabbitWeanScreen(busy, error, onWean, home)
        }

        RabbitPage.OUTCOME -> {
            RabbitOutcomeScreen(busy, error, onRecordOutcome, home)
        }

        RabbitPage.NESTS -> {
            RabbitRows("FOS-RABBIT-013", "Nest-box schedule", nestBoxes, "No nest boxes on this device", error, home)
        }

        RabbitPage.GI_STASIS -> {
            RabbitGiStasisScreen(busy, error, onRecordGiStasis, home)
        }
    }
}

/** FOS-RABBIT-001 — illustrated rabbitry dashboard. */
@Composable
private fun RabbitDashboard(
    animals: List<String>,
    cages: List<String>,
    waves: List<String>,
    availableBoxes: Long,
    error: String?,
    onOpen: (RabbitPage) -> Unit,
    onBack: () -> Unit,
) {
    FarmPastoralBackdrop(Modifier.fillMaxSize(), heroSpecies = FarmSpeciesVisual.RABBIT) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(FosDimens.SectionGap),
        ) {
            FarmStorySurface(Modifier.fillMaxWidth()) {
                Text("Rabbitry", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Breeding waves. Nest boxes. Litters. Market-ready rabbits.", color = MaterialTheme.colorScheme.primary)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RabbitMetric("Rabbits", animals.size, Modifier.weight(1f))
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
            RabbitDashboardAction("GI-stasis red flag", "Flag signs and create vet-call work", { onOpen(RabbitPage.GI_STASIS) })
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = onBack) { Text("Back to farm home") }
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
        TextButton(onClick = onClick) { Text("Open") }
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
) {
    FarmOperationalPage(screenId, title, "Rabbitry records on this device.", onBack = onBack) {
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
) {
    var cageCode by remember { mutableStateOf("") }
    var boxCode by remember { mutableStateOf("") }
    var boxId by remember { mutableStateOf("") }
    var boxStatus by remember { mutableStateOf("in_cage") }
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
            OutlinedTextField(boxId, {
                boxId = it
            }, label = { Text("Nest box id") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            OutlinedTextField(boxStatus, {
                boxStatus = it
            }, label = { Text("Next status") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            Button(onClick = {
                onSetNestStatus(boxId, boxStatus)
            }, enabled = !busy && boxId.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Set nest status") }
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
        OutlinedTextField(
            wave,
            { wave = it },
            label = { Text("Wave id") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            singleLine = true,
        )
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
        OutlinedTextField(
            wave,
            { wave = it },
            label = { Text("Wave id") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            singleLine = true,
        )
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
        OutlinedTextField(from, {
            from = it
        }, label = { Text("From wave") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        OutlinedTextField(
            to,
            { to = it },
            label = { Text("To wave") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            singleLine = true,
        )
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
    busy: Boolean,
    error: String?,
    onRecord: (String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var wave by remember { mutableStateOf("") }
    var count by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    RabbitEventPage("FOS-RABBIT-022", "Weaning", "Record kits weaned from a breeding wave.", busy, error, onBack) {
        OutlinedTextField(
            wave,
            { wave = it },
            label = { Text("Wave id") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            singleLine = true,
        )
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
    busy: Boolean,
    error: String?,
    onRecord: (String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var wave by remember { mutableStateOf("") }
    var outcome by remember { mutableStateOf("false_pregnancy") }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    RabbitEventPage("FOS-RABBIT-012", "Pregnancy status", "Record the breeding-wave outcome.", busy, error, onBack) {
        OutlinedTextField(
            wave,
            { wave = it },
            label = { Text("Wave id") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            singleLine = true,
        )
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
    busy: Boolean,
    error: String?,
    onRecord: (String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var animal by remember { mutableStateOf("") }
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
        OutlinedTextField(animal, {
            animal = it
        }, label = { Text("Rabbit id") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
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
            onRecord(animal, signs, day)
        }, enabled = !busy && animal.isNotBlank() && signs.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
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
