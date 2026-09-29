package com.farmos.feature.ops

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farmos.core.design.AnimalFarmEmptyState
import com.farmos.core.design.AnimalFarmTheme
import com.farmos.core.design.AnimalFarmWarningSurface
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.design.FarmVisualClass
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class HealthTreatmentView(
    val id: String,
    val speciesCode: String,
    /** Tag or name of the treated animal; null for a group/species-level treatment. */
    val subjectLabel: String?,
    /** Formulary product name; null when the formulary item is not on this device. */
    val productName: String?,
    val reason: String,
    val meatWithdrawalDays: Int?,
    val milkWithdrawalDays: Int?,
    val eggWithdrawalDays: Int?,
    val occurredAtEpochMillis: Long,
)

data class HealthWithdrawalView(
    val id: String,
    val treatmentId: String,
    val product: String,
    val windowKind: String,
    val endsEpochDay: Long,
)

data class HealthVetVisitView(
    val id: String,
    val speciesCode: String,
    val subjectLabel: String?,
    val reason: String,
    val attendingVet: String,
    val occurredEpochDay: Long,
)

data class HealthLabResultView(
    val id: String,
    val subjectLabel: String?,
    val testName: String,
    val resultText: String,
    val cellsPerMl: Int?,
    val occurredEpochDay: Long,
)

/** Local, farm-scoped health records for the read-only record pages. Nothing here prescribes. */
data class HealthReadModel(
    val treatments: List<HealthTreatmentView> = emptyList(),
    val withdrawals: List<HealthWithdrawalView> = emptyList(),
    val vetVisits: List<HealthVetVisitView> = emptyList(),
    val labResults: List<HealthLabResultView> = emptyList(),
)

internal object HealthRecords {
    /** Active through the end day, matching the farm home withdrawal count. */
    fun isActive(window: HealthWithdrawalView, today: LocalDate): Boolean = window.endsEpochDay >= today.toEpochDay()

    fun kindLabel(kind: String): String = when (kind.lowercase()) {
        "meat" -> "Meat"
        "milk" -> "Milk"
        "egg", "eggs" -> "Eggs"
        else -> kind
    }

    fun subject(label: String?, species: String?): String =
        label ?: species?.takeIf { it.isNotBlank() }?.let { "$it (no individual animal)" } ?: "No individual animal"

