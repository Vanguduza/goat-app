package com.farmos.feature.ops

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
import java.time.LocalDate

private enum class PoultryPage {
    DASHBOARD,
    KINDS,
    HOUSES,
    FLOCKS,
    PLACEMENT,
    DAILY,
    HATCHERY,
    SET_EGGS,
    CANDLING,
    HATCH_RESULT,
    VACCINATION,
    BIOSECURITY,
}

/** Flock/house-first Poultry UX. Individual-bird CRUD is intentionally not the primary navigation model. */
@Composable
fun PoultryExperienceScreen(
    enabledKinds: List<String>,
    houses: List<String>,
    placements: List<String>,
    flockDays: List<String>,
    hatches: List<String>,
    vaccinations: List<String>,
    busy: Boolean,
    error: String?,
    onEnableKind: (String) -> Unit,
    onCreateHouse: (code: String, houseKind: String, poultryKind: String) -> Unit,
    onPlaceFlock: (groupId: String, houseId: String, poultryKind: String, heads: String, day: String) -> Unit,
    onRecordFlockDay: (groupId: String, eggs: String, dead: String, culls: String, feedGrams: String, day: String) -> Unit,
    onSetEggs: (poultryKind: String, eggs: String, day: String, houseId: String, groupId: String) -> Unit,
    onCandle: (hatchId: String, fertile: String, infertile: String, midDead: String, day: String) -> Unit,
    onRecordHatch: (hatchId: String, hatched: String, culls: String, day: String) -> Unit,
    onVaccinate: (groupId: String, poultryKind: String, formularyId: String, day: String) -> Unit,
    onBiosecurity: (houseId: String, groupId: String, findings: String, mixedSpecies: Boolean, day: String) -> Unit,
    onBack: () -> Unit,
) {
    var page by remember { mutableStateOf(PoultryPage.DASHBOARD) }
    val home = { page = PoultryPage.DASHBOARD }
    when (page) {
        PoultryPage.DASHBOARD -> PoultryDashboard(enabledKinds, houses, placements, flockDays, hatches, error, { page = it }, onBack)
        PoultryPage.KINDS -> PoultryKinds(enabledKinds, busy, error, onEnableKind, home)
        PoultryPage.HOUSES -> PoultryHouses(houses, busy, error, onCreateHouse, home)
        PoultryPage.FLOCKS -> PoultryRows("FOS-POULTRY-004", "Flocks", placements, "No flock placements yet", error, home)
        PoultryPage.PLACEMENT -> PoultryPlacement(busy, error, onPlaceFlock, home)
        PoultryPage.DAILY -> PoultryDaily(flockDays, busy, error, onRecordFlockDay, home)
        PoultryPage.HATCHERY -> PoultryRows("FOS-POULTRY-017", "Hatchery", hatches, "No hatch batches yet", error, home, FarmVisualClass.I2)
        PoultryPage.SET_EGGS -> PoultrySetEggs(busy, error, onSetEggs, home)
        PoultryPage.CANDLING -> PoultryCandling(busy, error, onCandle, home)
        PoultryPage.HATCH_RESULT -> PoultryHatchResult(busy, error, onRecordHatch, home)
        PoultryPage.VACCINATION -> PoultryVaccination(vaccinations, busy, error, onVaccinate, home)
        PoultryPage.BIOSECURITY -> PoultryBiosecurity(busy, error, onBiosecurity, home)
    }
}

