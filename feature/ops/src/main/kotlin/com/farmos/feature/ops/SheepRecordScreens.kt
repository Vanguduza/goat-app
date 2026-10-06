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
import java.time.LocalDate

data class SheepWithdrawalRow(val id: String, val product: String, val windowKind: String, val endsEpochDay: Long)

data class SheepTimelineRow(val id: String, val epochDay: Long, val kind: String, val summary: String)

/** Records for one sheep, read from local farm-scoped tables. Nothing here writes. */
data class SheepAnimalRecords(
    val withdrawals: List<SheepWithdrawalRow> = emptyList(),
    val treatmentCount: Int = 0,
    val observationCount: Int = 0,
    val latestFamacha: Int? = null,
    val latestDag: Int? = null,
    val latestFootrot: Int? = null,
    /** Latest flystrike as "score (region)", or null. */
    val latestFlystrike: String? = null,
    val timeline: List<SheepTimelineRow> = emptyList(),
    /** Recorded weights, oldest first. */
    val weights: List<WeightView> = emptyList(),
)

/** One wool record row for the farm wool dashboard; [subject] is the sheep or mob it was recorded against. */
data class SheepWoolRow(val id: String, val epochDay: Long, val subject: String, val detail: String)

/**
 * Farm-level wool records. Row lists may be bounded to the latest records; [clipGreasyGramsTotal]
 * and the counts are exhaustive farm-scoped aggregates over every recorded row.
 */
data class SheepWoolRecords(
    val clips: List<SheepWoolRow> = emptyList(),
    val clipGreasyGramsTotal: Long = 0,
    val shearing: List<SheepWoolRow> = emptyList(),
    val micron: List<SheepWoolRow> = emptyList(),
    val clipCount: Int? = null,
    val shearingCount: Int? = null,
    val micronCount: Int? = null,
)

private sealed interface SheepLoadState<out T> {
    data object NoSelection : SheepLoadState<Nothing>
    data object Loading : SheepLoadState<Nothing>
    data class Failed(val message: String) : SheepLoadState<Nothing>
    data class Loaded<T>(val value: T) : SheepLoadState<T>
}

internal fun sheepKg(grams: Long): String = java.math.BigDecimal.valueOf(grams, 3).stripTrailingZeros().toPlainString() + " kg"

@Composable
private fun <T> rememberSheepLoad(key: String?, requireKey: Boolean, load: suspend (String?) -> T): SheepLoadState<T> {
    var state by remember(key) {
        mutableStateOf<SheepLoadState<T>>(if (requireKey && key == null) SheepLoadState.NoSelection else SheepLoadState.Loading)
    }
    LaunchedEffect(key) {
        if (requireKey && key == null) return@LaunchedEffect
        state = runCatching { load(key) }
            .fold({ SheepLoadState.Loaded(it) }, { SheepLoadState.Failed(it.message ?: "Records could not be loaded") })
    }
    return state
}

