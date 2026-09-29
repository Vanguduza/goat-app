package com.farmos.feature.ops

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.farmos.core.design.AnimalFarmEmptyState
import com.farmos.core.design.AnimalFarmTheme
import com.farmos.core.design.AnimalFarmWarningSurface
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.design.FarmVisualClass
import java.math.BigDecimal
import java.time.LocalDate

data class CattleMilkRow(val id: String, val epochDay: Long, val litresMilli: Long)

data class CattleSccRow(val id: String, val epochDay: Long, val cellsPerMl: Int, val dimDays: Int?)

data class CattleWithdrawalRow(val id: String, val product: String, val windowKind: String, val endsEpochDay: Long)

data class CattleObservationRow(val id: String, val epochDay: Long, val signs: String, val redFlag: Boolean)

/** One recorded official movement; places are shown exactly as recorded, or as not recorded. */
data class CattleMovementRow(val id: String, val epochDay: Long, val direction: String, val fromPlace: String?, val toPlace: String?)

/** One recorded identifier for the animal; inactive identifiers stay listed as history. */
data class CattleIdentifierRow(val id: String, val type: String, val value: String, val active: Boolean, val assignedEpochDay: Long)

/** One dated entry on the animal timeline; [kind] is the record family, [summary] its recorded facts. */
data class CattleTimelineRow(val id: String, val epochDay: Long, val kind: String, val summary: String)

/** Records for one animal, read from local farm-scoped tables. Nothing here writes. */
data class CattleRecords(
    val milk: List<CattleMilkRow> = emptyList(),
    val scc: List<CattleSccRow> = emptyList(),
    val withdrawals: List<CattleWithdrawalRow> = emptyList(),
    val observations: List<CattleObservationRow> = emptyList(),
    val treatmentCount: Int = 0,
    /** Latest recorded body condition as "score (scale)", or null. */
    val latestBcs: String? = null,
    val latestLocomotion: Int? = null,
    val timeline: List<CattleTimelineRow> = emptyList(),
    /** Recorded weights, oldest first. */
    val weights: List<WeightView> = emptyList(),
    /** Every recorded official movement, newest first. */
    val movements: List<CattleMovementRow> = emptyList(),
    /** Every recorded identifier, active first. */
    val identifiers: List<CattleIdentifierRow> = emptyList(),
)

private sealed interface CattleRecordsState {
    data object NoSelection : CattleRecordsState
    data object Loading : CattleRecordsState
    data class Failed(val message: String) : CattleRecordsState
    data class Loaded(val records: CattleRecords) : CattleRecordsState
}

internal enum class CattleRecordPage(val screenId: String, val title: String, val subtitle: String) {
    LACTATION_HISTORY("FOS-CATTLE-020", "Lactation history", "Milk recorded for the selected animal."),
    SCC_HISTORY("FOS-CATTLE-022", "SCC history", "Somatic cell counts recorded for the selected animal."),
    HEALTH_SUMMARY("FOS-CATTLE-033", "Cattle health summary", "Recorded health for the selected animal. Records only; no diagnosis or dosing."),
    TIMELINE("FOS-CATTLE-035", "Cattle timeline", "Every recorded event for the selected animal, newest first."),
    GROWTH_HISTORY("FOS-CATTLE-007", "Growth history", "Weights recorded for the selected animal. No growth rate or target is derived."),
    MOVEMENTS("FOS-CATTLE-031", "Official movement record", "Identifiers and on, off and transfer movements recorded for the selected animal."),
}

/** Recorded movement direction as captured on FOS-CATTLE-030; other stored values are shown as stored. */
fun cattleMovementDirection(direction: String): String = when (direction) {
    "on" -> "Moved on"
    "off" -> "Moved off"
    "transfer" -> "Transfer"
    else -> direction
}

internal fun cattleLitres(milli: Long): String = BigDecimal.valueOf(milli, 3).stripTrailingZeros().toPlainString() + " L"

@Composable
internal fun CattleRecordPageHost(
    page: CattleRecordPage,
    selectedId: String?,
    today: LocalDate,
    loadRecords: suspend (String) -> CattleRecords,
    onBack: () -> Unit,
) {
    var state by remember(selectedId) {
        mutableStateOf<CattleRecordsState>(if (selectedId == null) CattleRecordsState.NoSelection else CattleRecordsState.Loading)
    }
    LaunchedEffect(selectedId) {
        val id = selectedId ?: return@LaunchedEffect
        state = runCatching { loadRecords(id) }
            .fold({ CattleRecordsState.Loaded(it) }, { CattleRecordsState.Failed(it.message ?: "Records could not be loaded") })
    }
    val visualClass = if (page == CattleRecordPage.HEALTH_SUMMARY) FarmVisualClass.I4 else FarmVisualClass.I3
    FarmOperationalPage(page.screenId, page.title, page.subtitle, visualClass, onBack) {
        when (val current = state) {
            CattleRecordsState.NoSelection -> AnimalFarmEmptyState("Select an animal from the cattle herd first.")
            CattleRecordsState.Loading -> Text("Loading records")
            is CattleRecordsState.Failed -> AnimalFarmWarningSurface { Text(current.message) }
            is CattleRecordsState.Loaded -> when (page) {
                CattleRecordPage.LACTATION_HISTORY -> CattleLactationContent(current.records)
                CattleRecordPage.SCC_HISTORY -> CattleSccContent(current.records)
                CattleRecordPage.HEALTH_SUMMARY -> CattleHealthContent(current.records, today)
                CattleRecordPage.TIMELINE -> CattleTimelineContent(current.records)
                CattleRecordPage.GROWTH_HISTORY -> WeightHistoryContent(current.records.weights, "No weights recorded for this animal on this device.")
                CattleRecordPage.MOVEMENTS -> CattleMovementContent(current.records)
            }
        }
    }
}

