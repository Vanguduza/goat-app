package com.farmos.feature.goat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farmos.core.design.AnimalFarmEmptyState
import com.farmos.core.design.AnimalFarmTheme
import com.farmos.core.design.AnimalFarmWarningSurface
import com.farmos.core.design.FarmIllustratedSectionSurface
import com.farmos.domain.goat.GoatLabResultSample
import com.farmos.domain.goat.GoatSnapshot
import com.farmos.domain.goat.GoatTreatmentSample
import com.farmos.domain.goat.GoatVetVisitSample
import com.farmos.domain.goat.GoatWithdrawalSample
import java.time.LocalDate

/**
 * Read-only goat health records (FOS-GOAT-025/026/027/028/029) projected from the shared health ledger.
 * These pages never treat, prescribe or clear a withdrawal; they show what is recorded locally.
 */
internal object GoatHealthRecords {
    /** A window is active through its end day, matching the farm home withdrawal count. */
    fun isActive(window: GoatWithdrawalSample, today: LocalDate): Boolean = window.endsEpochDay >= today.toEpochDay()

    fun activeWindows(goat: GoatSnapshot, today: LocalDate): List<GoatWithdrawalSample> =
        goat.withdrawalWindows
            .filter { isActive(it, today) }
            .sortedWith(compareBy<GoatWithdrawalSample> { it.endsEpochDay }.thenBy { it.windowId })

    fun endedWindows(goat: GoatSnapshot, today: LocalDate): List<GoatWithdrawalSample> =
        goat.withdrawalWindows
            .filterNot { isActive(it, today) }
            .sortedWith(compareByDescending<GoatWithdrawalSample> { it.endsEpochDay }.thenBy { it.windowId })

    fun windowKindLabel(kind: String): String = when (kind.lowercase()) {
        "meat" -> "Meat"
        "milk" -> "Milk"
        "egg", "eggs" -> "Eggs"
        else -> kind
    }

