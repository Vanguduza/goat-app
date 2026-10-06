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
import java.math.BigDecimal
import java.time.LocalDate

data class CattleLotPlacementView(val id: String, val headCount: Int, val epochDay: Long)

data class CattleLotDaysView(val id: String, val daysOnFeed: Int, val epochDay: Long)

/** A recorded close-out; weight and days on feed are null when not recorded. */
data class CattleLotCloseView(val id: String, val headOut: Int, val weightGrams: Long?, val daysOnFeed: Int?, val epochDay: Long)

/** Every recorded feedlot row for one cattle group, newest first. Nothing here writes. */
data class CattleLotView(
    val groupId: String,
    val groupLabel: String,
    val placements: List<CattleLotPlacementView>,
    val daysOnFeed: List<CattleLotDaysView>,
    val closeouts: List<CattleLotCloseView>,
) {
    val headPlaced: Int get() = placements.sumOf { it.headCount }
    val closeoutRecorded: Boolean get() = closeouts.isNotEmpty()
}

private sealed interface LotLoadState {
    data object Loading : LotLoadState
    data class Failed(val message: String) : LotLoadState
    data class Loaded(val lots: List<CattleLotView>) : LotLoadState
}

private fun lotDate(epochDay: Long) = LocalDate.ofEpochDay(epochDay).toString()

private fun lotCount(count: Int) = if (count == 1) "1 lot" else "$count lots"

private fun CattleLotCloseView.label(): String =
    listOfNotNull(
        "$headOut head out",
        weightGrams?.let { BigDecimal.valueOf(it, 3).stripTrailingZeros().toPlainString() + " kg recorded" },
        daysOnFeed?.let { "$it days on feed" },
    ).joinToString(" · ")

@Composable
private fun LotRow(primary: String, secondary: String, tag: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag(tag), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(primary, color = AnimalFarmTheme.colors.mutedInk)
        Text(secondary, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun rememberLots(loadLots: suspend () -> List<CattleLotView>): LotLoadState {
    var state by remember { mutableStateOf<LotLoadState>(LotLoadState.Loading) }
    LaunchedEffect(Unit) {
        state = runCatching { loadLots() }.fold({ LotLoadState.Loaded(it) }, { LotLoadState.Failed(it.message ?: "Records could not be loaded") })
    }
    return state
}

/** FOS-CATTLE-025 — recorded feedlot lots; counts only, no performance or conversion is derived. */
@Composable
internal fun CattleBeefDashboardScreen(loadLots: suspend () -> List<CattleLotView>, onBack: () -> Unit) {
    val state = rememberLots(loadLots)
    FarmOperationalPage("FOS-CATTLE-025", "Beef dashboard", "Feedlot lots recorded on this device. No weight gain or feed conversion is derived.", FarmVisualClass.I2, onBack) {
        when (state) {
            LotLoadState.Loading -> Text("Loading records")
            is LotLoadState.Failed -> AnimalFarmWarningSurface { Text(state.message) }
            is LotLoadState.Loaded -> {
                val lots = state.lots
                if (lots.isEmpty()) {
                    AnimalFarmEmptyState("No feedlot lots recorded on this device.")
                    return@FarmOperationalPage
                }
                val open = lots.filterNot { it.closeoutRecorded }
                FarmOperationalSection("Lots") {
                    LotRow("Without a close-out recorded", "${lotCount(open.size)} · ${open.sumOf { it.headPlaced }} head placed", "beef-open")
                    LotRow("With a close-out recorded", "${lotCount(lots.size - open.size)} · ${lots.sumOf { lot -> lot.closeouts.sumOf { it.headOut } }} head out", "beef-closed")
                }
                FarmOperationalSection("Lots · ${lots.size}") {
                    lots.forEachIndexed { index, lot ->
                        if (index > 0) HorizontalDivider()
                        val latestDays = lot.daysOnFeed.firstOrNull()?.let { " · ${it.daysOnFeed} days on feed at ${lotDate(it.epochDay)}" } ?: ""
                        LotRow(
                            lot.groupLabel,
                            "${lot.headPlaced} head placed" + latestDays + if (lot.closeoutRecorded) " · closed out" else "",
                            "beef-lot:${lot.groupId}",
                        )
                    }
                }
            }
        }
    }
}

/** FOS-CATTLE-027 — one feedlot lot: placements, days-on-feed records and close-out as recorded. */
@Composable
internal fun CattleLotDetailScreen(loadLots: suspend () -> List<CattleLotView>, onBack: () -> Unit) {
    val state = rememberLots(loadLots)
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    FarmOperationalPage("FOS-CATTLE-027", "Feedlot lot detail", "Placements, days on feed and close-out recorded for one lot.", FarmVisualClass.I3, onBack) {
        when (state) {
            LotLoadState.Loading -> Text("Loading records")
            is LotLoadState.Failed -> AnimalFarmWarningSurface { Text(state.message) }
            is LotLoadState.Loaded -> {
                val lots = state.lots
                if (lots.isEmpty()) {
                    AnimalFarmEmptyState("No feedlot lots recorded on this device.")
                    return@FarmOperationalPage
                }
                val lot = lots.firstOrNull { it.groupId == selectedId } ?: lots.first()
                FarmOperationalSection("Lots") {
                    lots.forEach { option ->
                        val selected = option.groupId == lot.groupId
                        TextButton(
                            onClick = { selectedId = option.groupId },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = AnimalFarmTheme.minimumTouchDp.dp)
                                .semantics { this.selected = selected }
                                .testTag("lot-option:${option.groupId}"),
                        ) { Text(option.groupLabel, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) }
                    }
                }
                FarmOperationalSection(lot.groupLabel) {
                    LotRow("Head placed, all placements", lot.headPlaced.toString(), "lot-head-placed")
                    lot.placements.lastOrNull()?.let { LotRow("First placement", lotDate(it.epochDay), "lot-first-placement") }
                    LotRow(
                        "Close-out",
                        lot.closeouts.firstOrNull()?.let { "${lotDate(it.epochDay)} · ${it.label()}" } ?: "No close-out recorded",
                        "lot-closeout",
                    )
                }
                FarmOperationalSection("Placements · ${lot.placements.size}") {
                    lot.placements.forEach { LotRow(lotDate(it.epochDay), "${it.headCount} head", "lot-placement:${it.id}") }
                }
                FarmOperationalSection("Days on feed · ${lot.daysOnFeed.size}") {
                    if (lot.daysOnFeed.isEmpty()) Text("No days-on-feed records for this lot.", color = AnimalFarmTheme.colors.mutedInk)
                    lot.daysOnFeed.forEach { LotRow(lotDate(it.epochDay), "${it.daysOnFeed} days on feed", "lot-days:${it.id}") }
                }
            }
        }
    }
}
