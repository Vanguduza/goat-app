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
import com.farmos.core.database.BudgetEntity
import com.farmos.core.database.MoneyRecordEntity
import com.farmos.core.database.MoneyTotalRow
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalSection
import com.farmos.feature.ops.BudgetRevisionView
import com.farmos.feature.ops.BudgetView
import com.farmos.feature.ops.FinanceKindTotalView
import com.farmos.feature.ops.FinanceTransactionView
import java.io.File
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal enum class MoneyHubPage {
    HOME,
    REPORTS,
    TRANSACTION_DETAIL,
    ENTERPRISE,
    SPECIES_COST,
    ACTIVITY_COST,
    CASH_FLOW,
    BUDGET,
    BUDGET_CREATE,
    BUDGET_REVISE,
    VARIANCE,
    FINANCE_REPORT,
    EXPORT,
}

internal fun MoneyRecordEntity.toView() = FinanceTransactionView(
    id = id,
    kind = kind,
    category = categoryCode,
    amountMinor = amountMinor,
    currency = currency,
    epochDay = occurredEpochDay,
    note = note,
)

internal fun MoneyTotalRow.toKindView() = FinanceKindTotalView(
    kind = kind,
    currency = currency,
    amountMinor = amountMinor,
    recordCount = records,
)

internal fun BudgetEntity.toView() = BudgetView(
    budgetKey = budgetKey,
    version = version,
    name = name,
    kind = kind,
    categoryCode = categoryCode,
    periodStartYearMonth = periodStartYearMonth,
    periodEndYearMonth = periodEndYearMonth,
    amountMinor = amountMinor,
    currency = currency,
)

internal fun BudgetEntity.toRevisionView() = BudgetRevisionView(
    version = version,
    name = name,
    amountMinor = amountMinor,
    currency = currency,
    superseded = superseded,
    createdAtEpochMillis = createdAtEpochMillis,
)




/** Builds the finance report payload: totals plus every record, exact minor units, no float. */
internal fun buildFinanceReportJson(
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
                .put("date", LocalDate.ofEpochDay(tx.epochDay).toString())
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
internal fun FinanceExportScreen(
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
