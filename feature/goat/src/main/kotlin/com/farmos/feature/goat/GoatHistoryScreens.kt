package com.farmos.feature.goat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farmos.core.design.AnimalFarmEmptyState
import com.farmos.core.design.AnimalFarmTheme
import com.farmos.core.design.FarmIllustratedSectionSurface
import com.farmos.domain.goat.FamachaSample
import com.farmos.domain.goat.GoatSex
import com.farmos.domain.goat.GoatSnapshot
import com.farmos.domain.goat.MilkSample
import com.farmos.domain.goat.SccSample
import java.time.LocalDate

/**
 * Read-only goat record surfaces reached from the goat profile (FOS-GOAT-003).
 * Every page reads the selected [GoatSnapshot]; none of them writes business state.
 */
internal val goatHistoryPages = setOf(
    GoatPage.TIMELINE,
    GoatPage.GROWTH_HISTORY,
    GoatPage.GROWTH_CHART,
    GoatPage.ADG_DETAIL,
    GoatPage.LACTATION_HISTORY,
    GoatPage.SCC_HISTORY,
    GoatPage.FAMACHA_HISTORY,
    GoatPage.HEALTH_SUMMARY,
    GoatPage.TREATMENT_HISTORY,
    GoatPage.WITHDRAWAL_STATUS,
    GoatPage.VET_VISITS,
    GoatPage.LAB_RESULTS,
    GoatPage.DOE_REPRODUCTION,
    GoatPage.PEDIGREE,
    GoatPage.KIDDING_DETAIL,
    GoatPage.KID_PROFILE,
)

@Composable
internal fun GoatHistoryPage(
    page: GoatPage,
    goat: GoatSnapshot?,
    today: LocalDate,
    onOpen: (GoatPage) -> Unit,
    onBack: () -> Unit,
    selectedKiddingId: String? = null,
    onOpenKidding: (String) -> Unit = {},
) {
    when (page) {
        GoatPage.TIMELINE -> GoatTimelineScreen(goat, onBack)
        GoatPage.GROWTH_HISTORY -> GoatGrowthHistoryScreen(goat, onBack)
        GoatPage.GROWTH_CHART -> GoatGrowthChartScreen(goat, onBack)
        GoatPage.ADG_DETAIL -> GoatAdgDetailScreen(goat, onBack)
        GoatPage.LACTATION_HISTORY -> GoatLactationHistoryScreen(goat, onBack)
        GoatPage.SCC_HISTORY -> GoatSccHistoryScreen(goat, onBack)
        GoatPage.FAMACHA_HISTORY -> GoatFamachaHistoryScreen(goat, onBack)
        GoatPage.HEALTH_SUMMARY -> GoatHealthSummaryScreen(goat, today, onOpen, onBack)
        GoatPage.TREATMENT_HISTORY -> GoatTreatmentHistoryScreen(goat, onBack)
        GoatPage.WITHDRAWAL_STATUS -> GoatWithdrawalStatusScreen(goat, today, onBack)
        GoatPage.VET_VISITS -> GoatVetVisitsScreen(goat, onBack)
        GoatPage.LAB_RESULTS -> GoatLabResultsScreen(goat, onBack)
        GoatPage.DOE_REPRODUCTION -> GoatDoeReproductionScreen(goat, onOpen, onOpenKidding, onBack)
        GoatPage.KIDDING_DETAIL -> GoatKiddingDetailScreen(goat, selectedKiddingId, onBack)
        GoatPage.KID_PROFILE -> GoatKidProfileScreen(goat, onBack)
        GoatPage.PEDIGREE -> GoatPedigreeScreen(goat, onBack)
        else -> error("$page is not a goat history page")
    }
}

