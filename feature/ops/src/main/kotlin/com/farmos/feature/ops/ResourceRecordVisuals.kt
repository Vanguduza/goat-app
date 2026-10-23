package com.farmos.feature.ops

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.farmos.core.design.FarmBarValue
import com.farmos.core.design.FarmMetricBars
import com.farmos.core.design.FarmWorkflowState
import com.farmos.core.design.FarmWorkflowStep
import com.farmos.core.design.FarmWorkflowTimeline
import java.time.LocalDate

/** FOS-FEED-001/006. Each value retains its own inventory unit; none are mixed into a false total. */
@Composable
fun FeedRecordedFlow(records: FeedRecords, recordsContent: (@Composable () -> Unit)? = null) {
    val recent = records.issues.sortedWith(compareByDescending<FeedIssueView> { it.epochDay }.thenBy { it.id }).take(6)
    FarmWorkflowTimeline(
        title = "Recent feed issues",
        steps = recent.map { FarmWorkflowStep(LocalDate.ofEpochDay(it.epochDay).toString(), it.itemLabel + " · " + it.quantity, it.groupLabel, FarmWorkflowState.RECORDED) },
        modifier = Modifier.testTag("feed-issue-workflow"),
        caption = recordListTitle("Latest issues", recent.size, records.issueCount),
        recordsContent = recordsContent,
    )
}

/** FOS-WATER-001/009. A source timeline uses logged quantities, not an inferred consumption rate. */
@Composable
fun WaterRecordedFlow(records: WaterRecords, recordsContent: (@Composable () -> Unit)? = null) {
    val recent = records.records.sortedWith(compareByDescending<WaterRecordView> { it.epochDay }.thenBy { it.id }).take(6)
    FarmWorkflowTimeline(
        title = "Recent water records",
        steps = recent.map { FarmWorkflowStep(LocalDate.ofEpochDay(it.epochDay).toString(), it.source, waterLitres(it.litresMilli), FarmWorkflowState.RECORDED) },
        modifier = Modifier.testTag("water-record-workflow"),
        caption = recordListTitle("Latest records", recent.size, records.recordCount),
        recordsContent = recordsContent,
    )
}

@Composable
fun WaterSourceChart(records: WaterRecords) {
    FarmMetricBars(
        title = "Recorded water by source", unit = "Litres",
        values = records.sources.map { FarmBarValue(it.source, it.litresMilli / 1000.0, waterLitres(it.litresMilli), "${LocalDate.ofEpochDay(it.firstEpochDay)} to ${LocalDate.ofEpochDay(it.latestEpochDay)} · ${it.recordCount} records") },
        modifier = Modifier.testTag("water-source-chart"),
        caption = "Sums of recorded litres for each source's shown period.",
    )
}
