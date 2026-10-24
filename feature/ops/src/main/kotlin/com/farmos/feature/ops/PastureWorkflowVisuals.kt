package com.farmos.feature.ops

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.farmos.core.design.AnimalFarmEmptyState
import com.farmos.core.design.AnimalFarmHomeMetrics
import com.farmos.core.design.AnimalFarmTheme
import com.farmos.core.design.FarmSectionTabs
import com.farmos.core.design.FarmWorkflowState
import com.farmos.core.design.FarmWorkflowStep
import com.farmos.core.design.FarmWorkflowTimeline
import com.farmos.core.design.FosDimens
import java.time.LocalDate

/** FOS-PASTURE-001/004/010. Recorded occupancy is a schematic, never invented geography. */
@Composable
fun PastureOccupancyBoard(records: PastureRecords, today: LocalDate = LocalDate.now()) {
    var view by rememberSaveable { mutableStateOf("All") }
    if (records.paddocks.isEmpty()) { AnimalFarmEmptyState("No paddocks on this device."); return }
    Text("Recorded occupancy", style = MaterialTheme.typography.titleMedium)
    FarmSectionTabs(listOf("All", "Open grazing", "Resting"), view, { view = it })
    val shown = records.paddocks.filter { view == "All" || if (view == "Open grazing") it.openSessions > 0 else it.openSessions == 0 && it.latestExitedEpochDay != null }
    if (shown.isEmpty()) Text("No paddocks in this view.", color = AnimalFarmTheme.colors.mutedInk)
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("pasture-occupancy-board")) {
        val columns = if (LocalDensity.current.fontScale >= 1.5f) 1 else if (maxWidth >= 600.dp) 3 else 2
        Column(verticalArrangement = Arrangement.spacedBy(FosDimens.IntraCardGap)) {
            shown.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FosDimens.IntraCardGap)) {
                    row.forEach { paddock ->
                        val open = paddock.openSessions > 0
                        val resting = !open && paddock.latestExitedEpochDay != null
                        val colors = AnimalFarmTheme.colors
                        Surface(
                            modifier = Modifier.weight(1f).testTag("pasture-cell:" + paddock.id),
                            shape = RoundedCornerShape(AnimalFarmHomeMetrics.tileRadius),
                            color = if (open) colors.primary else if (resting) colors.lime else colors.surface,
                            contentColor = if (open) colors.onPrimary else if (resting) colors.onLime else colors.ink,
                            border = BorderStroke(FosDimens.Hairline, colors.divider),
                        ) {
                            Column(Modifier.padding(FosDimens.CardPadding), verticalArrangement = Arrangement.spacedBy(FosDimens.IntraCardGap)) {
                                Text(paddock.code, style = MaterialTheme.typography.titleLarge)
                                Text(paddock.displayName, style = MaterialTheme.typography.bodyLarge)
                                Text(when { open -> "Open grazing"; resting -> "Resting"; else -> "No grazing recorded" }, style = MaterialTheme.typography.labelLarge)
                                if (open) Text("${paddock.openHeadCount} head recorded at entry")
                                else paddock.latestExitedEpochDay?.let { ended ->
                                    val days = today.toEpochDay() - ended
                                    Text(if (days >= 0) "$days days since recorded exit" else "Exit recorded for ${LocalDate.ofEpochDay(ended)}")
                                }
                                if (!paddock.active) Text("Inactive paddock")
                            }
                        }
                    }
                }
            }
        }
    }
    Text("Occupancy schematic · positions are not geographic. Rest is measured from recorded exits; no target is inferred.", style = MaterialTheme.typography.bodySmall, color = AnimalFarmTheme.colors.mutedInk)
}

/** FOS-PASTURE-009. Open sessions remain open until a recorded exit, regardless of the calendar. */
@Composable
fun GrazingRecordedFlow(records: PastureRecords, recordsContent: (@Composable () -> Unit)? = null) {
    val recent = records.sessions.sortedWith(compareByDescending<GrazingSessionView> { it.enteredEpochDay }.thenBy { it.id }).take(6)
    FarmWorkflowTimeline(
        title = "Grazing rotation history",
        steps = recent.map { FarmWorkflowStep(
            it.paddockLabel, it.groupLabel + " · ${it.headCount} head at entry",
            "Entered ${LocalDate.ofEpochDay(it.enteredEpochDay)} · " + (it.exitedEpochDay?.let { end -> "Exited ${LocalDate.ofEpochDay(end)}" } ?: "Session still open"),
            if (it.exitedEpochDay == null) FarmWorkflowState.CURRENT else FarmWorkflowState.RECORDED,
        ) },
        modifier = Modifier.testTag("pasture-grazing-workflow"),
        caption = recordListTitle("Latest sessions", recent.size, records.sessionCount), recordsContent = recordsContent,
    )
}