/** Profile entry points into the read-only record pages. Records stay readable after an exit. */
@Composable
internal fun GoatRecordLinks(goat: GoatSnapshot, onOpen: (GoatPage) -> Unit) {
    FarmIllustratedSectionSurface {
        Text("Records", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        val links = buildList {
            add("Timeline" to GoatPage.TIMELINE)
            add("Growth history" to GoatPage.GROWTH_HISTORY)
            add("Growth chart" to GoatPage.GROWTH_CHART)
            add("Average daily gain" to GoatPage.ADG_DETAIL)
            add("FAMACHA history" to GoatPage.FAMACHA_HISTORY)
            add("Health summary" to GoatPage.HEALTH_SUMMARY)
            add("Treatment history" to GoatPage.TREATMENT_HISTORY)
            add("Withdrawal status" to GoatPage.WITHDRAWAL_STATUS)
            add("Vet visits" to GoatPage.VET_VISITS)
            add("Lab results" to GoatPage.LAB_RESULTS)
            if (goat.sex == GoatSex.FEMALE) add("Breeding records" to GoatPage.DOE_REPRODUCTION)
            add("Pedigree" to GoatPage.PEDIGREE)
            if (goat.birthRecord != null) add("Birth record" to GoatPage.KID_PROFILE)
            if (goat.sex == GoatSex.FEMALE) add("Milk history" to GoatPage.LACTATION_HISTORY)
            if (goat.sex == GoatSex.FEMALE) add("SCC history" to GoatPage.SCC_HISTORY)
        }
        links.forEach { (label, page) ->
            TextButton(
                onClick = { onOpen(page) },
                modifier = Modifier.fillMaxWidth().heightIn(min = AnimalFarmTheme.minimumTouchDp.dp),
            ) { Text(label, maxLines = 1, softWrap = false) }
        }
    }
}

@Composable
internal fun GoatHistoryFrame(
    title: String,
    screenId: String,
    goat: GoatSnapshot?,
    onBack: () -> Unit,
    content: @Composable (GoatSnapshot) -> Unit,
) {
    IllustratedGoatPage(title, screenId, onBack) {
        if (goat == null) {
            AnimalFarmEmptyState("Select a goat from the herd before opening its records.")
        } else {
            Text(
                goatDisplayName(goat) + " · " + goat.tag,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (goat.syncPending) {
                Text("Includes records saved on this device that are waiting to sync.", color = AnimalFarmTheme.colors.mutedInk)
            }
            content(goat)
        }
    }
}

@Composable
internal fun GoatHistoryRow(primary: String, secondary: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(primary, style = MaterialTheme.typography.bodyLarge)
        Text(secondary, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

/** FOS-GOAT-009 — chronological record timeline with record-type filters. */
@Composable
private fun GoatTimelineScreen(goat: GoatSnapshot?, onBack: () -> Unit) {
    GoatHistoryFrame("Timeline", "FOS-GOAT-009", goat, onBack) { subject ->
        val entries = GoatRecordHistory.timeline(subject)
        if (entries.isEmpty()) {
            AnimalFarmEmptyState("No records for this goat yet.")
            return@GoatHistoryFrame
        }
        var filterName by rememberSaveable(subject.animalId) { mutableStateOf<String?>(null) }
        val filter = filterName?.let { name -> GoatRecordKind.entries.firstOrNull { it.name == name } }
        val options = listOf<GoatRecordKind?>(null) + GoatRecordHistory.kindsPresent(entries)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            options.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { kind ->
                        val selected = filter == kind
                        TextButton(
                            onClick = { filterName = kind?.name },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = AnimalFarmTheme.minimumTouchDp.dp)
                                .semantics { this.selected = selected },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        ) {
                            Text(
                                kind?.label ?: "All",
                                maxLines = 1,
                                softWrap = false,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        val visible = entries.filter { filter == null || it.kind == filter }
        FarmIllustratedSectionSurface {
            visible.forEachIndexed { index, entry ->
                if (index > 0) HorizontalDivider()
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag("goat-timeline-entry:${entry.recordId}")) {
                    Text("${entry.day} · ${entry.kind.label}", color = AnimalFarmTheme.colors.mutedInk)
                    Text(entry.summary, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** FOS-GOAT-013 — every recorded weight with the change from the previous weight. */
@Composable
private fun GoatGrowthHistoryScreen(goat: GoatSnapshot?, onBack: () -> Unit) {
    GoatHistoryFrame("Growth history", "FOS-GOAT-013", goat, onBack) { subject ->
        val ordered = GoatRecordHistory.weightsAscending(subject)
        if (ordered.isEmpty()) {
            AnimalFarmEmptyState("No weights recorded yet.")
            return@GoatHistoryFrame
        }
        val changes = GoatRecordHistory.weightChanges(ordered)
        FarmIllustratedSectionSurface {
            ordered.indices.reversed().forEach { index ->
                val sample = ordered[index]
                val change = changes[index]?.let { " · ${formatSignedKg(it)} kg" } ?: " · first weight"
                GoatHistoryRow(
                    GoatRecordHistory.weightDay(sample.measuredAtEpochMillis).toString() + change,
                    "${formatKg(sample.weightGrams)} kg",
                )
            }
        }
    }
}

/** FOS-GOAT-014 — weight over time as a chart with the equivalent table. */
@Composable
private fun GoatGrowthChartScreen(goat: GoatSnapshot?, onBack: () -> Unit) {
    GoatHistoryFrame("Growth chart", "FOS-GOAT-014", goat, onBack) { subject ->
        val ordered = GoatRecordHistory.weightsAscending(subject)
        if (ordered.size < 2) {
            AnimalFarmEmptyState("Record at least two weights to draw a growth chart.")
        } else {
            val colors = AnimalFarmTheme.colors
            val minGrams = ordered.minOf { it.weightGrams }
            val maxGrams = ordered.maxOf { it.weightGrams }
            val firstMillis = ordered.first().measuredAtEpochMillis
            val spanMillis = (ordered.last().measuredAtEpochMillis - firstMillis).coerceAtLeast(1L)
            val description = "Weight in kilograms from ${GoatRecordHistory.weightDay(firstMillis)} to " +
                "${GoatRecordHistory.weightDay(ordered.last().measuredAtEpochMillis)}, " +
                "${formatKg(minGrams)} to ${formatKg(maxGrams)} kg across ${ordered.size} weights"
            FarmIllustratedSectionSurface {
                Text("Weight (kg)", color = colors.mutedInk)
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .testTag("goat-growth-chart")
                        .semantics { contentDescription = description },
                ) {
                    val range = (maxGrams - minGrams).coerceAtLeast(1L).toFloat()
                    val inset = 12.dp.toPx()
                    val points = ordered.map {
                        Offset(
                            inset + (size.width - 2 * inset) * ((it.measuredAtEpochMillis - firstMillis).toFloat() / spanMillis),
                            size.height - inset - (size.height - 2 * inset) * ((it.weightGrams - minGrams) / range),
                        )
                    }
                    drawLine(colors.divider, Offset(inset, size.height - inset), Offset(size.width - inset, size.height - inset), 1.dp.toPx())
                    points.zipWithNext().forEach { (a, b) -> drawLine(colors.primary, a, b, 3.dp.toPx()) }
                    points.forEach {
                        drawCircle(colors.primary, 5.dp.toPx(), it)
                        drawCircle(colors.surface, 5.dp.toPx(), it, style = Stroke(1.5.dp.toPx()))
                    }
                }
                Text("${formatKg(minGrams)} kg lowest · ${formatKg(maxGrams)} kg highest", color = colors.mutedInk)
            }
        }
        if (ordered.isNotEmpty()) {
            FarmIllustratedSectionSurface {
                Text("Table", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                ordered.forEach {
                    GoatHistoryRow(GoatRecordHistory.weightDay(it.measuredAtEpochMillis).toString(), "${formatKg(it.weightGrams)} kg")
                }
            }
        }
    }
}

/** FOS-GOAT-015 — how the recorded average daily gain is derived. */
@Composable
private fun GoatAdgDetailScreen(goat: GoatSnapshot?, onBack: () -> Unit) {
    GoatHistoryFrame("Average daily gain", "FOS-GOAT-015", goat, onBack) { subject ->
        val breakdown = GoatRecordHistory.adg(subject)
        if (breakdown == null) {
            AnimalFarmEmptyState("Average daily gain needs two weights recorded on different days.")
            return@GoatHistoryFrame
        }
        FarmIllustratedSectionSurface {
            Text(
                "${breakdown.averageDailyGainGrams} g/day",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("goat-adg-value"),
            )
            Text("From the first and latest of ${breakdown.sampleCount} weights", color = AnimalFarmTheme.colors.mutedInk)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            GoatHistoryRow(
                "First · " + GoatRecordHistory.weightDay(breakdown.first.measuredAtEpochMillis),
                "${formatKg(breakdown.first.weightGrams)} kg",
            )
            GoatHistoryRow(
                "Latest · " + GoatRecordHistory.weightDay(breakdown.last.measuredAtEpochMillis),
                "${formatKg(breakdown.last.weightGrams)} kg",
            )
            GoatHistoryRow("Change", "${formatSignedKg(breakdown.gainGrams)} kg")
            GoatHistoryRow("Days between weights", breakdown.elapsedDays.toString())
        }
    }
}

/** FOS-GOAT-019 — recorded milk yields for a doe. */
@Composable
private fun GoatLactationHistoryScreen(goat: GoatSnapshot?, onBack: () -> Unit) {
    GoatHistoryFrame("Milk history", "FOS-GOAT-019", goat, onBack) { subject ->
        val records = subject.milkHistory.sortedWith(compareByDescending<MilkSample> { it.occurredEpochDay }.thenBy { it.milkId })
        if (records.isEmpty()) {
            AnimalFarmEmptyState("No milk recorded for this goat yet.")
            return@GoatHistoryFrame
        }
        FarmIllustratedSectionSurface {
            GoatHistoryRow("Records", records.size.toString())
            GoatHistoryRow("Total recorded", "${formatLitres(records.sumOf { it.litresMilli })} L")
        }
        FarmIllustratedSectionSurface {
            records.forEach { GoatHistoryRow(LocalDate.ofEpochDay(it.occurredEpochDay).toString(), "${formatLitres(it.litresMilli)} L") }
        }
    }
}

/** FOS-GOAT-021 — somatic cell count results for a doe. */
@Composable
private fun GoatSccHistoryScreen(goat: GoatSnapshot?, onBack: () -> Unit) {
    GoatHistoryFrame("SCC history", "FOS-GOAT-021", goat, onBack) { subject ->
        val records = subject.sccHistory.sortedWith(compareByDescending<SccSample> { it.occurredEpochDay }.thenBy { it.recordId })
        if (records.isEmpty()) {
            AnimalFarmEmptyState("No SCC results recorded for this goat yet.")
            return@GoatHistoryFrame
        }
        FarmIllustratedSectionSurface {
            records.forEach { GoatHistoryRow(LocalDate.ofEpochDay(it.occurredEpochDay).toString(), sccSummary(it.cellsPerMl, it.dimDays)) }
        }
    }
}

/** FOS-GOAT-024 — FAMACHA scores recorded for this goat. */
@Composable
private fun GoatFamachaHistoryScreen(goat: GoatSnapshot?, onBack: () -> Unit) {
    GoatHistoryFrame("FAMACHA history", "FOS-GOAT-024", goat, onBack) { subject ->
        val records = subject.famachaHistory.sortedWith(compareByDescending<FamachaSample> { it.occurredEpochDay }.thenBy { it.scoreId })
        if (records.isEmpty()) {
            AnimalFarmEmptyState("No FAMACHA scores recorded for this goat yet.")
            return@GoatHistoryFrame
        }
        FarmIllustratedSectionSurface {
            records.forEach { GoatHistoryRow(LocalDate.ofEpochDay(it.occurredEpochDay).toString(), "Score ${it.score}") }
        }
    }
}
