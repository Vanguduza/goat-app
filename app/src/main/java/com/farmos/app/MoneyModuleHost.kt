package com.farmos.app

import android.content.Intent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.farmos.core.database.MoneyRecordEntity
import com.farmos.core.database.MoneyTotalRow
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.ops.FarmCurrency
import com.farmos.domain.ops.RecordMoney
import com.farmos.feature.ops.ActivityCostScreen
import com.farmos.feature.ops.BudgetScreen
import com.farmos.feature.ops.CashFlowScreen
import com.farmos.feature.ops.EnterpriseProfitabilityScreen
import com.farmos.feature.ops.FinanceCategoryTotalView
import com.farmos.feature.ops.FinanceKindTotalView
import com.farmos.feature.ops.FinanceMonthFlowView
import com.farmos.feature.ops.FinanceReportDestination
import com.farmos.feature.ops.FinanceReportSummaryScreen
import com.farmos.feature.ops.FinanceReportsHub
import com.farmos.feature.ops.FinanceTransactionDetailScreen
import com.farmos.feature.ops.FinanceTransactionView
import com.farmos.feature.ops.MoneyCaptureScreen
import com.farmos.feature.ops.SpeciesCostScreen
import com.farmos.feature.ops.VarianceScreen
import com.farmos.feature.ops.financeDateText
import com.farmos.feature.ops.formatMoneyMinor
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import com.farmos.core.design.runSuspendCatching

private enum class MoneyHubPage {
    HOME,
    REPORTS,
    TRANSACTION_DETAIL,
    ENTERPRISE,
    SPECIES_COST,
    ACTIVITY_COST,
    CASH_FLOW,
    BUDGET,
    VARIANCE,
    FINANCE_REPORT,
    EXPORT,
}

private fun MoneyRecordEntity.toView() = FinanceTransactionView(
    id = id,
    kind = kind,
    category = categoryCode,
    amountMinor = amountMinor,
    currency = currency,
    epochDay = occurredEpochDay,
    note = note,
)

private fun MoneyTotalRow.toKindView() = FinanceKindTotalView(
    kind = kind,
    currency = currency,
    amountMinor = amountMinor,
    recordCount = records,
)

