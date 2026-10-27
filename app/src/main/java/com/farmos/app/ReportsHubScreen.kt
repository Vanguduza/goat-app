package com.farmos.app

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import com.farmos.core.design.*
import com.farmos.domain.ops.MetricResult

/** FOS-REPORT-001, P05/P10: find a report before inspecting a calculation. No data authority here. */
@Composable
internal fun ReportsScreen(
    metrics: List<MetricResult>?, failure: String?, canExport: Boolean, exporting: Boolean,
    exportMessage: String?, onExportHerdRegister: () -> Unit, onExportMoney: () -> Unit = {},
    onExportSummary: () -> Unit = {}, onOpenDetail: (ReportDetail) -> Unit = {}, onBack: () -> Unit,
) {
    var exportSheet by rememberSaveable { mutableStateOf(false) }
    var view by rememberSaveable { mutableStateOf("Reports") }
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(canExport) { if (!canExport) exportSheet = false }
    if (exportSheet && canExport) {
        ReportExportSheet(exporting, exportMessage, onExportHerdRegister, onExportMoney, onExportSummary) { exportSheet = false }
        return
    }
    FarmOperationalPage("FOS-REPORT-001", "Reports", "This farm · On-device records", FarmVisualClass.I3, onBack) {
        FarmSectionTabs(listOf("Reports", "Farm figures"), view, { view = it })
        if (view == "Reports") ReportTools(canExport, onOpenDetail)
        else {
            FarmOutlinedTextField(query, { query = it }, label = { Text("Find a figure") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("report-figures-query"))
        }
        when {
            failure != null -> AnimalFarmWarningSurface { Text(failure, Modifier.testTag("report-data-error")) }
            metrics == null -> Text("Reading farm records", Modifier.testTag("report-data-loading"))
            metrics.isEmpty() -> AnimalFarmEmptyState("No report figures are recorded on this device yet.")
            else -> {
                Text("${metrics.size} figures from local records", Modifier.testTag("report-data-ready"),
                    style = MaterialTheme.typography.bodySmall, color = AnimalFarmTheme.colors.mutedInk)
                if (view == "Farm figures") {
                    val needle = query.trim()
                    val matches = metrics.filter { it.definition.name.contains(needle, ignoreCase = true) ||
                        it.definition.scope.contains(needle, ignoreCase = true) || it.definition.id == needle }
                    if (matches.isEmpty()) AnimalFarmEmptyState("No matching figures. Change the name or scope in your search.")
                    else matches.forEach { ReportMetricSection(it) }
                }
            }
        }
        if (canExport) {
            FarmActionButton({ exportSheet = true }, Modifier.fillMaxWidth().testTag("report-open-export"), enabled = !exporting) {
                Text(if (exporting) "Exporting" else "Export records")
            }
        } else Text("Exports are made by farm management.", color = AnimalFarmTheme.colors.mutedInk)
    }
}

@Composable
private fun ReportTools(canExport: Boolean, onOpen: (ReportDetail) -> Unit) {
    fun action(entry: ReportDetailEntry) = FarmToolAction(entry.label, { onOpen(entry.detail) }, tag = "report-open:${entry.screenId}")
    val animals = setOf(ReportDetail.Animal, ReportDetail.Herd, ReportDetail.Health, ReportDetail.Breeding, ReportDetail.Genetics)
    val operations = setOf(ReportDetail.Production, ReportDetail.Inventory, ReportDetail.Operations, ReportDetail.Finance)
    val entries = reportDetailEntries.filter { canExport || it.detail != ReportDetail.Share }
    FarmModuleTools(listOf(
        FarmToolGroup("Animals", entries.filter { it.detail in animals }.map(::action)),
        FarmToolGroup("Production and resources", entries.filter { it.detail in operations }.map(::action)),
        FarmToolGroup("Documents", entries.filter { it.detail !in animals && it.detail !in operations }.map(::action)),
    ), title = "Report types")
}

/** Exact values and missing-record warnings stay visible; optional explanatory copy is disclosed. */
@Composable
internal fun ReportMetricSection(result: MetricResult) {
    var expanded by rememberSaveable(result.definition.id) { mutableStateOf(false) }
    FarmOperationalSection(result.definition.name) {
        Text(metricValueText(result), fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("report-metric:${result.definition.id}"))
        Text(result.definition.period, style = MaterialTheme.typography.bodySmall, color = AnimalFarmTheme.colors.mutedInk)
        Text(result.completeness, color = if (result.complete) AnimalFarmTheme.colors.mutedInk else AnimalFarmTheme.colors.critical)
        FarmActionButton({ expanded = !expanded }, Modifier.fillMaxWidth().testTag("report-calculation:${result.definition.id}")
            .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }) {
            Text(if (expanded) "Hide calculation and scope" else "Calculation and scope")
        }
        if (expanded) {
            Text("How: ${result.definition.formula}", color = AnimalFarmTheme.colors.mutedInk)
            Text("Scope: ${result.definition.scope}", color = AnimalFarmTheme.colors.mutedInk)
            Text("Included records: ${result.included} · Missing values: ${result.missing}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** FOS-REPORT-011, P13. Callbacks stay with the existing authority-checked export owner. */
@Composable
private fun ReportExportSheet(
    exporting: Boolean, message: String?, onHerd: () -> Unit, onMoney: () -> Unit,
    onSummary: () -> Unit, onBack: () -> Unit,
) {
    FarmOperationalPage("FOS-REPORT-011", "Export", "Files are written to a location you choose on this device.", FarmVisualClass.I3, onBack, backLabel = "Reports") {
        FarmOperationalSection("Herd register (CSV)") {
            Text("Every animal on this farm, one row each: species, tag, name, sex, status, date of birth, latest weight and poultry kind.")
            FarmPrimaryButton(onHerd, Modifier.fillMaxWidth().testTag("report-export-herd-register"), enabled = !exporting) {
                Text(if (exporting) "Exporting" else "Export herd register")
            }
        }
        FarmOperationalSection("Money records (CSV)") {
            Text("Every income and expense record, oldest first: date, kind, category, amount, currency and note.")
            FarmPrimaryButton(onMoney, Modifier.fillMaxWidth().testTag("report-export-money"), enabled = !exporting) {
                Text(if (exporting) "Exporting" else "Export money records")
            }
        }
        FarmOperationalSection("Farm summary (PDF)") {
            Text("Every figure on Reports with how it is worked out, its period and scope, and whether it is complete, for printing or sharing.")
            FarmPrimaryButton(onSummary, Modifier.fillMaxWidth().testTag("report-export-summary"), enabled = !exporting) {
                Text(if (exporting) "Exporting" else "Export farm summary")
            }
        }
        message?.let { Text(it, Modifier.testTag("report-export-message")) }
    }
}