    fun day(epochMillis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    fun withdrawalDays(t: HealthTreatmentView): List<String> = listOfNotNull(
        t.meatWithdrawalDays?.let { "Meat $it day(s)" },
        t.milkWithdrawalDays?.let { "Milk $it day(s)" },
        t.eggWithdrawalDays?.let { "Eggs $it day(s)" },
    )
}

@Composable
private fun HealthRecordRow(primary: String, secondary: String, tag: String, onClick: (() -> Unit)? = null, detail: String? = null) {
    val base = Modifier.fillMaxWidth().heightIn(min = AnimalFarmTheme.minimumTouchDp.dp)
    Column(
        (if (onClick != null) base.clickable(role = Role.Button, onClick = onClick) else base).padding(vertical = 6.dp).testTag(tag),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(primary, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        Text(secondary)
        detail?.let { Text(it, color = AnimalFarmTheme.colors.mutedInk) }
    }
}

/** FOS-HEALTH-006 — recorded treatments, newest first. */
@Composable
internal fun HealthTreatmentListScreen(model: HealthReadModel, zone: ZoneId, onOpen: (String) -> Unit, onBack: () -> Unit) {
    FarmOperationalPage("FOS-HEALTH-006", "Treatment records", "Treatments recorded on this device. Records only; no dosing guidance.", onBack = onBack) {
        if (model.treatments.isEmpty()) {
            AnimalFarmEmptyState("No treatments recorded on this device.")
            return@FarmOperationalPage
        }
        FarmOperationalSection("Treatments · ${model.treatments.size}") {
            model.treatments.forEachIndexed { index, t ->
                if (index > 0) HorizontalDivider()
                HealthRecordRow(
                    t.productName ?: "Product not on this device",
                    HealthRecords.subject(t.subjectLabel, t.speciesCode),
                    "health-treatment:${t.id}",
                    onClick = { onOpen(t.id) },
                    detail = HealthRecords.day(t.occurredAtEpochMillis, zone).toString() + " · " + t.reason,
                )
            }
        }
    }
}

/** FOS-HEALTH-008 — one recorded treatment and the withdrawal windows it created. */
@Composable
internal fun HealthTreatmentDetailScreen(
    model: HealthReadModel,
    treatmentId: String?,
    today: LocalDate,
    zone: ZoneId,
    onOpenWithdrawal: (String) -> Unit,
    onBack: () -> Unit,
) {
    val t = model.treatments.firstOrNull { it.id == treatmentId }
    FarmOperationalPage("FOS-HEALTH-008", "Treatment record", t?.productName ?: "Treatment on this device", FarmVisualClass.I4, onBack = onBack) {
        if (t == null) {
            AnimalFarmEmptyState("This treatment is not on this device.")
            return@FarmOperationalPage
        }
        FarmOperationalSection("Record") {
            HealthRecordRow("Subject", HealthRecords.subject(t.subjectLabel, t.speciesCode), "health-treatment-subject")
            HealthRecordRow("Product", t.productName ?: "Product not on this device", "health-treatment-product")
            HealthRecordRow("Reason", t.reason, "health-treatment-reason")
            HealthRecordRow("Recorded", HealthRecords.day(t.occurredAtEpochMillis, zone).toString(), "health-treatment-date")
            val days = HealthRecords.withdrawalDays(t)
            HealthRecordRow("Withdrawal periods", if (days.isEmpty()) "None recorded" else days.joinToString(" · "), "health-treatment-withdrawal-days")
        }
        val windows = model.withdrawals.filter { it.treatmentId == t.id }.sortedWith(compareBy<HealthWithdrawalView> { it.endsEpochDay }.thenBy { it.id })
        FarmOperationalSection("Withdrawal windows") {
            if (windows.isEmpty()) Text("No withdrawal windows recorded for this treatment.", color = AnimalFarmTheme.colors.mutedInk)
            windows.forEach { w ->
                HealthRecordRow(
                    HealthRecords.kindLabel(w.windowKind),
                    (if (HealthRecords.isActive(w, today)) "Active until " else "Ended ") + LocalDate.ofEpochDay(w.endsEpochDay),
                    "health-treatment-window:${w.id}",
                    onClick = { onOpenWithdrawal(w.id) },
                )
            }
        }
    }
}

/** FOS-HEALTH-010 — one withdrawal window and its source treatment. */
@Composable
internal fun HealthWithdrawalDetailScreen(
    model: HealthReadModel,
    windowId: String?,
    today: LocalDate,
    onOpenTreatment: (String) -> Unit,
    onBack: () -> Unit,
) {
    val w = model.withdrawals.firstOrNull { it.id == windowId }
    FarmOperationalPage("FOS-HEALTH-010", "Withdrawal window", w?.product ?: "Withdrawal on this device", FarmVisualClass.I4, onBack = onBack) {
        if (w == null) {
            AnimalFarmEmptyState("This withdrawal window is not on this device.")
            return@FarmOperationalPage
        }
        val active = HealthRecords.isActive(w, today)
        if (active) {
            AnimalFarmWarningSurface {
                Text("Active ${HealthRecords.kindLabel(w.windowKind).lowercase()} withdrawal", fontWeight = FontWeight.Bold)
                Text("Until ${LocalDate.ofEpochDay(w.endsEpochDay)}")
            }
        }
        FarmOperationalSection("Window") {
            HealthRecordRow("Product", w.product, "health-withdrawal-product")
            HealthRecordRow("Applies to", HealthRecords.kindLabel(w.windowKind), "health-withdrawal-kind")
            HealthRecordRow(if (active) "Ends" else "Ended", LocalDate.ofEpochDay(w.endsEpochDay).toString(), "health-withdrawal-end")
            if (model.treatments.any { it.id == w.treatmentId }) {
                HealthRecordRow("Source", "Open treatment record", "health-withdrawal-source", onClick = { onOpenTreatment(w.treatmentId) })
            } else {
                HealthRecordRow("Source", "Treatment not on this device", "health-withdrawal-source")
            }
        }
    }
}

/** FOS-HEALTH-020 — recorded vet visits. */
@Composable
internal fun HealthVetVisitListScreen(model: HealthReadModel, onOpen: (String) -> Unit, onBack: () -> Unit) {
    FarmOperationalPage("FOS-HEALTH-020", "Vet visits", "Vet visits recorded on this device.", onBack = onBack) {
        if (model.vetVisits.isEmpty()) {
            AnimalFarmEmptyState("No vet visits recorded on this device.")
            return@FarmOperationalPage
        }
        FarmOperationalSection("Visits · ${model.vetVisits.size}") {
            model.vetVisits.forEachIndexed { index, v ->
                if (index > 0) HorizontalDivider()
                HealthRecordRow(
                    LocalDate.ofEpochDay(v.occurredEpochDay).toString() + " · " + v.attendingVet,
                    HealthRecords.subject(v.subjectLabel, v.speciesCode),
                    "health-vet-visit:${v.id}",
                    onClick = { onOpen(v.id) },
                    detail = v.reason,
                )
            }
        }
    }
}

/** FOS-HEALTH-022 — one recorded vet visit. */
@Composable
internal fun HealthVetVisitDetailScreen(model: HealthReadModel, visitId: String?, onBack: () -> Unit) {
    val v = model.vetVisits.firstOrNull { it.id == visitId }
    FarmOperationalPage("FOS-HEALTH-022", "Vet visit", v?.attendingVet ?: "Vet visit on this device", onBack = onBack) {
        if (v == null) {
            AnimalFarmEmptyState("This vet visit is not on this device.")
            return@FarmOperationalPage
        }
        FarmOperationalSection("Visit") {
            HealthRecordRow("Date", LocalDate.ofEpochDay(v.occurredEpochDay).toString(), "health-vet-visit-date")
            HealthRecordRow("Attending vet", v.attendingVet, "health-vet-visit-vet")
            HealthRecordRow("Subject", HealthRecords.subject(v.subjectLabel, v.speciesCode), "health-vet-visit-subject")
            HealthRecordRow("Reason", v.reason, "health-vet-visit-reason")
        }
    }
}

/** FOS-HEALTH-023 — recorded lab results. */
@Composable
internal fun HealthLabResultListScreen(model: HealthReadModel, onOpen: (String) -> Unit, onBack: () -> Unit) {
    FarmOperationalPage("FOS-HEALTH-023", "Lab results", "Lab results recorded on this device.", onBack = onBack) {
        if (model.labResults.isEmpty()) {
            AnimalFarmEmptyState("No lab results recorded on this device.")
            return@FarmOperationalPage
        }
        FarmOperationalSection("Results · ${model.labResults.size}") {
            model.labResults.forEachIndexed { index, r ->
                if (index > 0) HorizontalDivider()
                HealthRecordRow(
                    r.testName,
                    HealthRecords.subject(r.subjectLabel, null),
                    "health-lab-result:${r.id}",
                    onClick = { onOpen(r.id) },
                    detail = LocalDate.ofEpochDay(r.occurredEpochDay).toString(),
                )
            }
        }
    }
}

/** FOS-HEALTH-025 — one recorded lab result, shown as recorded without interpretation. */
@Composable
internal fun HealthLabResultDetailScreen(model: HealthReadModel, resultId: String?, onBack: () -> Unit) {
    val r = model.labResults.firstOrNull { it.id == resultId }
    FarmOperationalPage("FOS-HEALTH-025", "Lab result", r?.testName ?: "Lab result on this device", onBack = onBack) {
        if (r == null) {
            AnimalFarmEmptyState("This lab result is not on this device.")
            return@FarmOperationalPage
        }
        FarmOperationalSection("Result") {
            HealthRecordRow("Test", r.testName, "health-lab-test")
            HealthRecordRow("Subject", HealthRecords.subject(r.subjectLabel, null), "health-lab-subject")
            HealthRecordRow("Date", LocalDate.ofEpochDay(r.occurredEpochDay).toString(), "health-lab-date")
            HealthRecordRow("Result as recorded", r.resultText.ifBlank { "No result text recorded" }, "health-lab-result-text")
            r.cellsPerMl?.let { HealthRecordRow("Cell count", "%,d cells/mL".format(it), "health-lab-cells") }
        }
    }
}