    fun withdrawalDaysLabel(meatDays: Int?, milkDays: Int?): String? {
        val parts = buildList {
            meatDays?.let { add("meat $it day(s)") }
            milkDays?.let { add("milk $it day(s)") }
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(prefix = "Withdrawal: ", separator = " · ")
    }
}

/** FOS-GOAT-025 — health overview with source links. */
@Composable
internal fun GoatHealthSummaryScreen(
    goat: GoatSnapshot?,
    today: LocalDate,
    onOpen: (GoatPage) -> Unit,
    onBack: () -> Unit,
) {
    GoatHistoryFrame("Health summary", "FOS-GOAT-025", goat, onBack) { subject ->
        val active = GoatHealthRecords.activeWindows(subject, today)
        if (active.isNotEmpty()) {
            AnimalFarmWarningSurface(Modifier.testTag("goat-health-active-withdrawal")) {
                Text("Active withdrawal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                active.forEach { window ->
                    Text("${GoatHealthRecords.windowKindLabel(window.windowKind)} · ${window.product} · until ${LocalDate.ofEpochDay(window.endsEpochDay)}")
                }
            }
        }
        FarmIllustratedSectionSurface {
            GoatHistoryRow("Active withdrawals", active.size.toString())
            GoatHistoryRow("Treatments recorded", subject.treatmentHistory.size.toString())
            GoatHistoryRow("Observations recorded", subject.observationHistory.size.toString())
            GoatHistoryRow("Vet visits recorded", subject.vetVisits.size.toString())
            GoatHistoryRow("Lab results recorded", subject.labResults.size.toString())
            subject.famachaHistory.maxByOrNull { it.occurredEpochDay }?.let {
                GoatHistoryRow("Latest FAMACHA · ${LocalDate.ofEpochDay(it.occurredEpochDay)}", "Score ${it.score}")
            }
            subject.bcsHistory.maxByOrNull { it.occurredEpochDay }?.let {
                GoatHistoryRow("Latest BCS · ${LocalDate.ofEpochDay(it.occurredEpochDay)}", "Score ${formatBcs(it.scoreTenths)}")
            }
        }
        subject.observationHistory.maxByOrNull { it.occurredAtEpochMillis }?.let { latest ->
            FarmIllustratedSectionSurface {
                Text("Latest observation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(GoatRecordHistory.weightDay(latest.occurredAtEpochMillis).toString(), color = AnimalFarmTheme.colors.mutedInk)
                if (latest.redFlag) Text("Red flag", fontWeight = FontWeight.Bold)
                Text(latest.signs)
                latest.firstAidApplied?.takeIf { it.isNotBlank() }?.let { Text("First aid: $it") }
            }
        }
        FarmIllustratedSectionSurface {
            listOf(
                "Withdrawal status" to GoatPage.WITHDRAWAL_STATUS,
                "Treatment history" to GoatPage.TREATMENT_HISTORY,
                "FAMACHA history" to GoatPage.FAMACHA_HISTORY,
                "Vet visits" to GoatPage.VET_VISITS,
                "Lab results" to GoatPage.LAB_RESULTS,
            ).forEach { (label, page) ->
                TextButton(
                    onClick = { onOpen(page) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = AnimalFarmTheme.minimumTouchDp.dp),
                ) { Text(label, maxLines = 1, softWrap = false) }
            }
        }
    }
}

/** FOS-GOAT-028 — vet visits recorded for this goat, newest first. */
@Composable
internal fun GoatVetVisitsScreen(goat: GoatSnapshot?, onBack: () -> Unit) {
    GoatHistoryFrame("Vet visits", "FOS-GOAT-028", goat, onBack) { subject ->
        val visits = subject.vetVisits.sortedWith(compareByDescending<GoatVetVisitSample> { it.occurredEpochDay }.thenBy { it.visitId })
        if (visits.isEmpty()) {
            AnimalFarmEmptyState("No vet visits recorded for this goat on this device.")
            return@GoatHistoryFrame
        }
        FarmIllustratedSectionSurface {
            visits.forEachIndexed { index, visit ->
                if (index > 0) HorizontalDivider()
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag("goat-vet-visit:${visit.visitId}")) {
                    Text(LocalDate.ofEpochDay(visit.occurredEpochDay).toString(), color = AnimalFarmTheme.colors.mutedInk)
                    Text(visit.attendingVet, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(visit.reason)
                }
            }
        }
    }
}

/** FOS-GOAT-029 — lab results recorded for this goat, exactly as recorded, without interpretation. */
@Composable
internal fun GoatLabResultsScreen(goat: GoatSnapshot?, onBack: () -> Unit) {
    GoatHistoryFrame("Lab results", "FOS-GOAT-029", goat, onBack) { subject ->
        val results = subject.labResults.sortedWith(compareByDescending<GoatLabResultSample> { it.occurredEpochDay }.thenBy { it.resultId })
        if (results.isEmpty()) {
            AnimalFarmEmptyState("No lab results recorded for this goat on this device.")
            return@GoatHistoryFrame
        }
        FarmIllustratedSectionSurface {
            results.forEachIndexed { index, result ->
                if (index > 0) HorizontalDivider()
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag("goat-lab-result:${result.resultId}")) {
                    Text(LocalDate.ofEpochDay(result.occurredEpochDay).toString(), color = AnimalFarmTheme.colors.mutedInk)
                    Text(result.testName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(result.resultText)
                    result.cellsPerMl?.let { Text("%,d cells/mL".format(it)) }
                }
            }
        }
    }
}

/** FOS-GOAT-026 — treatments recorded for this goat, newest first. */
@Composable
internal fun GoatTreatmentHistoryScreen(goat: GoatSnapshot?, onBack: () -> Unit) {
    GoatHistoryFrame("Treatment history", "FOS-GOAT-026", goat, onBack) { subject ->
        val treatments = subject.treatmentHistory.sortedWith(
            compareByDescending<GoatTreatmentSample> { it.occurredAtEpochMillis }.thenBy { it.treatmentId },
        )
        if (treatments.isEmpty()) {
            AnimalFarmEmptyState("No treatments recorded for this goat on this device.")
            return@GoatHistoryFrame
        }
        FarmIllustratedSectionSurface {
            treatments.forEachIndexed { index, treatment ->
                if (index > 0) HorizontalDivider()
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag("goat-treatment:${treatment.treatmentId}")) {
                    Text(GoatRecordHistory.weightDay(treatment.occurredAtEpochMillis).toString(), color = AnimalFarmTheme.colors.mutedInk)
                    Text(treatment.productName ?: "Product not on this device", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(treatment.reason)
                    GoatHealthRecords.withdrawalDaysLabel(treatment.meatWithdrawalDays, treatment.milkWithdrawalDays)?.let { Text(it) }
                }
            }
        }
    }
}

/** FOS-GOAT-027 — active and ended withdrawal windows from this goat's treatments. */
@Composable
internal fun GoatWithdrawalStatusScreen(goat: GoatSnapshot?, today: LocalDate, onBack: () -> Unit) {
    GoatHistoryFrame("Withdrawal status", "FOS-GOAT-027", goat, onBack) { subject ->
        val active = GoatHealthRecords.activeWindows(subject, today)
        val ended = GoatHealthRecords.endedWindows(subject, today)
        if (active.isEmpty()) {
            AnimalFarmEmptyState("No active withdrawal windows recorded on this device.")
        } else {
            AnimalFarmWarningSurface {
                Text("Active withdrawal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                active.forEach { window ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("goat-withdrawal-active:${window.windowId}")) {
                        Text("${GoatHealthRecords.windowKindLabel(window.windowKind)} · ${window.product}", fontWeight = FontWeight.SemiBold)
                        Text("Until ${LocalDate.ofEpochDay(window.endsEpochDay)}")
                    }
                }
            }
        }
        if (ended.isNotEmpty()) {
            FarmIllustratedSectionSurface {
                Text("Ended", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                ended.forEach { window ->
                    GoatHistoryRow(
                        "${GoatHealthRecords.windowKindLabel(window.windowKind)} · ${window.product}",
                        "Ended ${LocalDate.ofEpochDay(window.endsEpochDay)}",
                        Modifier.testTag("goat-withdrawal-ended:${window.windowId}"),
                    )
                }
            }
        }
    }
}
