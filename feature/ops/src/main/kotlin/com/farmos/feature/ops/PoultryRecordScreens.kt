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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import java.math.BigDecimal
import java.time.LocalDate

data class PoultryDayView(val epochDay: Long, val eggs: Int, val dead: Int, val culls: Int, val feedGrams: Long)

data class PoultryVaccinationView(val id: String, val epochDay: Long, val productLabel: String)

data class PoultryFlockView(
    val groupId: String,
    val poultryKind: String,
    val houseLabel: String,
    /** Sum of recorded placements for this flock. */
    val placedHeads: Long,
    val firstPlacedEpochDay: Long,
    val days: List<PoultryDayView>,
    val vaccinations: List<PoultryVaccinationView>,
    /** Exhaustive count of this flock's vaccinations; [vaccinations] may be only the latest. */
    val vaccinationCount: Int? = null,
)

data class PoultryPlacementView(val id: String, val groupId: String, val poultryKind: String, val headCount: Int, val epochDay: Long)

data class PoultryWalkView(val id: String, val epochDay: Long, val subject: String, val findings: String, val mixedSpecies: Boolean)

data class PoultryHouseView(
    val id: String,
    val code: String,
    val houseKind: String,
    val poultryKind: String,
    val placements: List<PoultryPlacementView>,
    val walks: List<PoultryWalkView>,
    /** Exhaustive counts for this house; the lists above may be only the latest rows. */
    val placementCount: Int? = null,
    val walkCount: Int? = null,
)

data class PoultryHatchView(
    val id: String,
    val poultryKind: String,
    val setEpochDay: Long,
    val eggsSet: Int,
    val incubationDays: Int,
    val status: String,
    val location: String,
    val fertile: Int?,
    val infertile: Int?,
    val midDead: Int?,
    val hatched: Int?,
    val culls: Int?,
    val placementGroupId: String?,
)

/** Local, farm-scoped poultry records for the read-only record pages. Nothing here writes. */
data class PoultryRecords(
    val flocks: List<PoultryFlockView> = emptyList(),
    val houses: List<PoultryHouseView> = emptyList(),
    val hatches: List<PoultryHatchView> = emptyList(),
    val walks: List<PoultryWalkView> = emptyList(),
    /** Exhaustive farm-scoped walk counts; [walks] may be only the latest rows. */
    val walkCount: Int? = null,
    val mixedSpeciesWalkCount: Int? = null,
    /** Exhaustive count of distinct flocks with a recorded placement; null until loaded. */
    val flockCount: Int? = null,
)

internal object PoultryRecordMath {
    fun eggs(flock: PoultryFlockView): Long = flock.days.sumOf { it.eggs.toLong() }

    fun losses(flock: PoultryFlockView): Long = flock.days.sumOf { (it.dead + it.culls).toLong() }

    /** Placed heads minus recorded deaths and culls; not a physical census. */
    fun placedMinusLosses(flock: PoultryFlockView): Long = flock.placedHeads - losses(flock)

    fun kg(grams: Long): String = BigDecimal.valueOf(grams, 3).stripTrailingZeros().toPlainString() + " kg"
}