/** FOS-POULTRY-001 — illustrated flock/house operating dashboard. */
@Composable
private fun PoultryDashboard(
    enabledKinds: List<String>,
    houses: List<String>,
    placements: List<String>,
    flockDays: List<String>,
    hatches: List<String>,
    error: String?,
    onOpen: (PoultryPage) -> Unit,
    onBack: () -> Unit,
) {
    FarmPastoralBackdrop(Modifier.fillMaxSize(), heroSpecies = FarmSpeciesVisual.POULTRY) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(FosDimens.SectionGap),
        ) {
            FarmStorySurface(Modifier.fillMaxWidth()) {
                Text("Poultry", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Flocks. Houses. Production. Hatchery. Biosecurity.", color = MaterialTheme.colorScheme.primary)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PoultryMetric("Kinds", enabledKinds.size, Modifier.weight(1f))
                PoultryMetric("Houses", houses.size, Modifier.weight(1f))
                PoultryMetric("Flocks", placements.size, Modifier.weight(1f))
                PoultryMetric("Hatches", hatches.size, Modifier.weight(1f))
            }
            PoultryAction("Enabled kinds", "Chicken, duck, turkey, quail and farm-defined kinds") { onOpen(PoultryPage.KINDS) }
            PoultryAction("Houses", "Housing units and poultry kind") { onOpen(PoultryPage.HOUSES) }
            PoultryAction("Flocks", "Current flock placements") { onOpen(PoultryPage.FLOCKS) }
            PoultryAction("Place flock", "Assign a flock to a house") { onOpen(PoultryPage.PLACEMENT) }
            PoultryAction("Daily flock record", "Eggs, mortality, culls and feed") { onOpen(PoultryPage.DAILY) }
            PoultryAction("Hatchery", "Egg sets, candling and hatch outcomes") { onOpen(PoultryPage.HATCHERY) }
            PoultryAction("Set eggs", "Start an incubation batch") { onOpen(PoultryPage.SET_EGGS) }
            PoultryAction("Candling", "Record fertile, infertile and mid-dead counts") { onOpen(PoultryPage.CANDLING) }
            PoultryAction("Hatch result", "Record hatched and culled chicks") { onOpen(PoultryPage.HATCH_RESULT) }
            PoultryAction("Vaccination", "Use a vet-approved formulary item") { onOpen(PoultryPage.VACCINATION) }
            PoultryAction("Biosecurity", "Record house/flock findings") { onOpen(PoultryPage.BIOSECURITY) }
            if (flockDays.isEmpty()) Text("No daily flock records yet.")
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = onBack) { Text("Back to farm home") }
        }
    }
}

