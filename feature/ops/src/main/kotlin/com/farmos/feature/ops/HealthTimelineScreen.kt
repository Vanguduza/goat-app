package com.farmos.feature.ops

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

enum class HealthTimelineKind(val label: String) {
    OBSERVATION("Observation"),
    TREATMENT("Treatment"),
    VET_VISIT("Vet visit"),
    LAB_RESULT("Lab result"),
}

/** One recorded health event on the farm-wide timeline, on its local calendar day. */
data class HealthTimelineEntry(
    val kind: HealthTimelineKind,
    val id: String,
    val epochDay: Long,
    /** Tag or name of the animal; null for a group/species-level record. */
    val subjectLabel: String?,
    val speciesCode: String?,
    val detail: String,
)

/** One record type's newest-first rows; [bounded] when the list stopped at its row limit. */
data class HealthTimelineStream(val entries: List<HealthTimelineEntry>, val bounded: Boolean)

/**
 * Farm-wide health timeline. When any record type was bounded, only days after that type's oldest
 * listed day are complete for every type, so [entries] start at [completeFromEpochDay] and nothing
 * older is shown as if it were the whole history. [totalCount] is the exhaustive record count.
 */
data class HealthTimeline(
    val entries: List<HealthTimelineEntry> = emptyList(),
    val completeFromEpochDay: Long? = null,
    val totalCount: Int? = null,
)

/**
 * Merges per-type newest-first streams into one timeline. A bounded stream may be missing rows on
 * and before its oldest listed day, so every entry on or before the latest such day is dropped.
 */
fun mergeHealthTimeline(streams: List<HealthTimelineStream>, totalCount: Int?): HealthTimeline {
    val cutoff = streams.filter { it.bounded && it.entries.isNotEmpty() }.maxOfOrNull { stream -> stream.entries.minOf { it.epochDay } }
    val entries = streams.flatMap { it.entries }
        .filter { cutoff == null || it.epochDay > cutoff }
        .sortedWith(compareByDescending<HealthTimelineEntry> { it.epochDay }.thenBy { it.kind.ordinal }.thenBy { it.id })
    return HealthTimeline(entries, cutoff?.plus(1), totalCount)
}

/** FOS-HEALTH-029 — recorded health events across the farm, newest first. Records only. */
@Composable
internal fun HealthTimelineScreen(timeline: HealthTimeline, onBack: () -> Unit) {
    FarmOperationalPage(
        "FOS-HEALTH-029",
        "Health timeline",
        "Observations, treatments, vet visits and lab results recorded on this device, newest first. Records only; no diagnosis or dosing.",
        FarmVisualClass.I3,
        onBack,
    ) {
        if (timeline.entries.isEmpty() && timeline.completeFromEpochDay == null) {
            AnimalFarmEmptyState("No health records on this device.")
            return@FarmOperationalPage
        }
        timeline.completeFromEpochDay?.let {
            Text(
                "Every record from ${LocalDate.ofEpochDay(it)} onward is listed. Earlier records are not shown here.",
                color = AnimalFarmTheme.colors.mutedInk,
                modifier = Modifier.testTag("health-timeline-window"),
            )
        }
        FarmOperationalSection(recordListTitle("Records", timeline.entries.size, timeline.totalCount)) {
            timeline.entries.forEachIndexed { index, entry ->
                if (index > 0) HorizontalDivider()
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag("health-timeline:${entry.kind.name.lowercase()}:${entry.id}"),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text("${LocalDate.ofEpochDay(entry.epochDay)} · ${entry.kind.label}", color = AnimalFarmTheme.colors.mutedInk)
                    Text(HealthRecords.subject(entry.subjectLabel, entry.speciesCode), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(entry.detail)
                }
            }
        }
    }
}