@Composable
private fun SheepRow(primary: String, secondary: String, tag: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag(tag), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(primary, color = AnimalFarmTheme.colors.mutedInk)
        Text(secondary, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun <T> SheepLoadContent(state: SheepLoadState<T>, content: @Composable (T) -> Unit) {
    when (state) {
        SheepLoadState.NoSelection -> AnimalFarmEmptyState("Select a sheep from the flock first.")
        SheepLoadState.Loading -> Text("Loading records")
        is SheepLoadState.Failed -> AnimalFarmWarningSurface { Text(state.message) }
        is SheepLoadState.Loaded -> content(state.value)
    }
}

/** FOS-SHEEP-025 — recorded health for the selected sheep. Records only; no diagnosis or dosing. */
@Composable
internal fun SheepHealthSummaryScreen(
    selectedId: String?,
    today: LocalDate,
    loadRecords: suspend (String) -> SheepAnimalRecords,
    onBack: () -> Unit,
) {
    val state = rememberSheepLoad(selectedId, requireKey = true) { loadRecords(it!!) }
    FarmOperationalPage("FOS-SHEEP-025", "Sheep health summary", "Recorded health for the selected sheep. Records only; no diagnosis or dosing.", FarmVisualClass.I4, onBack) {
        SheepLoadContent(state) { records ->
            val active = records.withdrawals.filter { it.endsEpochDay >= today.toEpochDay() }.sortedBy { it.endsEpochDay }
            if (active.isNotEmpty()) {
                AnimalFarmWarningSurface(Modifier.testTag("sheep-active-withdrawal")) {
                    Text("Active withdrawal", fontWeight = FontWeight.Bold)
                    active.forEach { Text("${it.windowKind} · ${it.product} · until ${LocalDate.ofEpochDay(it.endsEpochDay)}") }
                }
            } else {
                Text("No active withdrawal windows recorded on this device.", color = AnimalFarmTheme.colors.mutedInk)
            }
            FarmOperationalSection("Recorded health") {
                SheepRow("Treatments recorded", records.treatmentCount.toString(), "sheep-health-treatments")
                SheepRow("Observations recorded", records.observationCount.toString(), "sheep-health-observations")
                records.latestFamacha?.let { SheepRow("Latest FAMACHA", "Score $it", "sheep-health-famacha") }
                records.latestDag?.let { SheepRow("Latest dag score", "Score $it", "sheep-health-dag") }
                records.latestFootrot?.let { SheepRow("Latest footrot score", "Score $it", "sheep-health-footrot") }
                records.latestFlystrike?.let { SheepRow("Latest flystrike", it, "sheep-health-flystrike") }
            }
        }
    }
}

/** FOS-SHEEP-031 — every recorded event for the selected sheep, newest first. */
@Composable
internal fun SheepTimelineScreen(selectedId: String?, loadRecords: suspend (String) -> SheepAnimalRecords, onBack: () -> Unit) {
    val state = rememberSheepLoad(selectedId, requireKey = true) { loadRecords(it!!) }
    FarmOperationalPage("FOS-SHEEP-031", "Sheep timeline", "Every recorded event for the selected sheep, newest first.", FarmVisualClass.I3, onBack) {
        SheepLoadContent(state) { records ->
            if (records.timeline.isEmpty()) {
                AnimalFarmEmptyState("No records for this sheep on this device.")
            } else {
                FarmOperationalSection("Events · ${records.timeline.size}") {
                    records.timeline.forEachIndexed { index, row ->
                        if (index > 0) HorizontalDivider()
                        SheepRow("${LocalDate.ofEpochDay(row.epochDay)} · ${row.kind}", row.summary, "sheep-timeline:${row.id}")
                    }
                }
            }
        }
    }
}

/** FOS-SHEEP-007 — weights recorded for the selected sheep. No growth rate or target is derived. */
@Composable
internal fun SheepGrowthHistoryScreen(selectedId: String?, loadRecords: suspend (String) -> SheepAnimalRecords, onBack: () -> Unit) {
    val state = rememberSheepLoad(selectedId, requireKey = true) { loadRecords(it!!) }
    FarmOperationalPage("FOS-SHEEP-007", "Growth history", "Weights recorded for the selected sheep. No growth rate or target is derived.", FarmVisualClass.I3, onBack) {
        SheepLoadContent(state) { records -> WeightHistoryContent(records.weights, "No weights recorded for this sheep on this device.") }
    }
}

/** FOS-SHEEP-018 — farm wool records: clips, shearing events and micron results. */
@Composable
internal fun SheepWoolDashboardScreen(loadWool: suspend () -> SheepWoolRecords, onBack: () -> Unit) {
    val state = rememberSheepLoad("farm", requireKey = false) { loadWool() }
    FarmOperationalPage("FOS-SHEEP-018", "Wool dashboard", "Fleece, shearing and micron records on this device.", FarmVisualClass.I2, onBack) {
        SheepLoadContent(state) { wool ->
            if (wool.clips.isEmpty() && wool.shearing.isEmpty() && wool.micron.isEmpty()) {
                AnimalFarmEmptyState("No wool records on this device.")
                return@SheepLoadContent
            }
            FarmOperationalSection(recordListTitle("Fleece", wool.clips.size, wool.clipCount)) {
                SheepRow("Recorded greasy weight, all clips", sheepKg(wool.clipGreasyGramsTotal), "sheep-wool-total")
                wool.clips.forEach { SheepRow("${LocalDate.ofEpochDay(it.epochDay)} · ${it.subject}", it.detail, "sheep-wool-clip:${it.id}") }
            }
            FarmOperationalSection(recordListTitle("Shearing", wool.shearing.size, wool.shearingCount)) {
                if (wool.shearing.isEmpty()) Text("No shearing events recorded.", color = AnimalFarmTheme.colors.mutedInk)
                wool.shearing.forEach { SheepRow("${LocalDate.ofEpochDay(it.epochDay)} · ${it.subject}", it.detail, "sheep-wool-shearing:${it.id}") }
            }
            FarmOperationalSection(recordListTitle("Micron", wool.micron.size, wool.micronCount)) {
                if (wool.micron.isEmpty()) Text("No micron results recorded.", color = AnimalFarmTheme.colors.mutedInk)
                wool.micron.forEach { SheepRow("${LocalDate.ofEpochDay(it.epochDay)} · ${it.subject}", it.detail, "sheep-wool-micron:${it.id}") }
            }
        }
    }
}

/** FOS-SHEEP-015 — lamb birth profile: dam, birth date, birth type, weights. */
data class SheepLambProfile(
    val lambId: String = "",
    val lambTag: String = "",
    val birthEpochDay: Long? = null,
    val birthType: String? = null,
    val damId: String? = null,
    val damTag: String? = null,
    val birthWeightGrams: Long? = null,
    val markingEpochDay: Long? = null,
    val weaningEpochDay: Long? = null,
)

/** FOS-SHEEP-032 — farm sheep aggregates. */
data class SheepFlockReport(
    val totalHead: Int = 0,
    val eweCount: Int = 0,
    val ramCount: Int = 0,
    val lambCount: Int = 0,
    val lambingsRecorded: Int = 0,
    val lambsBornAlive: Int = 0,
    val weaningsRecorded: Int = 0,
    val shearingsRecorded: Int = 0,
)

/** FOS-SHEEP-028 — sheep group option for paddock assignment. */
data class SheepGroupOption(val groupId: String, val name: String, val headCount: Int)

/** FOS-SHEEP-028 — paddock option for paddock assignment. */
data class SheepPaddockOption(val paddockId: String, val name: String, val hasOpenSession: Boolean)

/** FOS-SHEEP-015 — lamb profile: birth record, dam and early-life events from local records. */
@Composable
internal fun SheepLambProfileScreen(
    selectedId: String?,
    loadProfile: suspend (String) -> SheepLambProfile,
    onBack: () -> Unit,
) {
    val state = rememberSheepLoad(selectedId, requireKey = true) { loadProfile(it!!) }
    FarmOperationalPage("FOS-SHEEP-015", "Lamb profile", "Birth record, dam and early-life events for the selected lamb.", FarmVisualClass.I3, onBack) {
        SheepLoadContent(state) { profile ->
            FarmOperationalSection("Identity") {
                SheepRow("Tag", profile.lambTag.ifBlank { profile.lambId }, "sheep-lamb-tag")
                profile.birthEpochDay?.let { SheepRow("Birth date", LocalDate.ofEpochDay(it).toString(), "sheep-lamb-birth") }
                profile.birthType?.let { SheepRow("Birth type", it, "sheep-lamb-type") }
            }
            FarmOperationalSection("Dam") {
                if (profile.damId == null) {
                    Text("Dam not linked on this device.", color = AnimalFarmTheme.colors.mutedInk)
                } else {
                    SheepRow("Dam", profile.damTag ?: profile.damId, "sheep-lamb-dam")
                }
            }
            FarmOperationalSection("Early life") {
                profile.birthWeightGrams?.let { SheepRow("Birth weight", sheepKg(it), "sheep-lamb-birth-weight") }
                profile.markingEpochDay?.let { SheepRow("Marked", LocalDate.ofEpochDay(it).toString(), "sheep-lamb-marked") }
                profile.weaningEpochDay?.let { SheepRow("Weaned", LocalDate.ofEpochDay(it).toString(), "sheep-lamb-weaned") }
            }
        }
    }
}

/** FOS-SHEEP-032 — sheep report: flock composition and recorded reproduction events. */
@Composable
internal fun SheepFlockReportScreen(
    loadReport: suspend () -> SheepFlockReport,
    onBack: () -> Unit,
) {
    val state = rememberSheepLoad(key = "flock", requireKey = false) { loadReport() }
    FarmOperationalPage("FOS-SHEEP-032", "Sheep report", "Flock composition and recorded reproduction events.", FarmVisualClass.I3, onBack) {
        SheepLoadContent(state) { report ->
            FarmOperationalSection("Flock") {
                SheepRow("Total head", report.totalHead.toString(), "sheep-report-total")
                SheepRow("Ewes", report.eweCount.toString(), "sheep-report-ewes")
                SheepRow("Rams", report.ramCount.toString(), "sheep-report-rams")
                SheepRow("Lambs", report.lambCount.toString(), "sheep-report-lambs")
            }
            FarmOperationalSection("Reproduction") {
                SheepRow("Lambings recorded", report.lambingsRecorded.toString(), "sheep-report-lambings")
                SheepRow("Lambs born alive", report.lambsBornAlive.toString(), "sheep-report-born-alive")
                SheepRow("Weanings recorded", report.weaningsRecorded.toString(), "sheep-report-weanings")
            }
            FarmOperationalSection("Wool") {
                SheepRow("Shearings recorded", report.shearingsRecorded.toString(), "sheep-report-shearings")
            }
        }
    }
}
