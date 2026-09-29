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
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.design.FarmVisualClass
import java.time.LocalDate

/** One recorded grazing session; [exitedEpochDay] is null while the session is still open. */
data class GrazingSessionView(
    val id: String,
    val paddockId: String,
    val paddockLabel: String,
    val groupLabel: String,
    val speciesCode: String,
    val headCount: Int,
    val enteredEpochDay: Long,
    val exitedEpochDay: Long?,
)

/**
 * A paddock as recorded with its exhaustive grazing summary. [openHeadCount] is the head count
 * recorded when open sessions started, not a physical count. [areaM2] null means not recorded.
 */
data class PaddockView(
    val id: String,
    val code: String,
    val displayName: String,
    val areaM2: Int?,
    val waterSource: String,
    val shade: Boolean,
    val active: Boolean,
    val sessionCount: Int,
    val openSessions: Int,
    val openHeadCount: Int,
    val latestEnteredEpochDay: Long?,
    val latestExitedEpochDay: Long?,
)

/**
 * Farm-scoped pasture records. [sessions] may be only the latest rows; [sessionCount] and each
 * paddock's summary are exhaustive aggregates. No carrying capacity or rest target is derived.
 */
data class PastureRecords(
    val paddocks: List<PaddockView> = emptyList(),
    val sessions: List<GrazingSessionView> = emptyList(),
    val sessionCount: Int? = null,
)

enum class PastureRecordPage(val label: String) {
    PADDOCKS("Paddock list"),
    PADDOCK_DETAIL("Paddock detail"),
    GRAZING_HISTORY("Grazing history"),
}

private fun date(epochDay: Long) = LocalDate.ofEpochDay(epochDay).toString()

private fun PaddockView.occupancy(): String = when {
    openSessions > 0 -> "Grazing now · $openHeadCount head recorded at entry"
    latestExitedEpochDay != null -> "Not grazed now · last exit ${date(latestExitedEpochDay)}"
    else -> "No grazing recorded"
}

private fun GrazingSessionView.span(): String =
    "${date(enteredEpochDay)} to " + (exitedEpochDay?.let(::date) ?: "open")

@Composable
private fun PastureRow(primary: String, secondary: String, tag: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag(tag), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(primary, color = AnimalFarmTheme.colors.mutedInk)
        Text(secondary, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Pasture record navigation. [home] renders the pasture home (FOS-PASTURE-001) and places the
 * supplied record actions; each action opens a read-only record page whose back returns home.
 */
@Composable
fun PastureRecordNavigator(records: PastureRecords, home: @Composable (recordActions: @Composable () -> Unit) -> Unit) {
    var page by rememberSaveable { mutableStateOf<PastureRecordPage?>(null) }
    val back = { page = null }
    when (page) {
        null -> home {
            FarmOperationalSection("Records") {
                PastureRecordPage.entries.forEach { target ->
                    TextButton(onClick = { page = target }, modifier = Modifier.fillMaxWidth()) { Text("Open ${target.label}") }
                }
            }
        }
        PastureRecordPage.PADDOCKS -> PaddockListScreen(records, back)
        PastureRecordPage.PADDOCK_DETAIL -> PaddockDetailScreen(records, back)
        PastureRecordPage.GRAZING_HISTORY -> GrazingHistoryScreen(records, back)
    }
}

/** FOS-PASTURE-002 — every paddock with its recorded grazing state. */
@Composable
internal fun PaddockListScreen(records: PastureRecords, onBack: () -> Unit) {
    FarmOperationalPage("FOS-PASTURE-002", "Paddock list", "Paddocks on this device and their recorded grazing.", FarmVisualClass.I3, onBack) {
        if (records.paddocks.isEmpty()) {
            AnimalFarmEmptyState("No paddocks on this device.")
            return@FarmOperationalPage
        }
        FarmOperationalSection("Paddocks · ${records.paddocks.size}") {
            records.paddocks.forEachIndexed { index, paddock ->
                if (index > 0) HorizontalDivider()
                PastureRow(
                    "${paddock.code} · ${paddock.displayName}" + if (paddock.active) "" else " · inactive",
                    paddock.occupancy(),
                    "paddock:${paddock.id}",
                )
            }
        }
    }
}

/** FOS-PASTURE-003 — one paddock as recorded, with its grazing sessions. */
@Composable
internal fun PaddockDetailScreen(records: PastureRecords, onBack: () -> Unit) {
    var selectedId by rememberSaveable { mutableStateOf(records.paddocks.firstOrNull()?.id) }
    FarmOperationalPage("FOS-PASTURE-003", "Paddock detail", "One paddock as recorded and its grazing sessions.", FarmVisualClass.I2, onBack) {
        if (records.paddocks.isEmpty()) {
            AnimalFarmEmptyState("No paddocks on this device.")
            return@FarmOperationalPage
        }
        FarmOperationalSection("Paddocks") {
            records.paddocks.forEach { paddock ->
                val selected = paddock.id == selectedId
                TextButton(
                    onClick = { selectedId = paddock.id },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = AnimalFarmTheme.minimumTouchDp.dp)
                        .semantics { this.selected = selected }
                        .testTag("paddock-option:${paddock.id}"),
                ) { Text("${paddock.code} · ${paddock.displayName}", fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) }
            }
        }
        val paddock = records.paddocks.firstOrNull { it.id == selectedId } ?: return@FarmOperationalPage
        FarmOperationalSection(paddock.displayName) {
            PastureRow("Code", paddock.code, "paddock-code")
            PastureRow("Area", paddock.areaM2?.let { "$it m²" } ?: "Not recorded", "paddock-area")
            PastureRow("Water source", paddock.waterSource, "paddock-water")
            PastureRow("Shade", if (paddock.shade) "Yes" else "No", "paddock-shade")
            PastureRow("Status", if (paddock.active) "Active" else "Inactive", "paddock-status")
            PastureRow("Grazing", paddock.occupancy(), "paddock-occupancy")
        }
        val sessions = records.sessions.filter { it.paddockId == paddock.id }
        FarmOperationalSection(recordListTitle("Grazing sessions", sessions.size, paddock.sessionCount)) {
            if (paddock.sessionCount == 0) Text("No grazing recorded for this paddock.", color = AnimalFarmTheme.colors.mutedInk)
            sessions.forEach {
                PastureRow(it.span(), "${it.groupLabel} · ${it.speciesCode} · ${it.headCount} head at entry", "paddock-session:${it.id}")
            }
        }
    }
}

/** FOS-PASTURE-009 — recorded grazing sessions across paddocks, newest entry first. */
@Composable
internal fun GrazingHistoryScreen(records: PastureRecords, onBack: () -> Unit) {
    FarmOperationalPage("FOS-PASTURE-009", "Grazing history", "Recorded grazing sessions on this device, newest entry first.", FarmVisualClass.I3, onBack) {
        if (records.sessions.isEmpty()) {
            AnimalFarmEmptyState("No grazing recorded on this device.")
            return@FarmOperationalPage
        }
        FarmOperationalSection(recordListTitle("Grazing sessions", records.sessions.size, records.sessionCount)) {
            records.sessions.forEachIndexed { index, session ->
                if (index > 0) HorizontalDivider()
                PastureRow(
                    "${session.span()} · ${session.paddockLabel}",
                    "${session.groupLabel} · ${session.speciesCode} · ${session.headCount} head at entry",
                    "grazing-session:${session.id}",
                )
            }
        }
    }
}
