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

data class CattleOperationsActions(
    val onService: (animalId: String, method: String, day: String) -> Unit,
    val onPd: (animalId: String, result: String, day: String) -> Unit,
    val onCalving: (animalId: String, born: String, live: String, dead: String, day: String) -> Unit,
    val onBcs: (animalId: String, scale: String, scoreTenths: String, day: String) -> Unit,
    val onMilk: (animalId: String, litres: String, day: String) -> Unit,
    val onLocomotion: (animalId: String, score: String, day: String) -> Unit,
    val onScc: (animalId: String, cells: String, dim: String, day: String) -> Unit,
    val onDryOff: (animalId: String, day: String, expectedCalving: String) -> Unit,
    val onWeaning: (animalId: String, weightGrams: String, day: String) -> Unit,
    val onIdentifier: (animalId: String, type: String, value: String, day: String) -> Unit,
    val onMovement: (animalId: String, direction: String, from: String, to: String, day: String) -> Unit,
    val onPedigree: (animalId: String, parentId: String, relation: String) -> Unit,
    val onPlaceLot: (groupId: String, heads: String, day: String) -> Unit,
    val onDaysOnFeed: (groupId: String, days: String, day: String) -> Unit,
    val onCloseLot: (groupId: String, headOut: String, weightGrams: String, daysOnFeed: String, day: String) -> Unit,
)

private enum class CattleOpsPage {
    HOME,
    SERVICE,
    PD,
    CALVING,
    BCS,
    MILK,
    LOCOMOTION,
    SCC,
    DRY_OFF,
    WEANING,
    IDENTIFIER,
    MOVEMENT,
    PEDIGREE,
    LOT_PLACE,
    DAYS_ON_FEED,
    LOT_CLOSE,
}

@Composable
fun CattleOperationsScreen(
    selectedAnimalId: String?,
    busy: Boolean,
    error: String?,
    actions: CattleOperationsActions,
    onBack: () -> Unit,
) {
    var page by remember { mutableStateOf(CattleOpsPage.HOME) }
    val home = { page = CattleOpsPage.HOME }
    when (page) {
        CattleOpsPage.HOME -> {
            CattleOpsHome(selectedAnimalId, error, { page = it }, onBack)
        }

        CattleOpsPage.SERVICE -> {
            CattleServiceScreen(selectedAnimalId, busy, error, actions.onService, home)
        }

        CattleOpsPage.PD -> {
            CattlePdScreen(selectedAnimalId, busy, error, actions.onPd, home)
        }

        CattleOpsPage.CALVING -> {
            CattleCalvingScreen(selectedAnimalId, busy, error, actions.onCalving, home)
        }

        CattleOpsPage.BCS -> {
            CattleBcsScreen(selectedAnimalId, busy, error, actions.onBcs, home)
        }

        CattleOpsPage.MILK -> {
            CattleMilkScreen(selectedAnimalId, busy, error, actions.onMilk, home)
        }

        CattleOpsPage.LOCOMOTION -> {
            CattleScoreScreen(
                "FOS-CATTLE-023",
                "Locomotion score",
                "Record the observed locomotion score 1–5.",
                selectedAnimalId,
                busy,
                error,
                actions.onLocomotion,
                home,
                FarmVisualClass.I4,
            )
        }

        CattleOpsPage.SCC -> {
            CattleSccScreen(selectedAnimalId, busy, error, actions.onScc, home)
        }

        CattleOpsPage.DRY_OFF -> {
            CattleDryOffScreen(selectedAnimalId, busy, error, actions.onDryOff, home)
        }

        CattleOpsPage.WEANING -> {
            CattleWeaningScreen(selectedAnimalId, busy, error, actions.onWeaning, home)
        }

        CattleOpsPage.IDENTIFIER -> {
            CattleIdentifierScreen(selectedAnimalId, busy, error, actions.onIdentifier, home)
        }

        CattleOpsPage.MOVEMENT -> {
            CattleMovementScreen(selectedAnimalId, busy, error, actions.onMovement, home)
        }

        CattleOpsPage.PEDIGREE -> {
            CattlePedigreeScreen(selectedAnimalId, busy, error, actions.onPedigree, home)
        }

        CattleOpsPage.LOT_PLACE -> {
            CattleLotPlaceScreen(busy, error, actions.onPlaceLot, home)
        }

        CattleOpsPage.DAYS_ON_FEED -> {
            CattleDaysOnFeedScreen(busy, error, actions.onDaysOnFeed, home)
        }

        CattleOpsPage.LOT_CLOSE -> {
            CattleLotCloseScreen(busy, error, actions.onCloseLot, home)
        }
    }
}

