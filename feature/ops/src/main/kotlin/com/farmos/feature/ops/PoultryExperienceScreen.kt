package com.farmos.feature.ops

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
import com.farmos.core.design.FarmSelectionAtoms
import com.farmos.core.design.FarmSelectorOption
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
    FLOCK_PROFILE,
    HOUSE_DETAIL,
    EGG_PRODUCTION,
    FEED,
    INCUBATION_BATCH,
    BIOSECURITY_RECORDS,
    FLOCK_HEALTH,
    FLOCK_TIMELINE,
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
    var page by remember { mutableStateOf(PoultryPage.DASHBOARD) }
    val home = { page = PoultryPage.DASHBOARD }
    when (page) {
        PoultryPage.DASHBOARD -> PoultryDashboard(enabledKinds, houses, records.flockCount, flockDays, hatches, error, { page = it }, onBack)
        PoultryPage.KINDS -> PoultryKinds(enabledKinds, busy, error, onEnableKind, home)
        PoultryPage.HOUSES -> PoultryHouses(houses, busy, error, onCreateHouse, home)
        PoultryPage.FLOCKS -> PoultryRows("FOS-POULTRY-004", "Flocks", placements, "No flock placements yet", error, home)
        PoultryPage.PLACEMENT -> PoultryPlacement(flockGroups, houseOptions, busy, error, onPlaceFlock, home)
        PoultryPage.DAILY -> PoultryDaily(flockDays, flockGroups, busy, error, onRecordFlockDay, home)
        PoultryPage.HATCHERY -> PoultryRows("FOS-POULTRY-017", "Hatchery", hatches, "No hatch batches yet", error, home, FarmVisualClass.I2)
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
    }
}

/** FOS-POULTRY-001 — illustrated flock/house operating dashboard. */
@Composable
private fun PoultryDashboard(
    enabledKinds: List<String>,
    houses: List<String>,
    flockCount: Int?,
    flockDays: List<String>,
    hatches: List<String>,
    error: String?,
    onOpen: (PoultryPage) -> Unit,
    onBack: () -> Unit,
) {
    AnimalFarmCanvas {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(FosDimens.SectionGap),
        ) {
            AnimalFarmModuleHeader(
                title = "Poultry",
                subtitle = "Houses, flocks and hatchery",
                family = AnimalFarmFamily.POULTRY,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PoultryMetric("Kinds", enabledKinds.size, Modifier.weight(1f))
                PoultryMetric("Houses", houses.size, Modifier.weight(1f))
                // Distinct flocks from the exhaustive placement aggregate, not the bounded placement rows.
                PoultryMetric("Flocks", flockCount, Modifier.weight(1f))
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
            PoultryAction("Flock profiles", "Placement, daily records and losses per flock") { onOpen(PoultryPage.FLOCK_PROFILE) }
            PoultryAction("House details", "Placements and biosecurity per house") { onOpen(PoultryPage.HOUSE_DETAIL) }
            PoultryAction("Egg production", "Eggs recorded per flock and day") { onOpen(PoultryPage.EGG_PRODUCTION) }
            PoultryAction("Feed", "Feed recorded per flock and day") { onOpen(PoultryPage.FEED) }
            PoultryAction("Incubation batches", "Egg set, candling and hatch as recorded") { onOpen(PoultryPage.INCUBATION_BATCH) }
            PoultryAction("Biosecurity records", "Recorded walks and findings") { onOpen(PoultryPage.BIOSECURITY_RECORDS) }
            PoultryAction("Flock health", "Recorded losses, vaccinations and walks for one flock") { onOpen(PoultryPage.FLOCK_HEALTH) }
            PoultryAction("Flock timeline", "Every recorded event for one flock") { onOpen(PoultryPage.FLOCK_TIMELINE) }
            if (flockDays.isEmpty()) Text("No daily flock records yet.")
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = onBack) { Text("Farm home") }
        }
    }
}

@Composable
private fun PoultryMetric(
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
private fun PoultryAction(
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
    flockGroups: List<FarmSelectorOption>,
    houseOptions: List<FarmSelectorOption>,
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
private fun PoultryDaily(
    rows: List<String>,
    flockGroups: List<FarmSelectorOption>,
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
    var hatch by remember { mutableStateOf("") }
    var fertile by remember { mutableStateOf("") }
    var infertile by remember { mutableStateOf("0") }
    var midDead by remember { mutableStateOf("0") }
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
private fun PoultryVaccination(
    rows: List<String>,
    flockGroups: List<FarmSelectorOption>,
    poultryFormulary: List<FarmSelectorOption>,
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

private const val POULTRY_FORMULARY_SELECTOR = "poultry-formulary-selector"
private const val POULTRY_HATCH_SELECTOR = "poultry-hatch-selector"
private const val NO_SELECTION = "none"