@Composable
private fun PoultryMetric(
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
private fun PoultryAction(
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
private fun PoultryRows(
    screenId: String,
    title: String,
    rows: List<String>,
    empty: String,
    error: String?,
    onBack: () -> Unit,
    visualClass: FarmVisualClass = FarmVisualClass.I3,
) {
    FarmOperationalPage(screenId, title, "Flock and house records on this device.", visualClass, onBack) {
        FarmOperationalRows(rows, empty, "New records will appear here after local capture.")
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun PoultryKinds(
    enabled: List<String>,
    busy: Boolean,
    error: String?,
    onEnable: (String) -> Unit,
    onBack: () -> Unit,
) {
    var kind by remember { mutableStateOf("chicken") }
    FarmOperationalPage(
        "FOS-POULTRY-002",
        "Enabled poultry kinds",
        "Only enabled kinds should drive flock setup.",
        FarmVisualClass.I2,
        onBack,
    ) {
        FarmOperationalRows(enabled, "No poultry kinds enabled", "Enable the first kind for this farm.")
        FarmOperationalSection("Enable kind") {
            OutlinedTextField(kind, {
                kind = it
            }, label = { Text("Poultry kind") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            Text("Listed kinds: chicken, duck, muscovy, guinea_fowl, turkey, goose, quail, pigeon, farm_defined.")
            Button(
                onClick = { onEnable(kind) },
                enabled = !busy && kind.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Enable kind") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun PoultryHouses(
    houses: List<String>,
    busy: Boolean,
    error: String?,
    onCreate: (String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var code by remember { mutableStateOf("") }
    var houseKind by remember { mutableStateOf("house") }
    var poultryKind by remember { mutableStateOf("chicken") }
    FarmOperationalPage(
        "FOS-POULTRY-006",
        "Houses",
        "Housing is configured by physical unit and poultry kind.",
        FarmVisualClass.I2,
        onBack,
    ) {
        FarmOperationalRows(houses, "No poultry houses yet", "Create a house, coop, range, hatchery, brooder, pond or loft.")
        FarmOperationalSection("Create house") {
            OutlinedTextField(code, {
                code = it
            }, label = { Text("House code") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            OutlinedTextField(houseKind, {
                houseKind = it
            }, label = { Text("Housing kind") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            OutlinedTextField(poultryKind, {
                poultryKind = it
            }, label = { Text("Poultry kind") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            Button(onClick = {
                onCreate(code, houseKind, poultryKind)
            }, enabled = !busy && code.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Create house") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun PoultryPlacement(
    busy: Boolean,
    error: String?,
    onPlace: (String, String, String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var group by remember { mutableStateOf("") }
    var house by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("chicken") }
    var heads by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    FarmOperationalPage("FOS-POULTRY-008", "Flock placement", "Place a flock into a configured house.", onBack = onBack) {
        FarmOperationalSection("Placement") {
            OutlinedTextField(group, {
                group = it
            }, label = { Text("Flock group id") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            OutlinedTextField(house, {
                house = it
            }, label = { Text("House id") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            OutlinedTextField(kind, {
                kind = it
            }, label = { Text("Poultry kind") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            OutlinedTextField(heads, {
                heads = it
            }, label = { Text("Head count") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            OutlinedTextField(day, {
                day = it
            }, label = { Text("Placement date") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            Button(
                onClick = { onPlace(group, house, kind, heads, day) },
                enabled =
                    !busy && group.isNotBlank() && house.isNotBlank() && heads.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Place flock") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun PoultryDaily(
    rows: List<String>,
    busy: Boolean,
    error: String?,
    onRecord: (String, String, String, String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var group by remember { mutableStateOf("") }
    var eggs by remember { mutableStateOf("0") }
    var dead by remember { mutableStateOf("0") }
    var culls by remember { mutableStateOf("0") }
    var feed by remember { mutableStateOf("0") }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    FarmOperationalPage(
        "FOS-POULTRY-009",
        "Daily flock record",
        "Mortality, egg output and feed belong to the same flock-day fact.",
        FarmVisualClass.I4,
        onBack,
    ) {
        FarmOperationalRows(rows, "No daily flock records", null)
        FarmOperationalSection("Flock day") {
            OutlinedTextField(group, {
                group = it
            }, label = { Text("Flock group id") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            OutlinedTextField(
                eggs,
                { eggs = it },
                label = { Text("Eggs") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                singleLine = true,
            )
            OutlinedTextField(
                dead,
                { dead = it },
                label = { Text("Dead") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                singleLine = true,
            )
            OutlinedTextField(
                culls,
                { culls = it },
                label = { Text("Culls") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                singleLine = true,
            )
            OutlinedTextField(feed, {
                feed = it
            }, label = { Text("Feed grams") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            OutlinedTextField(
                day,
                { day = it },
                label = { Text("Date") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                singleLine = true,
            )
            Button(onClick = {
                onRecord(group, eggs, dead, culls, feed, day)
            }, enabled = !busy && group.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Record flock day") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun PoultrySetEggs(
    busy: Boolean,
    error: String?,
    onSet: (String, String, String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var kind by remember { mutableStateOf("chicken") }
    var eggs by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    var house by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("") }
    FarmOperationalPage("FOS-POULTRY-018", "Set eggs", "Incubation timing follows the selected poultry kind.", FarmVisualClass.I2, onBack) {
        FarmOperationalSection("Incubation batch") {
            OutlinedTextField(kind, {
                kind = it
            }, label = { Text("Poultry kind") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            OutlinedTextField(
                eggs,
                { eggs = it },
                label = { Text("Eggs set") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                singleLine = true,
            )
            OutlinedTextField(
                day,
                { day = it },
                label = { Text("Set date") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                singleLine = true,
            )
            OutlinedTextField(house, {
                house = it
            }, label = { Text("House id (optional)") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            OutlinedTextField(group, {
                group = it
            }, label = { Text("Flock id (optional)") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            Button(onClick = {
                onSet(kind, eggs, day, house, group)
            }, enabled = !busy && eggs.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Set eggs") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun PoultryCandling(
    busy: Boolean,
    error: String?,
    onRecord: (String, String, String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var hatch by remember { mutableStateOf("") }
    var fertile by remember { mutableStateOf("") }
    var infertile by remember { mutableStateOf("0") }
    var midDead by remember { mutableStateOf("0") }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    FarmOperationalPage("FOS-POULTRY-020", "Candling", "Record incubation evidence without inventing a diagnosis.", onBack = onBack) {
        FarmOperationalSection("Candling result") {
            OutlinedTextField(hatch, {
                hatch = it
            }, label = { Text("Hatch id") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            OutlinedTextField(fertile, {
                fertile = it
            }, label = { Text("Fertile") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            OutlinedTextField(infertile, {
                infertile = it
            }, label = { Text("Infertile") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            OutlinedTextField(midDead, {
                midDead = it
            }, label = { Text("Mid-dead") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            OutlinedTextField(
                day,
                { day = it },
                label = { Text("Date") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                singleLine = true,
            )
            Button(
                onClick = { onRecord(hatch, fertile, infertile, midDead, day) },
                enabled =
                    !busy && hatch.isNotBlank() && fertile.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Record candling") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun PoultryHatchResult(
    busy: Boolean,
    error: String?,
    onRecord: (String, String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var hatch by remember { mutableStateOf("") }
    var hatched by remember { mutableStateOf("") }
    var culls by remember { mutableStateOf("0") }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    FarmOperationalPage(
        "FOS-POULTRY-021",
        "Hatch result",
        "Record hatched and culled counts for the incubation batch.",
        FarmVisualClass.I4,
        onBack,
    ) {
        FarmOperationalSection("Hatch outcome") {
            OutlinedTextField(hatch, {
                hatch = it
            }, label = { Text("Hatch id") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            OutlinedTextField(hatched, {
                hatched = it
            }, label = { Text("Hatched") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            OutlinedTextField(
                culls,
                { culls = it },
                label = { Text("Culls") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                singleLine = true,
            )
            OutlinedTextField(
                day,
                { day = it },
                label = { Text("Date") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                singleLine = true,
            )
            Button(onClick = {
                onRecord(hatch, hatched, culls, day)
            }, enabled = !busy && hatch.isNotBlank() && hatched.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Record hatch") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun PoultryVaccination(
    rows: List<String>,
    busy: Boolean,
    error: String?,
    onRecord: (String, String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var group by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("chicken") }
    var formulary by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    FarmOperationalPage(
        "FOS-POULTRY-014",
        "Vaccination",
        "Use a vet-approved formulary item. Farm OS does not calculate a dose.",
        FarmVisualClass.I4,
        onBack,
    ) {
        FarmOperationalRows(rows, "No poultry vaccinations recorded", null)
        FarmOperationalSection("Vaccination record") {
            OutlinedTextField(group, {
                group = it
            }, label = { Text("Flock group id") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            OutlinedTextField(kind, {
                kind = it
            }, label = { Text("Poultry kind") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            OutlinedTextField(formulary, {
                formulary = it
            }, label = { Text("Formulary item id") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            OutlinedTextField(day, {
                day = it
            }, label = { Text("Vaccination date") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            Button(onClick = {
                onRecord(group, kind, formulary, day)
            }, enabled = !busy && group.isNotBlank() && formulary.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Record vaccination",
                )
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun PoultryBiosecurity(
    busy: Boolean,
    error: String?,
    onRecord: (String, String, String, Boolean, String) -> Unit,
    onBack: () -> Unit,
) {
    var house by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("") }
    var findings by remember { mutableStateOf("") }
    var mixed by remember { mutableStateOf(false) }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    FarmOperationalPage(
        "FOS-POULTRY-016",
        "Biosecurity check",
        "Record findings against a house or flock before they become invisible risk.",
        FarmVisualClass.I4,
        onBack,
    ) {
        FarmOperationalSection("Walk findings") {
            OutlinedTextField(house, {
                house = it
            }, label = { Text("House id") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            OutlinedTextField(group, {
                group = it
            }, label = { Text("Flock id") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            OutlinedTextField(
                findings,
                { findings = it },
                label = { Text("Findings") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
            )
            TextButton(onClick = {
                mixed = !mixed
            }, enabled = !busy) { Text(if (mixed) "Mixed-species exposure · yes" else "Mixed-species exposure · no") }
            OutlinedTextField(
                day,
                { day = it },
                label = { Text("Date") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                singleLine = true,
            )
            Button(
                onClick = { onRecord(house, group, findings, mixed, day) },
                enabled =
                    !busy && findings.isNotBlank() && (house.isNotBlank() || group.isNotBlank()),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Record biosecurity check") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
