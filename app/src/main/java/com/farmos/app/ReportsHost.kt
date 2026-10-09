package com.farmos.app

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.HerdRegisterRow
import com.farmos.core.design.AnimalFarmEmptyState
import com.farmos.core.design.AnimalFarmTheme
import com.farmos.core.design.AnimalFarmWarningSurface
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.design.FarmVisualClass
import com.farmos.domain.ops.FarmCsv
import com.farmos.domain.ops.MetricDefinition
import com.farmos.domain.ops.MetricResult
import com.farmos.core.database.BirthTotalRow
import com.farmos.core.database.HealthTotalRow
import com.farmos.core.database.ProductionTotalRow
import com.farmos.core.database.InventoryTotalRow
import com.farmos.core.database.MoneyRecordEntity
import com.farmos.core.database.MoneyTotalRow
import com.farmos.domain.ops.FarmCurrency
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal val speciesNames = linkedMapOf("goat" to "Goats", "sheep" to "Sheep", "cattle" to "Cattle", "rabbit" to "Rabbits", "poultry" to "Poultry")
private val exitedStatuses = setOf("sold", "dead", "culled")

/**
 * Herd metrics from the exhaustive herd register (owner decision D-026, resolution R10). Each declares its
 * formula, unit, period and scope; a weight total counts animals without a weight as missing, never as zero.
 */
internal fun herdMetrics(rows: List<HerdRegisterRow>): List<MetricResult> {
    val bySpecies = rows.groupBy { it.speciesCode }
    val ordered = speciesNames.keys.filter { it in bySpecies } + bySpecies.keys.filterNot { it in speciesNames }.sorted()
    return ordered.flatMap { code ->
        val name = speciesNames[code] ?: code
        val active = bySpecies.getValue(code).filter { it.status == "active" }
        listOf(
            MetricResult.count(
                MetricDefinition("active-$code", "$name on the farm", "Count of $code records whose status is active", "animals", "Now", "This farm · $name"),
                active.size,
            ),
            MetricResult.sumOfKnown(
                MetricDefinition("live-weight-$code", "$name live weight", "Sum of each active animal's latest recorded weight", "kg", "Latest weight on record", "This farm · active $name"),
                active.map { it.latestWeightGrams },
            ),
        )
    } + MetricResult.count(
        MetricDefinition("exited", "Animals that have left", "Count of records whose status is sold, dead or culled", "animals", "All time", "This farm · all species"),
        rows.count { it.status in exitedStatuses },
    )
}

/**
 * Money metrics (D-026): income and expenses per currency over every money record. Currencies are never
 * added together or converted.
 */
internal fun moneyMetrics(totals: List<MoneyTotalRow>): List<MetricResult> = totals.map { row ->
    val income = row.kind == "income"
    MetricResult(
        MetricDefinition(
            "money-${row.kind}-${row.currency}", if (income) "Income in ${row.currency}" else "Expenses in ${row.currency}",
            "Sum of every ${row.kind} record in ${row.currency}", row.currency, "All time", "This farm · money records in ${row.currency}",
        ),
        row.amountMinor, row.records, 0,
    )
}

private val youngNames = mapOf("goat" to "Kids", "sheep" to "Lambs", "cattle" to "Calves", "rabbit" to "Kits")
private val birthEvents = mapOf("goat" to "kiddings", "sheep" to "lambings", "cattle" to "calvings", "rabbit" to "kindlings")

/** Births per species over every recorded birth event (D-026); species with none recorded are left out. */
internal fun birthMetrics(totals: List<BirthTotalRow>): List<MetricResult> = totals.filter { it.events > 0 }.flatMap { row ->
    val young = youngNames[row.speciesCode] ?: row.speciesCode
    val event = birthEvents[row.speciesCode] ?: "births"
    val scope = "This farm · every recorded ${event.dropLast(1)}"
    listOf(
        MetricResult.count(MetricDefinition("births-${row.speciesCode}", "${event.replaceFirstChar { it.uppercase() }} recorded", "Count of ${event.dropLast(1)} records", "records", "All time", scope), row.events),
        MetricResult(MetricDefinition("born-alive-${row.speciesCode}", "$young born alive", "Sum of live young on each ${event.dropLast(1)} record", "animals", "All time", scope), row.live, row.events, 0),
        MetricResult(MetricDefinition("born-dead-${row.speciesCode}", "$young born dead", "Sum of dead young on each ${event.dropLast(1)} record", "animals", "All time", scope), row.dead, row.events, 0),
    )
}