@Composable
private fun CattleRow(primary: String, secondary: String, tag: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag(tag), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(primary, color = AnimalFarmTheme.colors.mutedInk)
        Text(secondary, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

/** FOS-CATTLE-020 */
@Composable
private fun CattleLactationContent(records: CattleRecords) {
    if (records.milk.isEmpty()) {
        AnimalFarmEmptyState("No milk recorded for this animal on this device.")
        return
    }
    FarmOperationalSection("Summary") {
        CattleRow("Records", records.milk.size.toString(), "cattle-milk-count")
        CattleRow("Total recorded", cattleLitres(records.milk.sumOf { it.litresMilli }), "cattle-milk-total")
    }
    FarmOperationalSection("Milk records") {
        records.milk.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider()
            CattleRow(LocalDate.ofEpochDay(row.epochDay).toString(), cattleLitres(row.litresMilli), "cattle-milk:${row.id}")
        }
    }
}

/** FOS-CATTLE-022 */
@Composable
private fun CattleSccContent(records: CattleRecords) {
    if (records.scc.isEmpty()) {
        AnimalFarmEmptyState("No SCC results recorded for this animal on this device.")
        return
    }
    FarmOperationalSection("SCC results") {
        records.scc.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider()
            CattleRow(
                LocalDate.ofEpochDay(row.epochDay).toString(),
                "%,d cells/mL".format(row.cellsPerMl) + (row.dimDays?.let { " · DIM $it" } ?: ""),
                "cattle-scc:${row.id}",
            )
        }
    }
}

/** FOS-CATTLE-033 */
@Composable
private fun CattleHealthContent(records: CattleRecords, today: LocalDate) {
    val active = records.withdrawals.filter { it.endsEpochDay >= today.toEpochDay() }.sortedBy { it.endsEpochDay }
    if (active.isNotEmpty()) {
        AnimalFarmWarningSurface(Modifier.testTag("cattle-active-withdrawal")) {
            Text("Active withdrawal", fontWeight = FontWeight.Bold)
            active.forEach { Text("${it.windowKind} · ${it.product} · until ${LocalDate.ofEpochDay(it.endsEpochDay)}") }
        }
    }
    FarmOperationalSection("Recorded health") {
        CattleRow("Active withdrawals", active.size.toString(), "cattle-health-active")
        CattleRow("Treatments recorded", records.treatmentCount.toString(), "cattle-health-treatments")
        CattleRow("Observations recorded", records.observations.size.toString(), "cattle-health-observations")
        records.latestBcs?.let { CattleRow("Latest BCS", it, "cattle-health-bcs") }
        records.latestLocomotion?.let { CattleRow("Latest locomotion score", it.toString(), "cattle-health-locomotion") }
    }
    records.observations.maxByOrNull { it.epochDay }?.let { latest ->
        FarmOperationalSection("Latest observation") {
            Text(LocalDate.ofEpochDay(latest.epochDay).toString(), color = AnimalFarmTheme.colors.mutedInk)
            if (latest.redFlag) Text("Red flag", fontWeight = FontWeight.Bold)
            Text(latest.signs)
        }
    }
    if (active.isEmpty()) Text("No active withdrawal windows recorded on this device.", color = AnimalFarmTheme.colors.mutedInk)
}

/** FOS-CATTLE-035 */
@Composable
private fun CattleTimelineContent(records: CattleRecords) {
    if (records.timeline.isEmpty()) {
        AnimalFarmEmptyState("No records for this animal on this device.")
        return
    }
    FarmOperationalSection("Events · ${records.timeline.size}") {
        records.timeline.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider()
            CattleRow("${LocalDate.ofEpochDay(row.epochDay)} · ${row.kind}", row.summary, "cattle-timeline:${row.id}")
        }
    }
}

/** FOS-CATTLE-031 */
@Composable
private fun CattleMovementContent(records: CattleRecords) {
    if (records.movements.isEmpty() && records.identifiers.isEmpty()) {
        AnimalFarmEmptyState("No identifiers or movements recorded for this animal on this device.")
        return
    }
    FarmOperationalSection("Identifiers · ${records.identifiers.size}") {
        if (records.identifiers.isEmpty()) Text("No identifiers recorded for this animal.", color = AnimalFarmTheme.colors.mutedInk)
        records.identifiers.forEach {
            CattleRow(
                "${it.type} · assigned ${LocalDate.ofEpochDay(it.assignedEpochDay)}" + if (it.active) "" else " · inactive",
                it.value,
                "cattle-identifier:${it.id}",
            )
        }
    }
    FarmOperationalSection("Movements · ${records.movements.size}") {
        if (records.movements.isEmpty()) Text("No movements recorded for this animal.", color = AnimalFarmTheme.colors.mutedInk)
        records.movements.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider()
            CattleRow(
                "${LocalDate.ofEpochDay(row.epochDay)} · ${cattleMovementDirection(row.direction)}",
                "From ${row.fromPlace ?: "not recorded"} · to ${row.toPlace ?: "not recorded"}",
                "cattle-movement:${row.id}",
            )
        }
    }
}
