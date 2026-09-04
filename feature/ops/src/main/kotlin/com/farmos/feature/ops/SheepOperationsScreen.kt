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
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.design.FarmVisualClass
import java.time.LocalDate

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
    val onIdentifier: (animalId: String, type: String, value: String, day: String) -> Unit,
    val onMovement: (animalId: String, direction: String, from: String, to: String, day: String) -> Unit,
    val onPedigree: (animalId: String, parentId: String, relation: String) -> Unit,
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
    IDENTIFIER,
    MOVEMENT,
    PEDIGREE,
}

@Composable
fun SheepOperationsScreen(
    selectedAnimalId: String?,
    busy: Boolean,
    error: String?,
    actions: SheepOperationsActions,
    onBack: () -> Unit,
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

        SheepOpsPage.PEDIGREE -> {
            SheepPedigreeScreen(selectedAnimalId, busy, error, actions.onPedigree, home)
        }
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
            SheepNav("Record lambing") { onOpen(SheepOpsPage.LAMBING) }
            SheepNav("Lamb marking") { onOpen(SheepOpsPage.MARKING) }
            SheepNav("Weaning") { onOpen(SheepOpsPage.WEANING) }
        }
        FarmOperationalSection("Wool") {
            SheepNav("Fleece record") { onOpen(SheepOpsPage.WOOL) }
            SheepNav("Shearing") { onOpen(SheepOpsPage.SHEARING) }
            SheepNav("Micron / fibre result") { onOpen(SheepOpsPage.MICRON) }
        }
        FarmOperationalSection("Field health") {
            SheepNav("FAMACHA") { onOpen(SheepOpsPage.FAMACHA) }
            SheepNav("Dag score") { onOpen(SheepOpsPage.DAG) }
            SheepNav("Footrot") { onOpen(SheepOpsPage.FOOTROT) }
            SheepNav("Flystrike") { onOpen(SheepOpsPage.FLYSTRIKE) }
        }
        FarmOperationalSection("Identity & traceability") {
            SheepNav("Official identifier") { onOpen(SheepOpsPage.IDENTIFIER) }
            SheepNav("Movement") { onOpen(SheepOpsPage.MOVEMENT) }
            SheepNav("Pedigree link") { onOpen(SheepOpsPage.PEDIGREE) }
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
        Field(groupId, { groupId = it }, "Sheep mob id", busy)
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
        Field(animalId, { animalId = it }, "Ewe id", busy)
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
        Field(animalId, { animalId = it }, "Ewe id", busy)
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
        Field(animalId, { animalId = it }, "Animal id (optional)", busy)
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
        Field(animalId, { animalId = it }, "Sheep id", busy)
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
        Field(animalId, { animalId = it }, "Sheep id", busy)
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
        Field(animalId, { animalId = it }, "Sheep id", busy)
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
        "Record parentage only. This does not calculate COI or genetic merit.",
        busy,
        error,
        onBack,
    ) {
        Field(animalId, { animalId = it }, "Sheep id", busy)
        Field(parentId, { parentId = it }, "Parent id", busy)
        Field(relation, {
            relation =
                it
        }, "Relation", busy)
        Button(onClick = {
            onRecord(animalId, parentId, relation)
        }, enabled = !busy && animalId.isNotBlank() && parentId.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Link parent") }
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
