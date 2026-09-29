package com.farmos.feature.ops

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farmos.core.design.AnimalFarmEmptyState
import com.farmos.core.design.AnimalFarmTheme
import com.farmos.core.design.AnimalFarmWarningSurface
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.design.FarmVisualClass
import java.time.LocalDate

/**
 * Every record for one flock, read exhaustively from local farm-scoped tables. [hatches] are the
 * incubation batches recorded as placed into this flock. Nothing here writes.
 */
data class PoultryFlockRecords(
    val placements: List<PoultryPlacementView> = emptyList(),
    val days: List<PoultryDayView> = emptyList(),
    val vaccinations: List<PoultryVaccinationView> = emptyList(),
    val walks: List<PoultryWalkView> = emptyList(),
    val hatches: List<PoultryHatchView> = emptyList(),
)

private sealed interface FlockLoadState {
    data object Loading : FlockLoadState
    data class Failed(val message: String) : FlockLoadState
    data class Loaded(val records: PoultryFlockRecords) : FlockLoadState
}

private fun flockDay(epochDay: Long) = LocalDate.ofEpochDay(epochDay).toString()

@Composable
private fun FlockRow(primary: String, secondary: String, tag: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag(tag), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(primary, color = AnimalFarmTheme.colors.mutedInk)
        Text(secondary, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * FOS-POULTRY-025 (health) and FOS-POULTRY-026 (timeline) for one selected flock, loaded by
 * [loadFlock] exhaustively for that flock only.
 */
@Composable
internal fun PoultryFlockRecordScreen(
    timeline: Boolean,
    flocks: List<PoultryFlockView>,
    loadFlock: suspend (String) -> PoultryFlockRecords,
    onBack: () -> Unit,
) {
    var selectedId by rememberSaveable { mutableStateOf(flocks.firstOrNull()?.groupId) }
    var state by remember(selectedId) { mutableStateOf<FlockLoadState>(FlockLoadState.Loading) }
    LaunchedEffect(selectedId) {
        val id = selectedId ?: return@LaunchedEffect
        state = runCatching { loadFlock(id) }.fold({ FlockLoadState.Loaded(it) }, { FlockLoadState.Failed(it.message ?: "Records could not be loaded") })
    }
    val screenId = if (timeline) "FOS-POULTRY-026" else "FOS-POULTRY-025"
    val title = if (timeline) "Flock timeline" else "Poultry health"
    val subtitle = if (timeline) {
        "Every recorded event for one flock, newest first."
    } else {
        "Recorded losses, vaccinations and biosecurity for one flock. Records only; no diagnosis or dosing."
    }
    FarmOperationalPage(screenId, title, subtitle, if (timeline) FarmVisualClass.I3 else FarmVisualClass.I4, onBack) {
        if (flocks.isEmpty()) {
            AnimalFarmEmptyState("No flocks placed on this device.")
            return@FarmOperationalPage
        }
        FarmOperationalSection("Flocks") {
            flocks.forEach { flock ->
                val selected = flock.groupId == selectedId
                TextButton(
                    onClick = { selectedId = flock.groupId },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = AnimalFarmTheme.minimumTouchDp.dp)
                        .semantics { this.selected = selected }
                        .testTag("flock-option:${flock.groupId}"),
                ) { Text("${flock.groupId} · ${flock.poultryKind}", fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) }
            }
        }
        when (val current = state) {
            FlockLoadState.Loading -> Text("Loading records")
            is FlockLoadState.Failed -> AnimalFarmWarningSurface { Text(current.message) }
            is FlockLoadState.Loaded -> if (timeline) FlockTimelineContent(current.records) else FlockHealthContent(current.records)
        }
    }
}

@Composable
private fun FlockHealthContent(records: PoultryFlockRecords) {
    val mixed = records.walks.count { it.mixedSpecies }
    if (mixed > 0) {
        AnimalFarmWarningSurface(Modifier.testTag("flock-health-mixed")) {
            Text("Mixed species recorded on $mixed walk(s)", fontWeight = FontWeight.Bold)
        }
    }
    FarmOperationalSection("Recorded losses") {
        FlockRow("Deaths recorded", records.days.sumOf { it.dead.toLong() }.toString(), "flock-health-dead")
        FlockRow("Culls recorded", records.days.sumOf { it.culls.toLong() }.toString(), "flock-health-culls")
        records.days.filter { it.dead + it.culls > 0 }.maxByOrNull { it.epochDay }?.let {
            FlockRow("Latest loss recorded", "${flockDay(it.epochDay)} · ${it.dead} dead · ${it.culls} culled", "flock-health-latest-loss")
        }
    }
    FarmOperationalSection("Vaccinations · ${records.vaccinations.size}") {
        if (records.vaccinations.isEmpty()) Text("No vaccinations recorded for this flock.", color = AnimalFarmTheme.colors.mutedInk)
        records.vaccinations.forEach { FlockRow(flockDay(it.epochDay), it.productLabel, "flock-health-vaccination:${it.id}") }
    }
    FarmOperationalSection("Biosecurity walks · ${records.walks.size}") {
        if (records.walks.isEmpty()) Text("No biosecurity walks recorded for this flock.", color = AnimalFarmTheme.colors.mutedInk)
        records.walks.forEach {
            FlockRow(flockDay(it.epochDay) + if (it.mixedSpecies) " · mixed species" else "", it.findings, "flock-health-walk:${it.id}")
        }
    }
}

@Composable
private fun FlockTimelineContent(records: PoultryFlockRecords) {
    val events = buildList {
        records.placements.forEach { add(Triple(it.epochDay, "placement:${it.id}", "${flockDay(it.epochDay)} · Placed" to "${it.headCount} head · ${it.poultryKind}")) }
        records.days.forEachIndexed { index, day ->
            add(Triple(day.epochDay, "day:${day.epochDay}:$index", "${flockDay(day.epochDay)} · Daily record" to "${day.eggs} eggs · ${day.dead} dead · ${day.culls} culled · ${PoultryRecordMath.kg(day.feedGrams)} feed"))
        }
        records.vaccinations.forEach { add(Triple(it.epochDay, "vaccination:${it.id}", "${flockDay(it.epochDay)} · Vaccination" to it.productLabel)) }
        records.walks.forEach { add(Triple(it.epochDay, "walk:${it.id}", "${flockDay(it.epochDay)} · Biosecurity walk" to it.findings)) }
        records.hatches.forEach {
            add(Triple(it.setEpochDay, "hatch:${it.id}", "${flockDay(it.setEpochDay)} · Eggs set for this flock" to "${it.eggsSet} eggs · " + (it.hatched?.let { h -> "$h hatched" } ?: "no hatch result recorded")))
        }
    }.sortedWith(compareByDescending<Triple<Long, String, Pair<String, String>>> { it.first }.thenBy { it.second })
    if (events.isEmpty()) {
        AnimalFarmEmptyState("No records for this flock on this device.")
        return
    }
    FarmOperationalSection("Events · ${events.size}") {
        events.forEachIndexed { index, (_, key, text) ->
            if (index > 0) HorizontalDivider()
            FlockRow(text.first, text.second, "flock-event:$key")
        }
    }
}