/** Health activity over every record, and the withdrawal windows running today (D-026). */
internal fun healthMetrics(totals: HealthTotalRow): List<MetricResult> = listOf(
    MetricResult.count(MetricDefinition("health-observations", "Health observations", "Count of health observation records", "records", "All time", "This farm · all species"), totals.observations),
    MetricResult.count(MetricDefinition("health-treatments", "Treatments", "Count of treatment records", "records", "All time", "This farm · all species"), totals.treatments),
    MetricResult.count(MetricDefinition("health-withdrawals", "Withdrawals running", "Count of withdrawal windows whose last day is today or later", "windows", "Today", "This farm · all species"), totals.activeWithdrawals),
)

internal val products = mapOf(
    "goat-milk" to Triple("Goat milk", "L", "goat milk record"),
    "cattle-milk" to Triple("Cattle milk", "L", "cattle milk record"),
    "sheep-wool" to Triple("Sheep wool (greasy)", "kg", "wool clip record"),
    "poultry-eggs" to Triple("Eggs", "eggs", "flock day record"),
)

/** Production over every record (D-026); a product with nothing recorded is left out rather than shown as zero. */
internal fun productionMetrics(totals: List<ProductionTotalRow>): List<MetricResult> = totals.filter { it.records > 0 }.map { row ->
    val (name, unit, record) = products[row.product] ?: Triple(row.product, "units", "record")
    MetricResult(MetricDefinition("production-${row.product}", name, "Sum over every $record", unit, "All time", "This farm"), row.amount, row.records, 0)
}

/** Stock items and those needing reorder, over every item (D-026). */
internal fun inventoryMetrics(totals: InventoryTotalRow): List<MetricResult> = if (totals.items == 0) emptyList() else listOf(
    MetricResult.count(MetricDefinition("inventory-items", "Stock items", "Count of inventory items", "items", "Now", "This farm"), totals.items),
    MetricResult.count(MetricDefinition("inventory-reorder", "Stock at or below reorder level", "Count of items whose quantity is at or below the reorder level set for them", "items", "Now", "This farm · items with a reorder level"), totals.atOrBelowReorder),
)

/** The metric value in its declared unit; weights are stored in grams and money in minor units. */
internal fun metricValueText(result: MetricResult): String {
    val unit = result.definition.unit
    return when {
        unit == "kg" -> "%.1f kg".format(result.value / 1000.0)
        unit == "L" -> "%.1f L".format(result.value / 1000.0)
        FarmCurrency.isRecordable(unit) -> "${BigDecimal.valueOf(result.value, FarmCurrency.minorDigits(unit)).toPlainString()} $unit"
        else -> "${result.value} $unit"
    }
}

/** Every money record as CSV, oldest first, amounts in each record's own currency. */
internal fun moneyRecordsCsv(rows: List<MoneyRecordEntity>): String = FarmCsv.write(
    listOf("Date", "Kind", "Category", "Amount", "Currency", "Note"),
    rows.map { row ->
        listOf(
            LocalDate.ofEpochDay(row.occurredEpochDay).toString(), row.kind, row.categoryCode,
            BigDecimal.valueOf(row.amountMinor, FarmCurrency.minorDigits(row.currency)).toPlainString(), row.currency, row.note,
        )
    },
)

/** The herd register as CSV: every animal on the farm, one row each. */
internal fun herdRegisterCsv(rows: List<HerdRegisterRow>): String = FarmCsv.write(
    listOf("Species", "Tag", "Name", "Sex", "Status", "Born", "Latest weight (kg)", "Poultry kind"),
    rows.map { row ->
        listOf(
            row.speciesCode, row.tag, row.name, row.sex.lowercase(), row.status,
            row.dateOfBirthEpochDay?.let { LocalDate.ofEpochDay(it).toString() },
            row.latestWeightGrams?.let { "%.3f".format(it / 1000.0) },
            row.poultryKindCode,
        )
    },
)

/** Every metric on Reports, read from every record on this device (D-026). */
internal suspend fun farmMetrics(database: FarmOsDatabase, farmId: String, today: LocalDate): List<MetricResult> {
    val reports = database.reports()
    return herdMetrics(reports.herdRegister(farmId)) + birthMetrics(reports.birthTotals(farmId)) + healthMetrics(reports.healthTotals(farmId, today.toEpochDay())) +
        productionMetrics(reports.productionTotals(farmId)) + inventoryMetrics(reports.inventoryTotals(farmId)) + moneyMetrics(reports.moneyTotals(farmId))
}

/** Reports (FOS-REPORT-001) and the export sheet (FOS-REPORT-011) for one farm. */
@Composable
internal fun ReportsModuleHost(database: FarmOsDatabase, farmId: String, canExport: Boolean, onBack: () -> Unit) {
    // A different farm starts a new report session; no selected record, draft or callback crosses it.
    key(farmId) { FarmReportsSession(database, farmId, canExport, onBack) }
}