@Composable
fun MoneyModuleHost(
    farmId: String,
    ops: RoomOpsRepository,
    newContext: () -> LocalCommandContext,
    enqueueSync: () -> Unit,
    onBack: () -> Unit,
    loadCurrency: suspend () -> String = { FarmCurrency.DEFAULT_CODE },
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val database = remember(context) { (context.applicationContext as FarmOsApplication).database }
    val currency by rememberFarmCurrency(farmId, loadCurrency)
    var rows by remember(farmId) { mutableStateOf(emptyList<String>()) }
    var transactions by remember(farmId) { mutableStateOf(emptyList<FinanceTransactionView>()) }
    var allTransactions by remember(farmId) { mutableStateOf(emptyList<FinanceTransactionView>()) }
    var kindTotals by remember(farmId) { mutableStateOf(emptyList<FinanceKindTotalView>()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var hubPage by remember { mutableStateOf(MoneyHubPage.HOME) }
    var selectedTransactionId by remember { mutableStateOf<String?>(null) }

    val minorDigitsFor: (String) -> Int = { code ->
        runCatching { FarmCurrency.minorDigits(code) }.getOrDefault(2)
    }

    suspend fun refresh() {
        val records = withContext(Dispatchers.IO) { database.reports().moneyRecords(farmId) }
        val totals = withContext(Dispatchers.IO) { database.reports().moneyTotals(farmId) }
        val recent = records.takeLast(50).reversed()
        allTransactions = records.map { it.toView() }
        transactions = recent.map { it.toView() }
        rows = recent.map { row ->
            "${row.kind} ${row.categoryCode} ${row.amountMinor} ${row.currency}"
        }
        kindTotals = totals.map { it.toKindView() }
    }

    fun runWrite(block: suspend () -> Unit) {
        scope.launch {
            busy = true
            error = null
            runSuspendCatching {
                block()
                refresh()
            }.onSuccess {
                enqueueSync()
            }.onFailure { failure ->
                error = failure.message
            }
            busy = false
        }
    }

    LaunchedEffect(farmId) {
        runSuspendCatching { refresh() }
            .onFailure { error = it.message }
    }

    val backToHome = { hubPage = MoneyHubPage.HOME }
    val backToReports = { hubPage = MoneyHubPage.REPORTS }

    when (hubPage) {
        MoneyHubPage.HOME -> MoneyCaptureScreen(
            rows = rows,
            busy = busy,
            error = error,
            onRecord = { kind, category, amount, day ->
                runWrite {
                    val code = checkNotNull(currency) { "The farm currency is still loading" }
                    val amountMinor = amount.toScaledLongExact(FarmCurrency.minorDigits(code), "Amount")
                    ops.recordMoney(
                        RecordMoney(
                            recordId = UUID.randomUUID().toString(),
                            kind = kind.trim(),
                            categoryCode = category.trim(),
                            amountMinor = amountMinor,
                            currency = code,
                            occurredEpochDay = LocalDate.parse(day).toEpochDay(),
                        ),
                        newContext(),
                    )
                }
            },
            onBack = onBack,
            onOpenReports = { hubPage = MoneyHubPage.REPORTS },
            transactions = transactions,
            onOpenTransaction = { id ->
                selectedTransactionId = id
                hubPage = MoneyHubPage.TRANSACTION_DETAIL
            },
            minorDigitsFor = minorDigitsFor,
        )
        MoneyHubPage.REPORTS -> FinanceReportsHub(
            onOpen = { destination ->
                hubPage = when (destination) {
                    FinanceReportDestination.ENTERPRISE -> MoneyHubPage.ENTERPRISE
                    FinanceReportDestination.SPECIES_COST -> MoneyHubPage.SPECIES_COST
                    FinanceReportDestination.ACTIVITY_COST -> MoneyHubPage.ACTIVITY_COST
                    FinanceReportDestination.CASH_FLOW -> MoneyHubPage.CASH_FLOW
                    FinanceReportDestination.BUDGET -> MoneyHubPage.BUDGET
                    FinanceReportDestination.VARIANCE -> MoneyHubPage.VARIANCE
                    FinanceReportDestination.FINANCE_REPORT -> MoneyHubPage.FINANCE_REPORT
                    FinanceReportDestination.EXPORT -> MoneyHubPage.EXPORT
                }
            },
            onBack = backToHome,
        )
        MoneyHubPage.TRANSACTION_DETAIL -> {
            val selected = transactions.firstOrNull { it.id == selectedTransactionId }
            if (selected == null) {
                LaunchedEffect(Unit) { hubPage = MoneyHubPage.HOME }
            } else {
                FinanceTransactionDetailScreen(
                    transaction = selected,
                    minorDigitsFor = minorDigitsFor,
                    onBack = backToHome,
                )
            }
        }
        MoneyHubPage.ENTERPRISE -> {
            val all = allTransactions
            val income = all.filter { it.kind == "income" }
                .groupBy { it.category to it.currency }
                .map { (key, list) -> FinanceCategoryTotalView(key.first, key.second, list.sumOf { it.amountMinor }, list.size) }
            val expense = all.filter { it.kind == "expense" }
                .groupBy { it.category to it.currency }
                .map { (key, list) -> FinanceCategoryTotalView(key.first, key.second, list.sumOf { it.amountMinor }, list.size) }
            EnterpriseProfitabilityScreen(
                income = income,
                expense = expense,
                minorDigitsFor = minorDigitsFor,
                onBack = backToReports,
            )
        }
        MoneyHubPage.SPECIES_COST -> SpeciesCostScreen(onBack = backToReports)
        MoneyHubPage.ACTIVITY_COST -> {
            val expense = allTransactions.filter { it.kind == "expense" }
                .groupBy { it.category to it.currency }
                .map { (key, list) -> FinanceCategoryTotalView(key.first, key.second, list.sumOf { it.amountMinor }, list.size) }
            ActivityCostScreen(
                expense = expense,
                minorDigitsFor = minorDigitsFor,
                onBack = backToReports,
            )
        }
        MoneyHubPage.CASH_FLOW -> {
            val flows = allTransactions
                .groupBy { YearMonth.from(LocalDate.ofEpochDay(it.epochDay)).toString() to it.currency }
                .map { (key, list) ->
                    FinanceMonthFlowView(
                        yearMonth = key.first,
                        currency = key.second,
                        incomeMinor = list.filter { it.kind == "income" }.sumOf { it.amountMinor },
                        expenseMinor = list.filter { it.kind == "expense" }.sumOf { it.amountMinor },
                    )
                }
            CashFlowScreen(
                flows = flows,
                minorDigitsFor = minorDigitsFor,
                onBack = backToReports,
            )
        }
        MoneyHubPage.BUDGET -> BudgetScreen(onBack = backToReports)
        MoneyHubPage.VARIANCE -> VarianceScreen(onBack = backToReports)
        MoneyHubPage.FINANCE_REPORT -> FinanceReportSummaryScreen(
            totals = kindTotals,
            transactionCount = allTransactions.size,
            earliest = allTransactions.minOfOrNull { it.epochDay }?.let { financeDateText(it) },
            latest = allTransactions.maxOfOrNull { it.epochDay }?.let { financeDateText(it) },
            minorDigitsFor = minorDigitsFor,
            onBack = backToReports,
        )
        MoneyHubPage.EXPORT -> FinanceExportScreen(
            farmId = farmId,
            totals = kindTotals,
            transactions = allTransactions,
            minorDigitsFor = minorDigitsFor,
            onBack = backToReports,
        )
    }
}

/** Builds the finance report payload: totals plus every record, exact minor units, no float. */
private fun buildFinanceReportJson(
    farmId: String,
    totals: List<FinanceKindTotalView>,
    transactions: List<FinanceTransactionView>,
): String {
    val root = JSONObject()
    root.put("farmId", farmId)
    root.put("generatedAt", LocalDate.now().toString())
    val totalsJson = JSONArray()
    totals.forEach { row ->
        totalsJson.put(
            JSONObject()
                .put("kind", row.kind)
                .put("currency", row.currency)
                .put("amountMinor", row.amountMinor)
                .put("records", row.recordCount),
        )
    }
    root.put("totals", totalsJson)
    val recordsJson = JSONArray()
    transactions.forEach { tx ->
        recordsJson.put(
            JSONObject()
                .put("id", tx.id)
                .put("kind", tx.kind)
                .put("category", tx.category)
                .put("amountMinor", tx.amountMinor)
                .put("currency", tx.currency)
                .put("date", financeDateText(tx.epochDay))
                .put("note", tx.note),
        )
    }
    root.put("records", recordsJson)
    return root.toString(2)
}

/**
 * FOS-FIN-013 — Export: writes the finance report to app-private storage and appends a
 * farm-scoped entry to the export log (the same log the reports module uses). The log lists
 * every finance export made on this device.
 */
@Composable
private fun FinanceExportScreen(
    farmId: String,
    totals: List<FinanceKindTotalView>,
    transactions: List<FinanceTransactionView>,
    minorDigitsFor: (String) -> Int,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var logged by remember { mutableStateOf<List<ExportLogEntry>?>(null) }

    LaunchedEffect(farmId) {
        logged = withContext(Dispatchers.IO) {
            readExportLog(context, farmId).filter { it.kind == "finance_report" }
        }
    }

    FarmOperationalPage(
        screenId = "FOS-FIN-013",
        title = "Export finance report",
        subtitle = "Write the finance report to this device and log the export.",
        onBack = onBack,
        backLabel = "Reports",
    ) {
        FarmOperationalSection("Report contents") {
            Text("${transactions.size} transaction(s) across ${totals.size} kind/currency total(s).")
            Text(
                "The export is a JSON document with exact integer minor units; it never uses floating-point money.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        FarmOperationalSection("Export") {
            Button(
                onClick = {
                    scope.launch {
                        busy = true
                        message = null
                        try {
                            val dir = withContext(Dispatchers.IO) {
                                val target = File(context.filesDir, "finance_exports")
                                target.mkdirs()
                                val filename = "finance-report-${LocalDate.now()}-${UUID.randomUUID().toString().take(8)}.json"
                                File(target, filename).writeText(buildFinanceReportJson(farmId, totals, transactions))
                                appendExportLog(
                                    context,
                                    farmId,
                                    ExportLogEntry(
                                        id = UUID.randomUUID().toString(),
                                        kind = "finance_report",
                                        filename = filename,
                                        timestamp = System.currentTimeMillis(),
                                        count = transactions.size,
                                    ),
                                )
                                filename
                            }
                            logged = withContext(Dispatchers.IO) {
                                readExportLog(context, farmId).filter { it.kind == "finance_report" }
                            }
                            message = "Exported $dir"
                        } catch (e: Exception) {
                            message = e.message ?: "The export could not be written"
                        }
                        busy = false
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (busy) "Exporting" else "Export finance report") }
            message?.let { Text(it, fontWeight = FontWeight.Bold) }
        }
        FarmOperationalSection("Logged exports") {
            val entries = logged
            when {
                entries == null -> Text("Reading the export log")
                entries.isEmpty() -> Text("No finance exports have been made on this device yet.")
                else -> entries.forEach { entry ->
                    Text("${entry.filename} — ${entry.count} record(s), ${exportDateTimeText(entry.timestamp)}")
                    TextButton(
                        onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Finance report")
                                putExtra(Intent.EXTRA_TEXT, "Finance report export: ${entry.filename} (${entry.count} records)")
                            }
                            context.startActivity(Intent.createChooser(send, "Share finance report"))
                        },
                    ) { Text("Share") }
                }
            }
        }
    }
}
