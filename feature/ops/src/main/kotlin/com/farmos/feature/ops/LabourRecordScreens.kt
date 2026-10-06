package com.farmos.feature.ops

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farmos.core.design.AnimalFarmEmptyState
import com.farmos.core.design.AnimalFarmTheme
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.design.FarmVisualClass
import java.time.LocalDate

data class LabourEntryView(val id: String, val workerName: String, val taskCode: String, val minutes: Int, val note: String?, val epochDay: Long)

/** Exhaustive recorded minutes for one worker label across every entry. */
data class LabourWorkerTotalView(val workerName: String, val minutes: Long, val entryCount: Int, val latestEpochDay: Long)

/**
 * Farm-scoped labour records. [entries] may be only the latest rows; [entryCount] and [workers]
 * are exhaustive aggregates. Worker names are farm labels, not logins; no cost or pay is derived.
 */
data class LabourRecords(
    val entries: List<LabourEntryView> = emptyList(),
    val entryCount: Int? = null,
    val workers: List<LabourWorkerTotalView> = emptyList(),
)

internal fun labourDuration(minutes: Long): String = if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"

@Composable
private fun LabourRow(primary: String, secondary: String, tag: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag(tag), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(primary, color = AnimalFarmTheme.colors.mutedInk)
        Text(secondary, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Labour record navigation. [home] renders the labour home (FOS-LABOUR-001) and places the supplied
 * record action; the action opens the read-only work log whose back returns home.
 */
@Composable
fun LabourRecordNavigator(
    records: LabourRecords,
    /** The worker register (FOS-LABOUR-002/003, resolution R1); null hides the entry. */
    workers: (@Composable (onBack: () -> Unit) -> Unit)? = null,
    home: @Composable (recordActions: @Composable () -> Unit) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    var register by rememberSaveable { mutableStateOf(false) }
    when {
        register && workers != null -> workers { register = false }
        open -> LabourWorkLogScreen(records) { open = false }
        else -> home {
            FarmOperationalSection("Records") {
                TextButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text("Open Work log") }
                if (workers != null) {
                    TextButton(onClick = { register = true }, modifier = Modifier.fillMaxWidth()) { Text("Workers") }
                }
            }
        }
    }
}

/** FOS-LABOUR-005 — exhaustive minutes per worker label and recorded entries, newest first. */
/** FOS-LABOUR-008 — workload: per-worker exhaustive minutes are the recorded workload view (root tag FOS-LABOUR-005). */
@Composable
internal fun LabourWorkLogScreen(records: LabourRecords, onBack: () -> Unit) {
    FarmOperationalPage("FOS-LABOUR-005", "Work log", "Recorded labour on this device, newest first. Worker names are farm labels.", FarmVisualClass.I3, onBack) {
        if (records.entries.isEmpty()) {
            AnimalFarmEmptyState("No labour recorded on this device.")
            return@FarmOperationalPage
        }
        FarmOperationalSection("By worker label, all recorded entries") {
            records.workers.forEach {
                LabourRow(
                    it.workerName,
                    "${labourDuration(it.minutes)} · ${it.entryCount} " + (if (it.entryCount == 1) "entry" else "entries") +
                        " · latest ${LocalDate.ofEpochDay(it.latestEpochDay)}",
                    "labour-worker:${it.workerName}",
                )
            }
        }
        FarmOperationalSection(recordListTitle("Entries", records.entries.size, records.entryCount)) {
            records.entries.forEachIndexed { index, entry ->
                if (index > 0) HorizontalDivider()
                LabourRow(
                    "${LocalDate.ofEpochDay(entry.epochDay)} · ${entry.workerName}",
                    "${entry.taskCode} · ${labourDuration(entry.minutes.toLong())}" + (entry.note?.let { " · $it" } ?: ""),
                    "labour-entry:${entry.id}",
                )
            }
        }
    }
}