@Composable
private fun CattleOpsHome(
    selectedId: String?,
    error: String?,
    onOpen: (CattleOpsPage) -> Unit,
    onBack: () -> Unit,
) {
    FarmOperationalPage(
        screenId = "FOS-CATTLE-018",
        title = "Dairy & cattle operations",
        subtitle = "Reproduction, milk, health, traceability and beef records.",
        visualClass = FarmVisualClass.I2,
        onBack = onBack,
    ) {
        FarmOperationalSection("Selected cattle") { Text(selectedId ?: "No individual selected — lot workflows remain available.") }
        FarmOperationalSection("Reproduction") {
            CattleNav("Service") { onOpen(CattleOpsPage.SERVICE) }
            CattleNav("Pregnancy diagnosis") { onOpen(CattleOpsPage.PD) }
            CattleNav("Calving") { onOpen(CattleOpsPage.CALVING) }
            CattleNav("Weaning") { onOpen(CattleOpsPage.WEANING) }
        }
        FarmOperationalSection("Dairy & condition") {
            CattleNav("Body condition score") { onOpen(CattleOpsPage.BCS) }
            CattleNav("Milk capture") { onOpen(CattleOpsPage.MILK) }
            CattleNav("SCC") { onOpen(CattleOpsPage.SCC) }
            CattleNav("Dry-off") { onOpen(CattleOpsPage.DRY_OFF) }
            CattleNav("Locomotion") { onOpen(CattleOpsPage.LOCOMOTION) }
        }
        FarmOperationalSection("Traceability") {
            CattleNav("Official identifier") { onOpen(CattleOpsPage.IDENTIFIER) }
            CattleNav("Movement") { onOpen(CattleOpsPage.MOVEMENT) }
            CattleNav("Pedigree link") { onOpen(CattleOpsPage.PEDIGREE) }
        }
        FarmOperationalSection("Beef / feedlot") {
            CattleNav("Place lot on feed") { onOpen(CattleOpsPage.LOT_PLACE) }
            CattleNav("Days on feed") { onOpen(CattleOpsPage.DAYS_ON_FEED) }
            CattleNav("Close-out") { onOpen(CattleOpsPage.LOT_CLOSE) }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun CattleNav(
    label: String,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label) }
}

@Composable
private fun CattleServiceScreen(
    selectedId: String?,
    busy: Boolean,
    error: String?,
    onRecord: (String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var animalId by remember(selectedId) { mutableStateOf(selectedId.orEmpty()) }
    var method by remember { mutableStateOf("ai") }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    CattleFormPage(
        "FOS-CATTLE-010",
        "Service",
        "Method is ai, natural or et; due work is generated from this record.",
        busy,
        error,
        onBack,
    ) {
        CattleField(animalId, { animalId = it }, "Cow id", busy)
        CattleField(method, { method = it }, "Method", busy)
        CattleField(day, {
            day =
                it
        }, "Service date", busy)
        Button(onClick = {
            onRecord(animalId, method, day)
        }, enabled = !busy && animalId.isNotBlank() && cattleDate(day), modifier = Modifier.fillMaxWidth()) { Text("Record service") }
    }
}

@Composable
private fun CattlePdScreen(
    selectedId: String?,
    busy: Boolean,
    error: String?,
    onRecord: (String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var animalId by remember(selectedId) { mutableStateOf(selectedId.orEmpty()) }
    var result by remember { mutableStateOf("pregnant") }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    CattleFormPage(
        "FOS-CATTLE-013",
        "Pregnancy diagnosis",
        "Record pregnant or open. Farm OS records the result; it does not diagnose.",
        busy,
        error,
        onBack,
        FarmVisualClass.I4,
    ) {
        CattleField(animalId, { animalId = it }, "Cow id", busy)
        CattleField(result, { result = it }, "PD result", busy)
        CattleField(day, {
            day =
                it
        }, "PD date", busy)
        Button(onClick = {
            onRecord(animalId, result, day)
        }, enabled = !busy && animalId.isNotBlank() && cattleDate(day), modifier = Modifier.fillMaxWidth()) { Text("Record PD") }
    }
}

@Composable
private fun CattleCalvingScreen(
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
    CattleFormPage("FOS-CATTLE-015", "Record calving", "Live plus dead must equal born.", busy, error, onBack, FarmVisualClass.I4) {
        CattleField(animalId, { animalId = it }, "Cow id", busy)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            CattleCompactField(born, { born = it }, "Born", busy, Modifier.weight(1f))
            CattleCompactField(live, { live = it }, "Live", busy, Modifier.weight(1f))
            CattleCompactField(dead, {
                dead =
                    it
            }, "Dead", busy, Modifier.weight(1f))
        }
        CattleField(day, { day = it }, "Calving date", busy)
        Button(
            onClick = { onRecord(animalId, born, live, dead, day) },
            enabled =
                !busy && animalId.isNotBlank() && born.isNotBlank() && live.isNotBlank() && cattleDate(day),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Record calving") }
    }
}

@Composable
private fun CattleBcsScreen(
    selectedId: String?,
    busy: Boolean,
    error: String?,
    onRecord: (String, String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var animalId by remember(selectedId) { mutableStateOf(selectedId.orEmpty()) }
    var scale by remember { mutableStateOf("1_5") }
    var score by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    CattleFormPage(
        "FOS-CATTLE-008",
        "Body condition score",
        "Use the configured 1–5 or 1–9 scale and store score as tenths.",
        busy,
        error,
        onBack,
    ) {
        CattleField(animalId, { animalId = it }, "Cattle id", busy)
        CattleField(scale, { scale = it }, "Scale", busy)
        CattleField(score, {
            score =
                it
        }, "Score tenths", busy)
        CattleField(day, { day = it }, "Date", busy)
        Button(
            onClick = { onRecord(animalId, scale, score, day) },
            enabled =
                !busy && animalId.isNotBlank() && score.isNotBlank() && cattleDate(day),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Record BCS") }
    }
}

@Composable
private fun CattleMilkScreen(
    selectedId: String?,
    busy: Boolean,
    error: String?,
    onRecord: (String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var animalId by remember(selectedId) { mutableStateOf(selectedId.orEmpty()) }
    var litres by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    CattleFormPage("FOS-CATTLE-019", "Milk capture", "Enter litres; the device stores milli-litres exactly.", busy, error, onBack) {
        CattleField(animalId, { animalId = it }, "Cow id", busy)
        CattleField(litres, { litres = it }, "Litres", busy)
        CattleField(day, {
            day =
                it
        }, "Milk date", busy)
        Button(
            onClick = { onRecord(animalId, litres, day) },
            enabled =
                !busy && animalId.isNotBlank() && litres.isNotBlank() && cattleDate(day),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Record milk") }
    }
}

@Composable
private fun CattleScoreScreen(
    screenId: String,
    title: String,
    subtitle: String,
    selectedId: String?,
    busy: Boolean,
    error: String?,
    onRecord: (String, String, String) -> Unit,
    onBack: () -> Unit,
    visualClass: FarmVisualClass,
) {
    var animalId by remember(selectedId) { mutableStateOf(selectedId.orEmpty()) }
    var score by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    CattleFormPage(screenId, title, subtitle, busy, error, onBack, visualClass) {
        CattleField(animalId, { animalId = it }, "Cattle id", busy)
        CattleField(score, { score = it }, "Score", busy)
        CattleField(day, {
            day =
                it
        }, "Date", busy)
        Button(
            onClick = { onRecord(animalId, score, day) },
            enabled =
                !busy && animalId.isNotBlank() && score.isNotBlank() && cattleDate(day),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Record $title") }
    }
}

@Composable
private fun CattleSccScreen(
    selectedId: String?,
    busy: Boolean,
    error: String?,
    onRecord: (String, String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var animalId by remember(selectedId) { mutableStateOf(selectedId.orEmpty()) }
    var cells by remember { mutableStateOf("") }
    var dim by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    CattleFormPage(
        "FOS-CATTLE-021",
        "SCC capture",
        "SCC is a cell count, not a Farm OS diagnosis.",
        busy,
        error,
        onBack,
        FarmVisualClass.I4,
    ) {
        CattleField(animalId, { animalId = it }, "Cow id", busy)
        CattleField(cells, { cells = it }, "Cells/ml", busy)
        CattleField(dim, {
            dim =
                it
        }, "DIM days (optional)", busy)
        CattleField(day, { day = it }, "Date", busy)
        Button(
            onClick = { onRecord(animalId, cells, dim, day) },
            enabled =
                !busy && animalId.isNotBlank() && cells.isNotBlank() && cattleDate(day),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Record SCC") }
    }
}

@Composable
private fun CattleDryOffScreen(
    selectedId: String?,
    busy: Boolean,
    error: String?,
    onRecord: (String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var animalId by remember(selectedId) { mutableStateOf(selectedId.orEmpty()) }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    var expected by remember { mutableStateOf("") }
    CattleFormPage("FOS-CATTLE-024", "Dry-off", "Record dry-off and an optional expected calving date.", busy, error, onBack) {
        CattleField(animalId, { animalId = it }, "Cow id", busy)
        CattleField(day, { day = it }, "Dry-off date", busy)
        CattleField(expected, {
            expected =
                it
        }, "Expected calving (optional)", busy)
        Button(
            onClick = { onRecord(animalId, day, expected) },
            enabled =
                !busy && animalId.isNotBlank() && cattleDate(day) && (expected.isBlank() || cattleDate(expected)),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Record dry-off") }
    }
}

@Composable
private fun CattleWeaningScreen(
    selectedId: String?,
    busy: Boolean,
    error: String?,
    onRecord: (String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var animalId by remember(selectedId) { mutableStateOf(selectedId.orEmpty()) }
    var grams by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    CattleFormPage("FOS-CATTLE-017", "Weaning", "Record calf weaning and optional weight in grams.", busy, error, onBack) {
        CattleField(animalId, { animalId = it }, "Calf id", busy)
        CattleField(grams, { grams = it }, "Weight grams (optional)", busy)
        CattleField(day, {
            day =
                it
        }, "Weaning date", busy)
        Button(onClick = {
            onRecord(animalId, grams, day)
        }, enabled = !busy && animalId.isNotBlank() && cattleDate(day), modifier = Modifier.fillMaxWidth()) { Text("Record weaning") }
    }
}

@Composable
private fun CattleIdentifierScreen(
    selectedId: String?,
    busy: Boolean,
    error: String?,
    onRecord: (String, String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var animalId by remember(selectedId) { mutableStateOf(selectedId.orEmpty()) }
    var type by remember { mutableStateOf("nlis") }
    var value by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    CattleFormPage("FOS-CATTLE-005", "Official identifier", "Bind an approved identifier to this cattle record.", busy, error, onBack) {
        CattleField(animalId, { animalId = it }, "Cattle id", busy)
        CattleField(type, { type = it }, "Identifier type", busy)
        CattleField(value, {
            value =
                it
        }, "Identifier value", busy)
        CattleField(day, { day = it }, "Date", busy)
        Button(
            onClick = { onRecord(animalId, type, value, day) },
            enabled =
                !busy && animalId.isNotBlank() && value.isNotBlank() && cattleDate(day),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Assign identifier") }
    }
}

@Composable
private fun CattleMovementScreen(
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
    CattleFormPage("FOS-CATTLE-030", "Movement", "Record on, off or transfer movement.", busy, error, onBack) {
        CattleField(animalId, { animalId = it }, "Cattle id", busy)
        CattleField(direction, { direction = it }, "Direction", busy)
        CattleField(from, {
            from =
                it
        }, "From", busy)
        CattleField(to, { to = it }, "To", busy)
        CattleField(day, { day = it }, "Date", busy)
        Button(onClick = {
            onRecord(animalId, direction, from, to, day)
        }, enabled = !busy && animalId.isNotBlank() && cattleDate(day), modifier = Modifier.fillMaxWidth()) { Text("Record movement") }
    }
}

@Composable
private fun CattlePedigreeScreen(
    selectedId: String?,
    busy: Boolean,
    error: String?,
    onRecord: (String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var animalId by remember(selectedId) { mutableStateOf(selectedId.orEmpty()) }
    var parent by remember { mutableStateOf("") }
    var relation by remember { mutableStateOf("sire") }
    CattleFormPage(
        "FOS-CATTLE-032",
        "Pedigree",
        "Record parentage only. This does not calculate COI or genetic merit.",
        busy,
        error,
        onBack,
    ) {
        CattleField(animalId, { animalId = it }, "Cattle id", busy)
        CattleField(parent, { parent = it }, "Parent id", busy)
        CattleField(relation, {
            relation =
                it
        }, "Relation", busy)
        Button(onClick = {
            onRecord(animalId, parent, relation)
        }, enabled = !busy && animalId.isNotBlank() && parent.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Link parent") }
    }
}

@Composable
private fun CattleLotPlaceScreen(
    busy: Boolean,
    error: String?,
    onRecord: (String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var group by remember { mutableStateOf("") }
    var heads by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    CattleFormPage("FOS-CATTLE-026", "Feedlot placement", "Place a cattle group onto feed with head count.", busy, error, onBack) {
        CattleField(group, { group = it }, "Cattle lot/group id", busy)
        CattleField(heads, { heads = it }, "Head count", busy)
        CattleField(day, {
            day =
                it
        }, "Placed date", busy)
        Button(onClick = {
            onRecord(group, heads, day)
        }, enabled = !busy && group.isNotBlank() && heads.isNotBlank() && cattleDate(day), modifier = Modifier.fillMaxWidth()) {
            Text("Place lot on feed")
        }
    }
}

@Composable
private fun CattleDaysOnFeedScreen(
    busy: Boolean,
    error: String?,
    onRecord: (String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var group by remember { mutableStateOf("") }
    var days by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    CattleFormPage("FOS-CATTLE-028", "Days on feed", "Record non-negative days on feed for a cattle lot.", busy, error, onBack) {
        CattleField(group, { group = it }, "Cattle lot/group id", busy)
        CattleField(days, { days = it }, "Days on feed", busy)
        CattleField(day, {
            day =
                it
        }, "Record date", busy)
        Button(onClick = {
            onRecord(group, days, day)
        }, enabled = !busy && group.isNotBlank() && days.isNotBlank() && cattleDate(day), modifier = Modifier.fillMaxWidth()) {
            Text("Record days on feed")
        }
    }
}

@Composable
private fun CattleLotCloseScreen(
    busy: Boolean,
    error: String?,
    onRecord: (String, String, String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var group by remember { mutableStateOf("") }
    var heads by remember { mutableStateOf("") }
    var grams by remember { mutableStateOf("") }
    var days by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    CattleFormPage(
        "FOS-CATTLE-029",
        "Lot close-out",
        "Close the lot with head-out count and optional weight/days-on-feed evidence.",
        busy,
        error,
        onBack,
    ) {
        CattleField(group, { group = it }, "Cattle lot/group id", busy)
        CattleField(heads, { heads = it }, "Head out", busy)
        CattleField(grams, {
            grams =
                it
        }, "Weight grams (optional)", busy)
        CattleField(days, { days = it }, "Days on feed (optional)", busy)
        CattleField(day, {
            day =
                it
        }, "Close date", busy)
        Button(
            onClick = { onRecord(group, heads, grams, days, day) },
            enabled =
                !busy && group.isNotBlank() && heads.isNotBlank() && cattleDate(day),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Close lot") }
    }
}

@Composable
private fun CattleFormPage(
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
private fun CattleField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    busy: Boolean,
) {
    OutlinedTextField(value, onValue, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
}

@Composable
private fun CattleCompactField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    busy: Boolean,
    modifier: Modifier,
) {
    OutlinedTextField(value, onValue, label = { Text(label) }, modifier = modifier, enabled = !busy, singleLine = true)
}

private fun cattleDate(value: String): Boolean = runCatching { LocalDate.parse(value) }.isSuccess
