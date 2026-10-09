package com.farmos.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.farmos.domain.ops.FarmCurrency
import com.farmos.domain.ops.RecordBudget
import com.farmos.domain.ops.RecordMoney
import com.farmos.domain.ops.ReviseBudget
import com.farmos.feature.ops.ActivityCostScreen
import com.farmos.feature.ops.BudgetCaptureScreen
import com.farmos.feature.ops.BudgetScreen
import com.farmos.feature.ops.BudgetVarianceView
import com.farmos.feature.ops.CashFlowScreen
import com.farmos.feature.ops.EnterpriseProfitabilityScreen
import com.farmos.feature.ops.FinanceCategoryTotalView
import com.farmos.feature.ops.FinanceMonthFlowView
import com.farmos.feature.ops.FinanceReportDestination
import com.farmos.feature.ops.FinanceReportSummaryScreen
import com.farmos.feature.ops.FinanceReportsHub
import com.farmos.feature.ops.FinanceTransactionDetailScreen
import com.farmos.feature.ops.MoneyCaptureScreen
import com.farmos.feature.ops.SpeciesCostScreen
import com.farmos.feature.ops.VarianceScreen
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import com.farmos.core.design.runSuspendCatching

@Composable
internal fun MoneyModuleContent(state: MoneyModuleState) {
    with(state) {
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
        MoneyHubPage.BUDGET -> BudgetScreen(
            budgets = budgets,
            minorDigitsFor = minorDigitsFor,
            onCreateBudget = { hubPage = MoneyHubPage.BUDGET_CREATE },
            onReviseBudget = { budget ->
                selectedBudgetKey = budget.budgetKey
                hubPage = MoneyHubPage.BUDGET_REVISE
            },
            onBack = backToReports,
        )
        MoneyHubPage.BUDGET_CREATE -> {
            val code = currency
            BudgetCaptureScreen(
                currency = code ?: FarmCurrency.DEFAULT_CODE,
                busy = busy,
                error = error,
                existing = null,
                revisions = emptyList(),
                minorDigitsFor = minorDigitsFor,
                onSave = { name, kind, category, periodStart, periodEnd, amount ->
                    runWrite {
                        val budgetCurrency = checkNotNull(currency) { "The farm currency is still loading" }
                        val amountMinor = amount.toScaledLongExact(FarmCurrency.minorDigits(budgetCurrency), "Amount")
                        ops.recordBudget(
                            RecordBudget(
                                budgetId = UUID.randomUUID().toString(),
                                budgetKey = UUID.randomUUID().toString(),
                                name = name,
                                kind = kind,
                                categoryCode = category,
                                periodStartYearMonth = periodStart,
                                periodEndYearMonth = periodEnd,
                                amountMinor = amountMinor,
                                currency = budgetCurrency,
                            ),
                            newContext(),
                        )
                        hubPage = MoneyHubPage.BUDGET
                    }
                },
                onBack = { hubPage = MoneyHubPage.BUDGET },
            )
        }
        MoneyHubPage.BUDGET_REVISE -> {
            val selected = budgets.firstOrNull { it.budgetKey == selectedBudgetKey }
            if (selected == null) {
                LaunchedEffect(Unit) { hubPage = MoneyHubPage.BUDGET }
            } else {
                LaunchedEffect(selected.budgetKey) {
                    runSuspendCatching { refreshRevisions(selected.budgetKey) }
                        .onFailure { failure -> error = failure.message }
                }
                BudgetCaptureScreen(
                    currency = selected.currency,
                    busy = busy,
                    error = error,
                    existing = selected,
                    revisions = budgetRevisions,
                    minorDigitsFor = minorDigitsFor,
                    onSave = { name, _, _, _, _, amount ->
                        runWrite {
                            val amountMinor = amount.toScaledLongExact(FarmCurrency.minorDigits(selected.currency), "Amount")
                            ops.reviseBudget(
                                ReviseBudget(
                                    budgetId = UUID.randomUUID().toString(),
                                    budgetKey = selected.budgetKey,
                                    name = name,
                                    amountMinor = amountMinor,
                                ),
                                newContext(),
                            )
                            hubPage = MoneyHubPage.BUDGET
                        }
                    },
                    onBack = { hubPage = MoneyHubPage.BUDGET },
                )
            }
        }
        MoneyHubPage.VARIANCE -> {
            val variances = budgets.map { budget ->
                val start = YearMonth.parse(budget.periodStartYearMonth)
                val end = YearMonth.parse(budget.periodEndYearMonth)
                val actual = allTransactions
                    .filter { it.kind == budget.kind && it.category == budget.categoryCode && it.currency == budget.currency }
                    .filter {
                        val ym = YearMonth.from(LocalDate.ofEpochDay(it.epochDay))
                        !ym.isBefore(start) && !ym.isAfter(end)
                    }
                    .sumOf { it.amountMinor }
                BudgetVarianceView(budget = budget, actualMinor = actual, varianceMinor = actual - budget.amountMinor)
            }
            VarianceScreen(
                variances = variances,
                minorDigitsFor = minorDigitsFor,
                onBack = backToReports,
            )
        }
        MoneyHubPage.FINANCE_REPORT -> FinanceReportSummaryScreen(
            totals = kindTotals,
            transactionCount = allTransactions.size,
            earliest = allTransactions.minOfOrNull { it.epochDay }?.let { LocalDate.ofEpochDay(it).toString() },
            latest = allTransactions.maxOfOrNull { it.epochDay }?.let { LocalDate.ofEpochDay(it).toString() },
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
}
