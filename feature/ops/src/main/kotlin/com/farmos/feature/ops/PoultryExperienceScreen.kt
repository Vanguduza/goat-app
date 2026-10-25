package com.farmos.feature.ops

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
import com.farmos.core.design.FarmSelectionAtoms
import com.farmos.core.design.FarmSelectorOption
import com.farmos.core.design.FarmVisualClass
import com.farmos.core.design.FosDimens
import java.time.LocalDate

internal enum class PoultryPage {
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
    FLOCK_PROFILE,
    HOUSE_DETAIL,
    EGG_PRODUCTION,
    FEED,
    INCUBATION_BATCH,
    BIOSECURITY_RECORDS,
    FLOCK_HEALTH,
    FLOCK_TIMELINE,
    FLOCK_MOVE,
    FLOCK_CLOSEOUT,
    POULTRY_REPORT,
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
    onMoveFlock: (groupId: String, fromHouseId: String, toHouseId: String, heads: String, day: String) -> Unit = { _, _, _, _, _ -> },
    onCloseFlock: (groupId: String, headOut: String, reason: String, day: String) -> Unit = { _, _, _, _ -> },
    onBack: () -> Unit,
    records: PoultryRecords = PoultryRecords(),
    loadFlock: suspend (String) -> PoultryFlockRecords = { PoultryFlockRecords() },
    /** This farm's poultry groups; placement, flock-day and vaccination commands accept only these. */
    flockGroups: List<FarmSelectorOption> = emptyList(),
    /** This farm's poultry houses; placement accepts only a configured house. */
    houseOptions: List<FarmSelectorOption> = emptyList(),
    /** Vet-approved poultry formulary items; vaccination accepts only these. */
    poultryFormulary: List<FarmSelectorOption> = emptyList(),
    /** Hatches still in `set` status; candling accepts only these. */
    setHatches: List<FarmSelectorOption> = emptyList(),
    /** Hatches in `candled` status; a hatch result accepts only these. */
    candledHatches: List<FarmSelectorOption> = emptyList(),
) {
    var page by rememberSaveable { mutableStateOf(PoultryPage.DASHBOARD) }
    val home = { page = PoultryPage.DASHBOARD }
    val pageStateHolder = rememberSaveableStateHolder()
    pageStateHolder.SaveableStateProvider(page.name) {
    when (page) {
        PoultryPage.DASHBOARD -> PoultryDashboard(enabledKinds, houses, records, error, { page = it }, onBack)
        PoultryPage.KINDS -> PoultryKinds(enabledKinds, busy, error, onEnableKind, home)
        PoultryPage.HOUSES -> PoultryHouses(houses, busy, error, onCreateHouse, home)
        PoultryPage.FLOCKS -> PoultryRows("FOS-POULTRY-004", "Flocks", placements, "No flock placements yet", error, home)
        PoultryPage.PLACEMENT -> PoultryPlacement(flockGroups, houseOptions, busy, error, onPlaceFlock, home)
        PoultryPage.DAILY -> PoultryDaily(flockDays, flockGroups, busy, error, onRecordFlockDay, home)
        PoultryPage.HATCHERY -> PoultryHatcheryScreen(records, error, { page = it }, home)
        PoultryPage.SET_EGGS -> PoultrySetEggs(houseOptions, flockGroups, busy, error, onSetEggs, home)
        PoultryPage.CANDLING -> PoultryCandling(setHatches, busy, error, onCandle, home)
        PoultryPage.HATCH_RESULT -> PoultryHatchResult(candledHatches, busy, error, onRecordHatch, home)
        PoultryPage.VACCINATION -> PoultryVaccination(vaccinations, flockGroups, poultryFormulary, busy, error, onVaccinate, home)
        PoultryPage.BIOSECURITY -> PoultryBiosecurity(houseOptions, flockGroups, busy, error, onBiosecurity, home)
        PoultryPage.FLOCK_PROFILE -> PoultryFlockProfileScreen(records, home)
        PoultryPage.HOUSE_DETAIL -> PoultryHouseDetailScreen(records, home)
        PoultryPage.EGG_PRODUCTION -> PoultryEggProductionScreen(records, home)
        PoultryPage.FEED -> PoultryFeedScreen(records, home)
        PoultryPage.INCUBATION_BATCH -> PoultryIncubationBatchScreen(records, home)
        PoultryPage.BIOSECURITY_RECORDS -> PoultryBiosecurityDashboardScreen(records, home)
        PoultryPage.FLOCK_HEALTH -> PoultryFlockRecordScreen(timeline = false, records.flocks, loadFlock, home)
        PoultryPage.FLOCK_TIMELINE -> PoultryFlockRecordScreen(timeline = true, records.flocks, loadFlock, home)
        PoultryPage.FLOCK_MOVE -> PoultryFlockMove(flockGroups, houseOptions, busy, error, onMoveFlock, home)
        PoultryPage.FLOCK_CLOSEOUT -> PoultryFlockCloseout(flockGroups, records.flocks, busy, error, onCloseFlock, home)
        PoultryPage.POULTRY_REPORT -> PoultryReportScreen(records, home)
    }
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
    FarmOperationalPage(screenId, title, "", visualClass, onBack) {
        FarmOperationalRows(rows, empty, null)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

/**
 * FOS-POULTRY-002 — enabled poultry kinds; FOS-POULTRY-003 — enable kind, served as the
 * "Enable kind" form section below (not a separate route).
 */
@Composable
private fun PoultryKinds(
    enabled: List<String>,
    busy: Boolean,
    error: String?,
    onEnable: (String) -> Unit,
    onBack: () -> Unit,
) {
    var kind by rememberSaveable { mutableStateOf("chicken") }
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
                modifier = Modifier.fillMaxWidth().testTag("farm-screen:FOS-POULTRY-003"),
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
    var code by rememberSaveable { mutableStateOf("") }
    var houseKind by rememberSaveable { mutableStateOf("house") }
    var poultryKind by rememberSaveable { mutableStateOf("chicken") }
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
/**
 * FOS-POULTRY-022 — Chick Placement: assign a flock (animal group) to a poultry house
 * via the governed PlacePoultryFlock command.
 */
private fun PoultryPlacement(
    flockGroups: List<FarmSelectorOption>,
    houseOptions: List<FarmSelectorOption>,
    busy: Boolean,
    error: String?,
    onPlace: (String, String, String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var group by rememberSaveable { mutableStateOf("") }
    var house by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf("chicken") }
    var heads by rememberSaveable { mutableStateOf("") }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    FarmOperationalPage("FOS-POULTRY-008", "Flock placement", "Place a flock into a configured house.", onBack = onBack) {
        FarmOperationalSection("Placement") {
            FlockGroupSelector(flockGroups, group, busy) { group = it }
            FarmEntitySelector(
                atomTag = FarmSelectionAtoms.LOCATION_SELECTOR,
                title = "House",
                options = houseOptions,
                selectedId = house.ifBlank { null },
                onSelect = { house = it },
                emptyText = "Create a poultry house first.",
                enabled = !busy,
            )
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
/**
 * FOS-POULTRY-011 — Egg Collection: record one day's eggs, mortality, culls and feed
 * for a flock via the governed RecordPoultryFlockDay command.
 */
private fun PoultryDaily(
    rows: List<String>,
    flockGroups: List<FarmSelectorOption>,
    busy: Boolean,
    error: String?,
    onRecord: (String, String, String, String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var group by rememberSaveable { mutableStateOf("") }
    var eggs by rememberSaveable { mutableStateOf("0") }
    var dead by rememberSaveable { mutableStateOf("0") }
    var culls by rememberSaveable { mutableStateOf("0") }
    var feed by rememberSaveable { mutableStateOf("0") }
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
            FlockGroupSelector(flockGroups, group, busy) { group = it }
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
    houseOptions: List<FarmSelectorOption>,
    flockGroups: List<FarmSelectorOption>,
    busy: Boolean,
    error: String?,
    onSet: (String, String, String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var kind by rememberSaveable { mutableStateOf("chicken") }
    var eggs by rememberSaveable { mutableStateOf("") }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    var house by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf("") }
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
            OptionalPoultrySelector(FarmSelectionAtoms.LOCATION_SELECTOR, "House (optional)", "No house", houseOptions, house, busy) { house = it }
            OptionalPoultrySelector(FarmSelectionAtoms.GROUP_SELECTOR, "Flock (optional)", "No flock", flockGroups, group, busy) { group = it }
            Button(onClick = {
                onSet(kind, eggs, day, house, group)
            }, enabled = !busy && eggs.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Set eggs") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun PoultryCandling(
    hatches: List<FarmSelectorOption>,
    busy: Boolean,
    error: String?,
    onRecord: (String, String, String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var hatch by rememberSaveable { mutableStateOf("") }
    var fertile by rememberSaveable { mutableStateOf("") }
    var infertile by rememberSaveable { mutableStateOf("0") }
    var midDead by rememberSaveable { mutableStateOf("0") }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    FarmOperationalPage("FOS-POULTRY-020", "Candling", "Record incubation evidence without inventing a diagnosis.", onBack = onBack) {
        FarmOperationalSection("Candling result") {
            HatchSelector(hatches, hatch, busy) { hatch = it }
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
    hatches: List<FarmSelectorOption>,
    busy: Boolean,
    error: String?,
    onRecord: (String, String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var hatch by rememberSaveable { mutableStateOf("") }
    var hatched by rememberSaveable { mutableStateOf("") }
    var culls by rememberSaveable { mutableStateOf("0") }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    FarmOperationalPage(
        "FOS-POULTRY-021",
        "Hatch result",
        "Record hatched and culled counts for the incubation batch.",
        FarmVisualClass.I4,
        onBack,
    ) {
        FarmOperationalSection("Hatch outcome") {
            HatchSelector(hatches, hatch, busy) { hatch = it }
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
/**
 * FOS-POULTRY-013 — Vaccination Schedule: the flock's recorded vaccinations and the
 * capture form, using vet-approved formulary items via RecordPoultryVaccination.
 */
private fun PoultryVaccination(
    rows: List<String>,
    flockGroups: List<FarmSelectorOption>,
    poultryFormulary: List<FarmSelectorOption>,
    busy: Boolean,
    error: String?,
    onRecord: (String, String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var group by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf("chicken") }
    var formulary by rememberSaveable { mutableStateOf("") }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    FarmOperationalPage(
        "FOS-POULTRY-014",
        "Vaccination",
        "Use a vet-approved formulary item. Animal Farm does not calculate a dose.",
        FarmVisualClass.I4,
        onBack,
    ) {
        FarmOperationalRows(rows, "No poultry vaccinations recorded", null)
        FarmOperationalSection("Vaccination record") {
            FlockGroupSelector(flockGroups, group, busy) { group = it }
            OutlinedTextField(kind, {
                kind = it
            }, label = { Text("Poultry kind") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            FarmEntitySelector(
                atomTag = POULTRY_FORMULARY_SELECTOR,
                title = "Vet-approved poultry formulary item",
                options = poultryFormulary,
                selectedId = formulary.ifBlank { null },
                onSelect = { formulary = it },
                emptyText = "No vet-approved poultry formulary items. Add one in Health first.",
                enabled = !busy,
            )
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
    houseOptions: List<FarmSelectorOption>,
    flockGroups: List<FarmSelectorOption>,
    busy: Boolean,
    error: String?,
    onRecord: (String, String, String, Boolean, String) -> Unit,
    onBack: () -> Unit,
) {
    var house by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf("") }
    var findings by rememberSaveable { mutableStateOf("") }
    var mixed by rememberSaveable { mutableStateOf(false) }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    FarmOperationalPage(
        "FOS-POULTRY-016",
        "Biosecurity check",
        "Record findings against a house or flock before they become invisible risk.",
        FarmVisualClass.I4,
        onBack,
    ) {
        FarmOperationalSection("Walk findings") {
            Text("Record against a house, a flock, or both.")
            OptionalPoultrySelector(FarmSelectionAtoms.LOCATION_SELECTOR, "House", "No house", houseOptions, house, busy) { house = it }
            OptionalPoultrySelector(FarmSelectionAtoms.GROUP_SELECTOR, "Flock", "No flock", flockGroups, group, busy) { group = it }
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

/** FOS-ATOM-005 over this farm's poultry groups, so a flock command never carries a mistyped id. */
@Composable
private fun FlockGroupSelector(flockGroups: List<FarmSelectorOption>, selected: String, busy: Boolean, onSelect: (String) -> Unit) {
    FarmEntitySelector(
        atomTag = FarmSelectionAtoms.GROUP_SELECTOR,
        title = "Flock",
        options = flockGroups,
        selectedId = selected.ifBlank { null },
        onSelect = onSelect,
        emptyText = "Create a poultry group in Groups first.",
        enabled = !busy,
    )
}

/** Hatch selector limited to the hatches in the status the owning command accepts. */
@Composable
private fun HatchSelector(hatches: List<FarmSelectorOption>, selected: String, busy: Boolean, onSelect: (String) -> Unit) {
    FarmEntitySelector(
        atomTag = POULTRY_HATCH_SELECTOR,
        title = "Hatch",
        options = hatches,
        selectedId = selected.ifBlank { null },
        onSelect = onSelect,
        emptyText = "No hatch is at this stage.",
        enabled = !busy,
    )
}

/** An optional house or flock reference: an explicit "none" option sends no id, any other option this farm's id. */
@Composable
private fun OptionalPoultrySelector(
    atomTag: String,
    title: String,
    noneLabel: String,
    options: List<FarmSelectorOption>,
    selected: String,
    busy: Boolean,
    onSelect: (String) -> Unit,
) {
    FarmEntitySelector(
        atomTag = atomTag,
        title = title,
        options = listOf(FarmSelectorOption(NO_SELECTION, noneLabel)) + options,
        selectedId = selected.ifBlank { NO_SELECTION },
        onSelect = { onSelect(if (it == NO_SELECTION) "" else it) },
        emptyText = "",
        enabled = !busy,
    )
}

/**
 * FOS-POULTRY-023 — Flock Move: move a flock to another house via the governed
 * MovePoultryFlock command. Recorded as a new placement row for the same group.
 */
@Composable
private fun PoultryFlockMove(
    flockGroups: List<FarmSelectorOption>,
    houseOptions: List<FarmSelectorOption>,
    busy: Boolean,
    error: String?,
    onMove: (groupId: String, fromHouseId: String, toHouseId: String, heads: String, day: String) -> Unit,
    onBack: () -> Unit,
) {
    var group by rememberSaveable { mutableStateOf("") }
    var fromHouse by rememberSaveable { mutableStateOf("") }
    var toHouse by rememberSaveable { mutableStateOf("") }
    var heads by rememberSaveable { mutableStateOf("") }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    FarmOperationalPage("FOS-POULTRY-023", "Move flock", "Move a flock to another house.", onBack = onBack) {
        FarmOperationalSection("Flock move") {
            FlockGroupSelector(flockGroups, group, busy) { group = it }
            FarmEntitySelector(
                atomTag = FarmSelectionAtoms.LOCATION_SELECTOR,
                title = "From house",
                options = houseOptions,
                selectedId = fromHouse.ifBlank { null },
                onSelect = { fromHouse = it },
                emptyText = "Create a poultry house first.",
                enabled = !busy,
            )
            FarmEntitySelector(
                atomTag = FarmSelectionAtoms.LOCATION_SELECTOR,
                title = "To house",
                options = houseOptions,
                selectedId = toHouse.ifBlank { null },
                onSelect = { toHouse = it },
                emptyText = "Create a poultry house first.",
                enabled = !busy,
            )
            OutlinedTextField(heads, {
                heads = it
            }, label = { Text("Head count moved") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            OutlinedTextField(day, {
                day = it
            }, label = { Text("Move date") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            Button(
                onClick = { onMove(group, fromHouse, toHouse, heads, day) },
                enabled = !busy && group.isNotBlank() && fromHouse.isNotBlank() && toHouse.isNotBlank() && heads.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Move flock") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

/**
 * FOS-POULTRY-024 — Flock Close-Out: end a flock's production cycle via the governed
 * ClosePoultryFlock command. Shows the flock's lifetime totals before closing.
 */
@Composable
private fun PoultryFlockCloseout(
    flockGroups: List<FarmSelectorOption>,
    flocks: List<PoultryFlockView>,
    busy: Boolean,
    error: String?,
    onClose: (groupId: String, headOut: String, reason: String, day: String) -> Unit,
    onBack: () -> Unit,
) {
    var group by rememberSaveable { mutableStateOf("") }
    var headOut by rememberSaveable { mutableStateOf("") }
    var reason by rememberSaveable { mutableStateOf("") }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    val selected = flocks.firstOrNull { it.groupId == group }
    FarmOperationalPage("FOS-POULTRY-024", "Close out flock", "End a flock's production cycle.", onBack = onBack) {
        FarmOperationalSection("Flock close-out") {
            FlockGroupSelector(flockGroups, group, busy) { group = it }
            selected?.let {
                Text("Placed ${it.placedHeads} head · ${PoultryRecordMath.eggs(it)} eggs · ${PoultryRecordMath.losses(it)} losses")
            }
            OutlinedTextField(headOut, {
                headOut = it
            }, label = { Text("Head count out") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            OutlinedTextField(reason, {
                reason = it
            }, label = { Text("Reason (sold, culled, cycle end)") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            OutlinedTextField(day, {
                day = it
            }, label = { Text("Close-out date") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            Button(
                onClick = { onClose(group, headOut, reason, day) },
                enabled = !busy && group.isNotBlank() && headOut.isNotBlank() && reason.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Close out flock") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

/**
 * FOS-POULTRY-027 — Poultry Report: farm-scoped flock, egg, loss and vaccination
 * totals computed from local records only.
 */
@Composable
private fun PoultryReportScreen(
    records: PoultryRecords,
    onBack: () -> Unit,
) {
    val flocks = records.flocks
    val totalPlaced = flocks.sumOf { it.placedHeads }
    val totalEggs = flocks.sumOf { PoultryRecordMath.eggs(it) }
    val totalLosses = flocks.sumOf { PoultryRecordMath.losses(it) }
    val totalVaccinations = flocks.sumOf { (it.vaccinationCount ?: it.vaccinations.size).toLong() }
    FarmOperationalPage("FOS-POULTRY-027", "Poultry report", "Flock, egg and loss totals for this farm.", onBack = onBack) {
        FarmOperationalSection("Totals") {
            Text("Flocks: ${flocks.size}")
            Text("Placed head: $totalPlaced")
            Text("Eggs recorded: $totalEggs")
            Text("Losses (dead + culls): $totalLosses")
            Text("Vaccinations recorded: $totalVaccinations")
        }
        FarmOperationalSection("Per flock") {
            if (flocks.isEmpty()) Text("No flocks recorded yet.")
            flocks.forEach { flock ->
                Text("${flock.poultryKind} · ${flock.houseLabel} · placed ${flock.placedHeads} · ${PoultryRecordMath.eggs(flock)} eggs · ${PoultryRecordMath.losses(flock)} losses")
            }
        }
    }
}

private const val POULTRY_FORMULARY_SELECTOR = "poultry-formulary-selector"
private const val POULTRY_HATCH_SELECTOR = "poultry-hatch-selector"
private const val NO_SELECTION = "none"
