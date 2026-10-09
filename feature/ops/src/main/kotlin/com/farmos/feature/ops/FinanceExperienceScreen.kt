package com.farmos.feature.ops

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalRows
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.design.FarmVisualClass
import java.math.BigDecimal
import java.time.LocalDate

private enum class FinancePage { DASHBOARD, TRANSACTIONS, INCOME, EXPENSE }

/** One money record for display, mapped from the local money_records table. Currencies are never mixed. */
data class FinanceTransactionView(
    val id: String,
    val kind: String,
    val category: String,
    val amountMinor: Long,
    val currency: String,
    val epochDay: Long,
    val note: String?,
)

/** Exhaustive total for one kind (income/expense) in one currency over every local money record. */
data class FinanceKindTotalView(
    val kind: String,
    val currency: String,
    val amountMinor: Long,
    val recordCount: Int,
)

/** Exhaustive total for one category code in one currency over every local money record. */
data class FinanceCategoryTotalView(
    val category: String,
    val currency: String,
    val amountMinor: Long,
    val recordCount: Int,
)

/** One calendar month of money movement in one currency; net is income minus expense. */
data class FinanceMonthFlowView(
    val yearMonth: String,
    val currency: String,
    val incomeMinor: Long,
    val expenseMinor: Long,
)

/** Report destinations behind the finance hub. */
enum class FinanceReportDestination {
    ENTERPRISE,
    SPECIES_COST,
    ACTIVITY_COST,
    CASH_FLOW,
    BUDGET,
    VARIANCE,
    FINANCE_REPORT,
    EXPORT,
}

/**
 * Exact decimal rendering of integer minor units. Uses BigDecimal point shifting only;
 * this function never uses floating-point money.
 */
fun formatMoneyMinor(amountMinor: Long, currency: String, minorDigits: Int): String {
    val scaled = BigDecimal(amountMinor).movePointLeft(minorDigits).toPlainString()
    return "$scaled $currency"
}

internal fun financeDateText(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).toString()

/** FOS-FIN-001/002/004/005 — exact-money operational reference family. */
@Composable
fun MoneyCaptureScreen(
    rows: List<String>,
    busy: Boolean,
    error: String?,
    onRecord: (kind: String, category: String, amount: String, day: String) -> Unit,
    onBack: () -> Unit,
    onOpenReports: () -> Unit = {},
    transactions: List<FinanceTransactionView> = emptyList(),
    onOpenTransaction: (String) -> Unit = {},
    minorDigitsFor: (String) -> Int = { 2 },
) {
    var page by remember { mutableStateOf(FinancePage.DASHBOARD) }
    when (page) {
        FinancePage.DASHBOARD -> FinanceDashboard(rows, error, onOpen = { page = it }, onOpenReports = onOpenReports, onBack = onBack)
        FinancePage.TRANSACTIONS -> FinanceTransactions(rows, transactions, error, onOpenTransaction, minorDigitsFor) { page = FinancePage.DASHBOARD }
        FinancePage.INCOME -> FinanceRecord("income", busy, error, onRecord) { page = FinancePage.DASHBOARD }
        FinancePage.EXPENSE -> FinanceRecord("expense", busy, error, onRecord) { page = FinancePage.DASHBOARD }
    }
}