@Composable
private fun FarmReportsSession(database: FarmOsDatabase, farmId: String, canExport: Boolean, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val exportAllowed by rememberUpdatedState(canExport)
    val context = LocalContext.current
    var metrics by remember(farmId) { mutableStateOf<List<MetricResult>?>(null) }
    var pending by remember { mutableStateOf(REPORT_HERD) }
    var failure by remember { mutableStateOf<String?>(null) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    var exporting by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<ReportDetail?>(null) }
    LaunchedEffect(canExport) {
        if (!canExport && detail == ReportDetail.Share) detail = null
    }
    LaunchedEffect(farmId) {
        try {
            metrics = farmMetrics(database, farmId, LocalDate.now())
        } catch (e: Exception) {
            failure = e.message ?: "Records could not be read"
        }
    }
    val onChosen: (Uri?) -> Unit = { uri ->
        if (!exportAllowed) {
            exportMessage = "Exports are made by farm management."
        } else if (uri == null) {
            exportMessage = "Export cancelled; nothing was written."
        } else {
            scope.launch {
                exporting = true
                exportMessage = try {
                    val outcome = when (pending) {
                        REPORT_MONEY -> {
                            val records = database.reports().moneyRecords(farmId)
                            ExportOutcome(
                                writer = { it.write(moneyRecordsCsv(records).toByteArray(Charsets.UTF_8)) },
                                message = "Money records exported: ${records.size} record(s).",
                                kind = "money-records", filename = "money-records-${LocalDate.now()}.csv", count = records.size,
                            )
                        }
                        REPORT_SUMMARY -> {
                            val today = LocalDate.now()
                            val summary = farmMetrics(database, farmId, today)
                            ExportOutcome(
                                writer = { writeFarmSummaryPdf(context, summary, today, it) },
                                message = "Farm summary exported: ${summary.size} figure(s).",
                                kind = "farm-summary", filename = "farm-summary-$today.pdf", count = summary.size,
                            )
                        }
                        else -> {
                            val register = database.reports().herdRegister(farmId)
                            ExportOutcome(
                                writer = { it.write(herdRegisterCsv(register).toByteArray(Charsets.UTF_8)) },
                                message = "Herd register exported: ${register.size} animal(s).",
                                kind = "herd-register", filename = "herd-register-${LocalDate.now()}.csv", count = register.size,
                            )
                        }
                    }
                    withContext(Dispatchers.IO) {
                        check(exportAllowed) { "Exports are made by farm management." }
                        requireNotNull(context.contentResolver.openOutputStream(uri)) { "The chosen location cannot be written" }.use(outcome.writer)
                        // The export already succeeded; a log failure must not rewrite its message.
                        runCatching {
                            appendExportLog(
                                context, farmId,
                                ExportLogEntry(System.currentTimeMillis().toString(), outcome.kind, outcome.filename, System.currentTimeMillis(), outcome.count),
                            )
                        }
                    }
                    outcome.message
                } catch (e: Exception) {
                    "Export failed: ${e.message ?: "the file could not be written"}"
                }
                exporting = false
            }
        }
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv"), onChosen)
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf"), onChosen)
    val backToHub: () -> Unit = { detail = null }
    // Do not render a stale share action during the frame in which permission changes.
    val permittedDetail = detail.takeUnless { it == ReportDetail.Share && !canExport }
    when (val current = permittedDetail) {
        null -> ReportsScreen(
            metrics = metrics,
            failure = failure,
            canExport = canExport,
            exporting = exporting,
            exportMessage = exportMessage,
            onExportHerdRegister = { pending = REPORT_HERD; csvLauncher.launch("herd-register-${LocalDate.now()}.csv") },
            onExportMoney = { pending = REPORT_MONEY; csvLauncher.launch("money-records-${LocalDate.now()}.csv") },
            onExportSummary = { pending = REPORT_SUMMARY; pdfLauncher.launch("farm-summary-${LocalDate.now()}.pdf") },
            onOpenDetail = { target ->
                if (target != ReportDetail.Share || exportAllowed) detail = target
            },
            onBack = onBack,
        )
        ReportDetail.Animal -> AnimalReportScreen(database, farmId, onBack = backToHub)
        ReportDetail.Herd -> HerdReportScreen(database, farmId, onBack = backToHub)
        ReportDetail.Health -> HealthReportScreen(database, farmId, onBack = backToHub)
        ReportDetail.Production -> ProductionReportScreen(database, farmId, onBack = backToHub)
        ReportDetail.Finance -> FinanceReportScreen(database, farmId, onBack = backToHub)
        ReportDetail.Inventory -> InventoryReportScreen(database, farmId, onBack = backToHub)
        ReportDetail.Breeding -> BreedingReportScreen(database, farmId, onBack = backToHub)
        ReportDetail.Genetics -> GeneticsReportScreen(database, farmId, onBack = backToHub)
        ReportDetail.Operations -> OperationsReportScreen(database, farmId, onBack = backToHub)
        ReportDetail.Documents -> GeneratedDocumentsScreen(farmId, onOpenDocument = { id -> detail = ReportDetail.Document(id) }, onBack = backToHub)
        is ReportDetail.Document -> ExportDocumentScreen(farmId, entryId = current.entryId, onBack = { detail = ReportDetail.Documents })
        ReportDetail.Share -> ShareReportScreen(database, farmId, canShare = { exportAllowed }, onBack = backToHub)
        ReportDetail.Movements -> MovementCertificatesScreen(database, farmId, onBack = backToHub)
    }
}

@Composable
internal fun ReportsScreen(
    metrics: List<MetricResult>?,
    failure: String?,
    canExport: Boolean,
    exporting: Boolean,
    exportMessage: String?,
    onExportHerdRegister: () -> Unit,
    onExportMoney: () -> Unit = {},
    onExportSummary: () -> Unit = {},
    onOpenDetail: (ReportDetail) -> Unit = {},
    onBack: () -> Unit,
) {
    var exportSheet by remember { mutableStateOf(false) }
    LaunchedEffect(canExport) { if (!canExport) exportSheet = false }
    if (exportSheet && canExport) {
        FarmOperationalPage("FOS-REPORT-011", "Export", "Files are written to a location you choose on this device.", FarmVisualClass.I3, { exportSheet = false }, backLabel = "Reports") {
            FarmOperationalSection("Herd register (CSV)") {
                Text("Every animal on this farm, one row each: species, tag, name, sex, status, date of birth, latest weight and poultry kind.")
                Button(onClick = onExportHerdRegister, enabled = !exporting, modifier = Modifier.fillMaxWidth().testTag("report-export-herd-register")) {
                    Text(if (exporting) "Exporting" else "Export herd register")
                }
            }
            FarmOperationalSection("Money records (CSV)") {
                Text("Every income and expense record, oldest first: date, kind, category, amount, currency and note.")
                Button(onClick = onExportMoney, enabled = !exporting, modifier = Modifier.fillMaxWidth().testTag("report-export-money")) {
                    Text(if (exporting) "Exporting" else "Export money records")
                }
            }
            FarmOperationalSection("Farm summary (PDF)") {
                Text("Every figure on Reports with how it is worked out, its period and scope, and whether it is complete, for printing or sharing.")
                Button(onClick = onExportSummary, enabled = !exporting, modifier = Modifier.fillMaxWidth().testTag("report-export-summary")) {
                    Text(if (exporting) "Exporting" else "Export farm summary")
                }
            }
            exportMessage?.let { Text(it, modifier = Modifier.testTag("report-export-message")) }
        }
        return
    }
    FarmOperationalPage("FOS-REPORT-001", "Reports", "Figures from every record on this device. Each says how it is worked out.", FarmVisualClass.I3, onBack) {
        when {
            failure != null -> AnimalFarmWarningSurface { Text(failure) }
            metrics == null -> Text("Reading farm records")
            metrics.isEmpty() -> AnimalFarmEmptyState("No animals are recorded on this device yet.")
            else -> metrics.forEach { result ->
                FarmOperationalSection(result.definition.name) {
                    Text(metricValueText(result), fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("report-metric:${result.definition.id}"))
                    Text("How: ${result.definition.formula}", color = AnimalFarmTheme.colors.mutedInk)
                    Text("Period: ${result.definition.period} · Scope: ${result.definition.scope}", color = AnimalFarmTheme.colors.mutedInk)
                    Text(result.completeness, color = if (result.complete) AnimalFarmTheme.colors.mutedInk else AnimalFarmTheme.colors.critical)
                }
            }
        }
        FarmOperationalSection("Report types", "One screen per report. Every figure is read from the records on this device.") {
            reportDetailEntries.filter { canExport || it.detail != ReportDetail.Share }.forEach { entry ->
                TextButton(onClick = { onOpenDetail(entry.detail) }, modifier = Modifier.fillMaxWidth().testTag("report-open:${entry.screenId}")) {
                    Text(entry.label)
                }
            }
        }
        if (canExport) {
            TextButton(onClick = { exportSheet = true }, modifier = Modifier.fillMaxWidth().testTag("report-open-export")) { Text("Export records") }
        } else {
            Text("Exports are made by farm management.", color = AnimalFarmTheme.colors.mutedInk)
        }
    }
}

private const val REPORT_HERD = "herd"
private const val REPORT_MONEY = "money"
private const val REPORT_SUMMARY = "summary"

/** One finished export: how to write it, what to tell the user, and what to log. */
private data class ExportOutcome(
    val writer: (java.io.OutputStream) -> Unit,
    val message: String,
    val kind: String,
    val filename: String,
    val count: Int,
)