@Composable
private fun PoultryRecordRow(primary: String, secondary: String, tag: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag(tag), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(primary, color = AnimalFarmTheme.colors.mutedInk)
        Text(secondary, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PoultrySwitcher(title: String, options: List<Pair<String, String>>, selectedId: String?, onSelect: (String) -> Unit) {
    FarmOperationalSection(title) {
        options.forEach { (id, label) ->
            val selected = id == selectedId
            TextButton(
                onClick = { onSelect(id) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = AnimalFarmTheme.minimumTouchDp.dp)
                    .semantics { this.selected = selected }
                    .testTag("poultry-option:$id"),
            ) { Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) }
        }
    }
}

/** FOS-POULTRY-005 — one flock's placement, daily records, losses and vaccinations. */
@Composable
internal fun PoultryFlockProfileScreen(records: PoultryRecords, onBack: () -> Unit) {
    var selectedId by rememberSaveable { mutableStateOf(records.flocks.firstOrNull()?.groupId) }
    FarmOperationalPage("FOS-POULTRY-005", "Flock profile", "Placement, daily records and losses for one flock.", onBack = onBack) {
        if (records.flocks.isEmpty()) {
            AnimalFarmEmptyState("No flocks placed on this device.")
            return@FarmOperationalPage
        }
        PoultrySwitcher("Flocks", records.flocks.map { it.groupId to "${it.groupId} · ${it.poultryKind}" }, selectedId) { selectedId = it }
        val flock = records.flocks.firstOrNull { it.groupId == selectedId } ?: return@FarmOperationalPage
        FarmOperationalSection("Flock ${flock.groupId}") {
            PoultryRecordRow("House", flock.houseLabel, "poultry-flock-house")
            PoultryRecordRow("Placed, all placements", "${flock.placedHeads} head · first placed ${LocalDate.ofEpochDay(flock.firstPlacedEpochDay)}", "poultry-flock-placed")
            PoultryRecordRow("Recorded deaths and culls", PoultryRecordMath.losses(flock).toString(), "poultry-flock-losses")
            PoultryRecordRow("Placed minus recorded losses", PoultryRecordMath.placedMinusLosses(flock).toString(), "poultry-flock-remaining")
            PoultryRecordRow("Eggs recorded", PoultryRecordMath.eggs(flock).toString(), "poultry-flock-eggs")
            PoultryRecordRow("Feed recorded", PoultryRecordMath.kg(flock.days.sumOf { it.feedGrams }), "poultry-flock-feed")
        }
        FarmOperationalSection(recordListTitle("Vaccinations", flock.vaccinations.size, flock.vaccinationCount)) {
            if (flock.vaccinations.isEmpty()) Text("No vaccinations recorded for this flock.", color = AnimalFarmTheme.colors.mutedInk)
            flock.vaccinations.forEach { PoultryRecordRow(LocalDate.ofEpochDay(it.epochDay).toString(), it.productLabel, "poultry-flock-vaccination:${it.id}") }
        }
    }
}

/** FOS-POULTRY-007 — one house, the flocks placed in it and its biosecurity walks. */
@Composable
internal fun PoultryHouseDetailScreen(records: PoultryRecords, onBack: () -> Unit) {
    var selectedId by rememberSaveable { mutableStateOf(records.houses.firstOrNull()?.id) }
    FarmOperationalPage("FOS-POULTRY-007", "House detail", "Placements and biosecurity for one house.", onBack = onBack) {
        if (records.houses.isEmpty()) {
            AnimalFarmEmptyState("No poultry houses on this device.")
            return@FarmOperationalPage
        }
        PoultrySwitcher("Houses", records.houses.map { it.id to "${it.code} · ${it.poultryKind}" }, selectedId) { selectedId = it }
        val house = records.houses.firstOrNull { it.id == selectedId } ?: return@FarmOperationalPage
        FarmOperationalSection("House ${house.code}") {
            PoultryRecordRow("Housing", house.houseKind, "poultry-house-kind")
            PoultryRecordRow("Poultry kind", house.poultryKind, "poultry-house-poultry-kind")
        }
        FarmOperationalSection(recordListTitle("Placements", house.placements.size, house.placementCount)) {
            if (house.placements.isEmpty()) Text("No flocks placed in this house.", color = AnimalFarmTheme.colors.mutedInk)
            house.placements.forEach {
                PoultryRecordRow(LocalDate.ofEpochDay(it.epochDay).toString() + " · " + it.groupId, "${it.headCount} head · ${it.poultryKind}", "poultry-house-placement:${it.id}")
            }
        }
        FarmOperationalSection(recordListTitle("Biosecurity walks", house.walks.size, house.walkCount)) {
            if (house.walks.isEmpty()) Text("No biosecurity walks recorded for this house.", color = AnimalFarmTheme.colors.mutedInk)
            house.walks.forEach { PoultryRecordRow(LocalDate.ofEpochDay(it.epochDay).toString(), it.findings, "poultry-house-walk:${it.id}") }
        }
    }
}

/** FOS-POULTRY-010 — recorded eggs per flock and day. No lay-rate percentage is derived. */
@Composable
internal fun PoultryEggProductionScreen(records: PoultryRecords, onBack: () -> Unit) {
    FarmOperationalPage("FOS-POULTRY-010", "Egg production", "Eggs recorded per flock and day.", onBack = onBack) {
        val laying = records.flocks.filter { flock -> flock.days.any { it.eggs > 0 } }
        if (laying.isEmpty()) {
            AnimalFarmEmptyState("No eggs recorded on this device.")
            return@FarmOperationalPage
        }
        laying.forEach { flock ->
            FarmOperationalSection("${flock.groupId} · ${flock.poultryKind} · ${PoultryRecordMath.eggs(flock)} eggs") {
                flock.days.filter { it.eggs > 0 }.forEachIndexed { index, day ->
                    if (index > 0) HorizontalDivider()
                    PoultryRecordRow(LocalDate.ofEpochDay(day.epochDay).toString(), "${day.eggs} eggs", "poultry-eggs:${flock.groupId}:${day.epochDay}")
                }
            }
        }
    }
}

/**
 * FOS-POULTRY-012 — recorded feed per flock and day, totalled per flock. No feed conversion ratio
 * is derived: bird and egg weights are not recorded, so any ratio would be invented.
 */
@Composable
internal fun PoultryFeedScreen(records: PoultryRecords, onBack: () -> Unit) {
    FarmOperationalPage("FOS-POULTRY-012", "Feed", "Feed recorded per flock and day. No conversion ratio is derived.", onBack = onBack) {
        val fed = records.flocks.filter { flock -> flock.days.any { it.feedGrams > 0 } }
        if (fed.isEmpty()) {
            AnimalFarmEmptyState("No feed recorded on this device.")
            return@FarmOperationalPage
        }
        Text(
            "Bird and egg weights are not recorded, so no feed conversion ratio is shown.",
            color = AnimalFarmTheme.colors.mutedInk,
            modifier = Modifier.testTag("poultry-feed-no-fcr"),
        )
        fed.forEach { flock ->
            FarmOperationalSection("${flock.groupId} · ${flock.poultryKind} · ${PoultryRecordMath.kg(flock.days.sumOf { it.feedGrams })}") {
                flock.days.filter { it.feedGrams > 0 }.forEachIndexed { index, day ->
                    if (index > 0) HorizontalDivider()
                    PoultryRecordRow(LocalDate.ofEpochDay(day.epochDay).toString(), PoultryRecordMath.kg(day.feedGrams), "poultry-feed:${flock.groupId}:${day.epochDay}")
                }
            }
        }
    }
}

/** FOS-POULTRY-019 — one incubation batch as recorded. */
@Composable
internal fun PoultryIncubationBatchScreen(records: PoultryRecords, onBack: () -> Unit) {
    var selectedId by rememberSaveable { mutableStateOf(records.hatches.firstOrNull()?.id) }
    FarmOperationalPage("FOS-POULTRY-019", "Incubation batch", "Egg set, candling and hatch outcome as recorded.", FarmVisualClass.I3, onBack) {
        if (records.hatches.isEmpty()) {
            AnimalFarmEmptyState("No incubation batches on this device.")
            return@FarmOperationalPage
        }
        PoultrySwitcher("Batches", records.hatches.map { it.id to "${LocalDate.ofEpochDay(it.setEpochDay)} · ${it.poultryKind} · ${it.eggsSet} eggs" }, selectedId) { selectedId = it }
        val batch = records.hatches.firstOrNull { it.id == selectedId } ?: return@FarmOperationalPage
        FarmOperationalSection("Batch") {
            PoultryRecordRow("Status", batch.status, "poultry-batch-status")
            PoultryRecordRow("Set", "${batch.eggsSet} eggs · ${LocalDate.ofEpochDay(batch.setEpochDay)}", "poultry-batch-set")
            PoultryRecordRow("Recorded incubation period", "${batch.incubationDays} days", "poultry-batch-period")
            PoultryRecordRow("Location", batch.location, "poultry-batch-location")
        }
        FarmOperationalSection("Candling") {
            if (batch.fertile == null && batch.infertile == null && batch.midDead == null) {
                Text("Not candled yet.", color = AnimalFarmTheme.colors.mutedInk)
            } else {
                PoultryRecordRow("Fertile / infertile / mid-dead", "${batch.fertile ?: "—"} / ${batch.infertile ?: "—"} / ${batch.midDead ?: "—"}", "poultry-batch-candling")
            }
        }
        FarmOperationalSection("Hatch") {
            if (batch.hatched == null) {
                Text("No hatch result recorded.", color = AnimalFarmTheme.colors.mutedInk)
            } else {
                PoultryRecordRow("Hatched / culled", "${batch.hatched} / ${batch.culls ?: 0}", "poultry-batch-hatch")
                batch.placementGroupId?.let { PoultryRecordRow("Placed as flock", it, "poultry-batch-placement") }
            }
        }
    }
}

/** FOS-POULTRY-015 — recorded biosecurity walks, newest first. */
@Composable
internal fun PoultryBiosecurityDashboardScreen(records: PoultryRecords, onBack: () -> Unit) {
    FarmOperationalPage("FOS-POULTRY-015", "Biosecurity records", "Biosecurity walks recorded on this device.", FarmVisualClass.I4, onBack) {
        if (records.walks.isEmpty()) {
            AnimalFarmEmptyState("No biosecurity walks recorded on this device.")
            return@FarmOperationalPage
        }
        val mixed = records.mixedSpeciesWalkCount ?: records.walks.count { it.mixedSpecies }
        if (mixed > 0) {
            AnimalFarmWarningSurface(Modifier.testTag("poultry-biosecurity-mixed")) {
                Text("Mixed species recorded on $mixed walk(s)", fontWeight = FontWeight.Bold)
            }
        }
        FarmOperationalSection(recordListTitle("Walks", records.walks.size, records.walkCount)) {
            records.walks.forEachIndexed { index, walk ->
                if (index > 0) HorizontalDivider()
                PoultryRecordRow(
                    "${LocalDate.ofEpochDay(walk.epochDay)} · ${walk.subject}" + if (walk.mixedSpecies) " · mixed species" else "",
                    walk.findings,
                    "poultry-walk:${walk.id}",
                )
            }
        }
    }
}