@Composable
private fun FinanceDashboard(
    rows: List<String>,
    error: String?,
    onOpen: (FinancePage) -> Unit,
    onOpenReports: () -> Unit,
    onBack: () -> Unit,
) {
    FarmOperationalPage(
        screenId = "FOS-FIN-001",
        title = "Finance",
        subtitle = "Income and expense records on this device.",
        visualClass = FarmVisualClass.I2,
        onBack = onBack,
    ) {
        FarmOperationalSection("Farm money") {
            Text("${rows.size} recent transaction(s)", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Values are persisted as integer minor units; this view never uses floating-point money.")
        }
        FarmOperationalSection("Quick actions") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Button(onClick = { onOpen(FinancePage.INCOME) }) { Text("Record income") }
                Button(onClick = { onOpen(FinancePage.EXPENSE) }) { Text("Record expense") }
            }
            TextButton(onClick = { onOpen(FinancePage.TRANSACTIONS) }) { Text("View transactions") }
            TextButton(onClick = onOpenReports) { Text("Reports and analysis") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun FinanceTransactions(
    rows: List<String>,
    transactions: List<FinanceTransactionView>,
    error: String?,
    onOpenTransaction: (String) -> Unit,
    minorDigitsFor: (String) -> Int,
    onBack: () -> Unit,
) {
    FarmOperationalPage(
        screenId = "FOS-FIN-002",
        title = "Transactions",
        subtitle = "Recent farm income and expense records. Tap a row for detail.",
        onBack = onBack,
        backLabel = "Finance",
    ) {
        if (transactions.isNotEmpty()) {
            FarmOperationalSection("Current records") {
                transactions.forEach { tx ->
                    TextButton(onClick = { onOpenTransaction(tx.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "${tx.kind} ${tx.category} " +
                                "${formatMoneyMinor(tx.amountMinor, tx.currency, minorDigitsFor(tx.currency))} " +
                                financeDateText(tx.epochDay),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        } else {
            FarmOperationalRows(rows, "No money records yet", "Income and expenses will appear here after local capture.")
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun FinanceRecord(
    kind: String,
    busy: Boolean,
    error: String?,
    onRecord: (kind: String, category: String, amount: String, day: String) -> Unit,
    onBack: () -> Unit,
) {
    var category by remember { mutableStateOf(if (kind == "income") "sales" else "feed") }
    var amount by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    val income = kind == "income"
    FarmOperationalPage(
        screenId = if (income) "FOS-FIN-004" else "FOS-FIN-005",
        title = if (income) "Record income" else "Record expense",
        subtitle = if (income) "Capture money earned by the farm." else "Capture a farm operating cost.",
        onBack = onBack,
        backLabel = "Finance",
    ) {
        FarmOperationalSection("Details", "Enter a decimal amount such as 12.50. Storage converts it to exact minor units.") {
            OutlinedTextField(category, {
                category = it
            }, label = { Text("Category") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            OutlinedTextField(amount, {
                amount = it
            }, label = { Text("Amount") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            OutlinedTextField(
                day,
                { day = it },
                label = { Text("Date") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                singleLine = true,
            )
            Button(
                onClick = { onRecord(kind, category, amount, day) },
                enabled = !busy && category.isNotBlank() && amount.isNotBlank() && runCatching { LocalDate.parse(day) }.isSuccess,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (income) "Save income" else "Save expense") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

/**
 * FOS-FIN-001 — finance reports hub: entry to the finance report family.
 * Each destination opens its own tagged screen; back returns here, then to Finance.
 */
@Composable
fun FinanceReportsHub(
    onOpen: (FinanceReportDestination) -> Unit,
    onBack: () -> Unit,
) {
    FarmOperationalPage(
        screenId = "FOS-FIN-001",
        title = "Finance reports",
        subtitle = "Read-only analysis over the money records on this device.",
        onBack = onBack,
        backLabel = "Finance",
    ) {
        FarmOperationalSection("Reports") {
            TextButton(onClick = { onOpen(FinanceReportDestination.ENTERPRISE) }, modifier = Modifier.fillMaxWidth()) {
                Text("Enterprise profitability", modifier = Modifier.fillMaxWidth())
            }
            TextButton(onClick = { onOpen(FinanceReportDestination.SPECIES_COST) }, modifier = Modifier.fillMaxWidth()) {
                Text("Species cost", modifier = Modifier.fillMaxWidth())
            }
            TextButton(onClick = { onOpen(FinanceReportDestination.ACTIVITY_COST) }, modifier = Modifier.fillMaxWidth()) {
                Text("Activity cost", modifier = Modifier.fillMaxWidth())
            }
            TextButton(onClick = { onOpen(FinanceReportDestination.CASH_FLOW) }, modifier = Modifier.fillMaxWidth()) {
                Text("Cash flow", modifier = Modifier.fillMaxWidth())
            }
            TextButton(onClick = { onOpen(FinanceReportDestination.BUDGET) }, modifier = Modifier.fillMaxWidth()) {
                Text("Budget", modifier = Modifier.fillMaxWidth())
            }
            TextButton(onClick = { onOpen(FinanceReportDestination.VARIANCE) }, modifier = Modifier.fillMaxWidth()) {
                Text("Budget variance", modifier = Modifier.fillMaxWidth())
            }
            TextButton(onClick = { onOpen(FinanceReportDestination.FINANCE_REPORT) }, modifier = Modifier.fillMaxWidth()) {
                Text("Finance report", modifier = Modifier.fillMaxWidth())
            }
            TextButton(onClick = { onOpen(FinanceReportDestination.EXPORT) }, modifier = Modifier.fillMaxWidth()) {
                Text("Export", modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** FOS-FIN-003 — Transaction Detail: one money record's kind, category, exact amount, date and journal reference. */
@Composable
fun FinanceTransactionDetailScreen(
    transaction: FinanceTransactionView,
    minorDigitsFor: (String) -> Int,
    onBack: () -> Unit,
) {
    FarmOperationalPage(
        screenId = "FOS-FIN-003",
        title = "Transaction detail",
        subtitle = "One money record exactly as stored.",
        onBack = onBack,
        backLabel = "Transactions",
    ) {
        FarmOperationalSection("Record") {
            Text("Kind: ${transaction.kind}", fontWeight = FontWeight.Bold)
            Text("Category: ${transaction.category}")
            Text("Amount: ${formatMoneyMinor(transaction.amountMinor, transaction.currency, minorDigitsFor(transaction.currency))}")
            Text("Date: ${financeDateText(transaction.epochDay)}")
            Text("Journal reference: ${transaction.id}")
            transaction.note?.takeIf { it.isNotBlank() }?.let { Text("Note: $it") }
        }
        FarmOperationalSection("Integrity") {
            Text("Amounts are stored as integer minor units; no floating-point money is used anywhere in this record.")
        }
    }
}

/**
 * FOS-FIN-006 — Enterprise Profitability: income versus expense per enterprise category,
 * per currency. Profit is income minus expense in integer minor units; currencies are never added.
 * [income] and [expense] are per-category totals split by kind; profit is their difference.
 */
@Composable
fun EnterpriseProfitabilityScreen(
    income: List<FinanceCategoryTotalView>,
    expense: List<FinanceCategoryTotalView>,
    minorDigitsFor: (String) -> Int,
    onBack: () -> Unit,
) {
    val keys = (income.map { it.category to it.currency } + expense.map { it.category to it.currency })
        .toSortedSet(compareBy({ it.first }, { it.second }))
    val incomeMap = income.associateBy { it.category to it.currency }
    val expenseMap = expense.associateBy { it.category to it.currency }
    FarmOperationalPage(
        screenId = "FOS-FIN-006",
        title = "Enterprise profitability",
        subtitle = "Income versus expense per enterprise category, per currency.",
        onBack = onBack,
        backLabel = "Reports",
    ) {
        if (keys.isEmpty()) {
            FarmOperationalSection("No data") {
                Text("No money records yet. Profitability appears here after local income and expense capture.")
            }
            return@FarmOperationalPage
        }
        FarmOperationalSection("By enterprise") {
            Text(
                "Profit is income minus expense in exact minor units. " +
                    "Currencies are reported separately and never added together.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        keys.forEach { (category, currency) ->
            val inc = incomeMap[category to currency]?.amountMinor ?: 0L
            val exp = expenseMap[category to currency]?.amountMinor ?: 0L
            val profit = inc - exp
            val digits = minorDigitsFor(currency)
            FarmOperationalSection("$category ($currency)") {
                Text("Income: ${formatMoneyMinor(inc, currency, digits)}")
                Text("Expense: ${formatMoneyMinor(exp, currency, digits)}")
                Text(
                    "Profit: ${formatMoneyMinor(profit, currency, digits)}",
                    fontWeight = FontWeight.Bold,
                    color = if (profit >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * FOS-FIN-007 — Species Cost: cost aggregates grouped by species.
 *
 * GENUINE GAP — not implemented: the money_records table carries no species linkage
 * (no species_code/animal_id column), so costs cannot be attributed to a species from
 * recorded data. Required domain piece: a species (or enterprise) attribution field on
 * money records plus a governed command/validator change. This screen fails closed
 * instead of inventing an attribution.
 */
@Composable
fun SpeciesCostScreen(onBack: () -> Unit) {
    FarmOperationalPage(
        screenId = "FOS-FIN-007",
        title = "Species cost",
        subtitle = "Not available in this build.",
        onBack = onBack,
        backLabel = "Reports",
    ) {
        FarmOperationalSection("Unavailable") {
            Text(
                "Species cost needs money records linked to a species, and the local money records " +
                    "carry no species field. No cost is shown rather than a guessed attribution.",
                fontWeight = FontWeight.Bold,
            )
            Text("Required: a species attribution field on money records with a governed command change.")
        }
    }
}

/**
 * FOS-FIN-008 — Activity Cost: expense totals grouped by activity (category code),
 * per currency, over every local money record.
 */
@Composable
fun ActivityCostScreen(
    expense: List<FinanceCategoryTotalView>,
    minorDigitsFor: (String) -> Int,
    onBack: () -> Unit,
) {
    FarmOperationalPage(
        screenId = "FOS-FIN-008",
        title = "Activity cost",
        subtitle = "Expense totals by activity category, per currency.",
        onBack = onBack,
        backLabel = "Reports",
    ) {
        if (expense.isEmpty()) {
            FarmOperationalSection("No data") {
                Text("No expense records yet. Activity costs appear here after local expense capture.")
            }
            return@FarmOperationalPage
        }
        expense.sortedWith(compareBy({ it.currency }, { it.category })).forEach { row ->
            FarmOperationalSection("${row.category} (${row.currency})") {
                Text("Total: ${formatMoneyMinor(row.amountMinor, row.currency, minorDigitsFor(row.currency))}", fontWeight = FontWeight.Bold)
                Text("${row.recordCount} record(s)")
            }
        }
    }
}

/**
 * FOS-FIN-009 — Cash Flow: money in versus money out per calendar month, per currency.
 * Net is income minus expense in exact minor units.
 */
@Composable
fun CashFlowScreen(
    flows: List<FinanceMonthFlowView>,
    minorDigitsFor: (String) -> Int,
    onBack: () -> Unit,
) {
    FarmOperationalPage(
        screenId = "FOS-FIN-009",
        title = "Cash flow",
        subtitle = "Money in versus money out per calendar month, per currency.",
        onBack = onBack,
        backLabel = "Reports",
    ) {
        if (flows.isEmpty()) {
            FarmOperationalSection("No data") {
                Text("No money records yet. Cash flow appears here after local income and expense capture.")
            }
            return@FarmOperationalPage
        }
        flows.sortedWith(compareBy({ it.yearMonth }, { it.currency })).forEach { flow ->
            val net = flow.incomeMinor - flow.expenseMinor
            val digits = minorDigitsFor(flow.currency)
            FarmOperationalSection("${flow.yearMonth} (${flow.currency})") {
                Text("In: ${formatMoneyMinor(flow.incomeMinor, flow.currency, digits)}")
                Text("Out: ${formatMoneyMinor(flow.expenseMinor, flow.currency, digits)}")
                Text(
                    "Net: ${formatMoneyMinor(net, flow.currency, digits)}",
                    fontWeight = FontWeight.Bold,
                    color = if (net >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** One active budget line for display, mapped from the local budgets table. Currencies are never mixed. */
data class BudgetView(
    val budgetKey: String,
    val version: Long,
    val name: String,
    val kind: String,
    val categoryCode: String,
    val periodStartYearMonth: String,
    val periodEndYearMonth: String,
    val amountMinor: Long,
    val currency: String,
)

/** One superseded-or-current revision of a budget line, for the audit trail. */
data class BudgetRevisionView(
    val version: Long,
    val name: String,
    val amountMinor: Long,
    val currency: String,
    val superseded: Boolean,
    val createdAtEpochMillis: Long,
)

/**
 * FOS-FIN-010 — Budget.
 *
 * Active budget lines on this farm, each scoped to exactly one (kind, category,
 * currency, year-month period). Amounts are integer minor units. Revising a
 * budget records a new revision; prior revisions are kept as the audit trail.
 */
@Composable
fun BudgetScreen(
    budgets: List<BudgetView>,
    minorDigitsFor: (String) -> Int,
    onCreateBudget: () -> Unit,
    onReviseBudget: (BudgetView) -> Unit,
    onBack: () -> Unit,
) {
    FarmOperationalPage(
        screenId = "FOS-FIN-010",
        title = "Budget",
        subtitle = "Planned spending and income per category and period.",
        onBack = onBack,
        backLabel = "Reports",
    ) {
        FarmOperationalSection("Budgets") {
            if (budgets.isEmpty()) {
                Text("No budgets recorded yet.")
            }
            budgets.forEach { budget ->
                val digits = minorDigitsFor(budget.currency)
                TextButton(onClick = { onReviseBudget(budget) }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "${budget.name} — ${formatMoneyMinor(budget.amountMinor, budget.currency, digits)}\n" +
                            "${budget.kind} · ${budget.categoryCode} · ${budget.periodStartYearMonth} to ${budget.periodEndYearMonth} · v${budget.version}",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        FarmOperationalSection("Actions") {
            Button(onClick = onCreateBudget, modifier = Modifier.fillMaxWidth()) { Text("Record budget") }
            Text("Tap a budget to revise it. A revision keeps every prior version.")
        }
    }
}

/**
 * FOS-FIN-010 — budget capture. Creates a new budget line or revises an
 * existing one. The scope (kind, category, currency, period) is fixed at
 * creation; a revision changes only the name and amount, as a new version.
 */
@Composable
fun BudgetCaptureScreen(
    currency: String,
    busy: Boolean,
    error: String?,
    existing: BudgetView?,
    revisions: List<BudgetRevisionView>,
    minorDigitsFor: (String) -> Int,
    onSave: (name: String, kind: String, category: String, periodStart: String, periodEnd: String, amount: String) -> Unit,
    onBack: () -> Unit,
) {
    val revising = existing != null
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var kind by remember { mutableStateOf(existing?.kind ?: "expense") }
    var category by remember { mutableStateOf(existing?.categoryCode ?: "") }
    var periodStart by remember { mutableStateOf(existing?.periodStartYearMonth ?: LocalDate.now().let { "%04d-%02d".format(it.year, it.monthValue) }) }
    var periodEnd by remember { mutableStateOf(existing?.periodEndYearMonth ?: periodStart) }
    var amount by remember { mutableStateOf("") }
    val digits = minorDigitsFor(currency)
    val canSave = !busy && name.isNotBlank() && category.isNotBlank() && amount.isNotBlank() &&
        runCatching { java.time.YearMonth.parse(periodStart) }.isSuccess &&
        runCatching { java.time.YearMonth.parse(periodEnd) }.isSuccess
    FarmOperationalPage(
        screenId = "FOS-FIN-010",
        title = if (revising) "Revise budget" else "Record budget",
        subtitle = if (revising) "A revision is saved as a new version; nothing is overwritten." else "Plan spending or income for one category and period.",
        onBack = onBack,
        backLabel = "Budget",
    ) {
        FarmOperationalSection("Details", "Enter a decimal amount such as 12.50. Storage converts it to exact minor units.") {
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            if (!revising) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Button(onClick = { kind = "expense" }, enabled = !busy && kind != "expense") { Text("Expense") }
                    Button(onClick = { kind = "income" }, enabled = !busy && kind != "income") { Text("Income") }
                }
                OutlinedTextField(category, { category = it }, label = { Text("Category code") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
                OutlinedTextField(periodStart, { periodStart = it }, label = { Text("Period start (YYYY-MM)") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
                OutlinedTextField(periodEnd, { periodEnd = it }, label = { Text("Period end (YYYY-MM)") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
                Text("Currency: $currency (farm currency)")
            } else {
                Text("Scope (fixed): ${existing.kind} · ${existing.categoryCode} · ${existing.periodStartYearMonth} to ${existing.periodEndYearMonth} · $currency")
                Text("Current: ${formatMoneyMinor(existing.amountMinor, existing.currency, digits)} (v${existing.version})")
            }
            OutlinedTextField(amount, { amount = it }, label = { Text("Amount") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            Button(
                onClick = { onSave(name, kind, category, periodStart, periodEnd, amount) },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (revising) "Save revision" else "Save budget") }
        }
        if (revising && revisions.isNotEmpty()) {
            FarmOperationalSection("Revision history") {
                revisions.forEach { revision ->
                    Text(
                        "v${revision.version} — ${formatMoneyMinor(revision.amountMinor, revision.currency, minorDigitsFor(revision.currency))}" +
                            if (revision.superseded) " (superseded)" else " (current)",
                    )
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

/** One budget line compared against actuals, in a single currency. Variance = actual − budget. */
data class BudgetVarianceView(
    val budget: BudgetView,
    val actualMinor: Long,
    val varianceMinor: Long,
)

/**
 * FOS-FIN-011 — Budget Variance.
 *
 * Budget versus actuals per category per currency per period. Actuals come
 * from the local money_records table (same kind, category, currency, with the
 * record's year-month inside the budget period). Variance is actual minus
 * budget in integer minor units; currencies are never summed.
 */
@Composable
fun VarianceScreen(
    variances: List<BudgetVarianceView>,
    minorDigitsFor: (String) -> Int,
    onBack: () -> Unit,
) {
    FarmOperationalPage(
        screenId = "FOS-FIN-011",
        title = "Budget variance",
        subtitle = "Actuals against plan, per category and period.",
        onBack = onBack,
        backLabel = "Reports",
    ) {
        FarmOperationalSection("Variance") {
            if (variances.isEmpty()) {
                Text("No budgets recorded yet, so there is nothing to compare.")
            }
            variances.forEach { row ->
                val budget = row.budget
                val digits = minorDigitsFor(budget.currency)
                val over = row.varianceMinor > 0 && budget.kind == "expense"
                Text(
                    "${budget.name} (${budget.periodStartYearMonth} to ${budget.periodEndYearMonth})",
                    fontWeight = FontWeight.Bold,
                )
                Text("Budget: ${formatMoneyMinor(budget.amountMinor, budget.currency, digits)}")
                Text("Actual: ${formatMoneyMinor(row.actualMinor, budget.currency, digits)}")
                Text(
                    "Variance: ${formatMoneyMinor(row.varianceMinor, budget.currency, digits)}",
                    fontWeight = FontWeight.Bold,
                    color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        }
        FarmOperationalSection("Reading variance") {
            Text("For expense budgets, a positive variance means overspend. For income budgets, a positive variance means beating the plan. Every figure is exact minor units; no currency is ever converted or summed.")
        }
    }
}

/**
 * FOS-FIN-012 — Finance Report: read-only summary of the farm's money position —
 * totals by kind and currency, record counts, and the reporting period covered.
 */
@Composable
fun FinanceReportSummaryScreen(
    totals: List<FinanceKindTotalView>,
    transactionCount: Int,
    earliest: String?,
    latest: String?,
    minorDigitsFor: (String) -> Int,
    onBack: () -> Unit,
) {
    FarmOperationalPage(
        screenId = "FOS-FIN-012",
        title = "Finance report",
        subtitle = "Read-only summary over every money record on this device.",
        onBack = onBack,
        backLabel = "Reports",
    ) {
        FarmOperationalSection("Coverage") {
            Text("$transactionCount money record(s) in this report.")
            if (earliest != null && latest != null) {
                Text("Period: $earliest to $latest")
            }
        }
        if (totals.isEmpty()) {
            FarmOperationalSection("No data") {
                Text("No money records yet. Totals appear here after local income and expense capture.")
            }
        } else {
            totals.sortedWith(compareBy({ it.currency }, { it.kind })).forEach { row ->
                FarmOperationalSection("${row.kind} (${row.currency})") {
                    Text(
                        "Total: ${formatMoneyMinor(row.amountMinor, row.currency, minorDigitsFor(row.currency))}",
                        fontWeight = FontWeight.Bold,
                    )
                    Text("${row.recordCount} record(s)")
                }
            }
            FarmOperationalSection("Net position") {
                Text(
                    "Net per currency is income minus expense in exact minor units. " +
                        "Currencies are never added together.",
                    style = MaterialTheme.typography.bodySmall,
                )
                totals.map { it.currency }.toSortedSet().forEach { currency ->
                    val inc = totals.filter { it.currency == currency && it.kind == "income" }.sumOf { it.amountMinor }
                    val exp = totals.filter { it.currency == currency && it.kind == "expense" }.sumOf { it.amountMinor }
                    Text(
                        "$currency net: ${formatMoneyMinor(inc - exp, currency, minorDigitsFor(currency))}",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
