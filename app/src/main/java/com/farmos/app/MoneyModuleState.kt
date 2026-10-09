package com.farmos.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.ops.FarmCurrency
import com.farmos.feature.ops.BudgetRevisionView
import com.farmos.feature.ops.BudgetView
import com.farmos.feature.ops.FinanceKindTotalView
import com.farmos.feature.ops.FinanceTransactionView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.farmos.core.design.runSuspendCatching

internal class MoneyModuleState(
    val farmId: String,
    val ops: RoomOpsRepository,
    newContext: () -> LocalCommandContext,
    enqueueSync: () -> Unit,
    onBack: () -> Unit,
    loadCurrency: suspend () -> String,
    val scope: kotlinx.coroutines.CoroutineScope,
    private val database: com.farmos.core.database.FarmOsDatabase,
    private val currencyState: androidx.compose.runtime.State<String?>,
) {
    var newContext: () -> LocalCommandContext by mutableStateOf(newContext)
    var enqueueSync: () -> Unit by mutableStateOf(enqueueSync)
    var onBack: () -> Unit by mutableStateOf(onBack)
    var loadCurrency: suspend () -> String by mutableStateOf(loadCurrency)

    val currency: String? get() = currencyState.value
    var rows by mutableStateOf(emptyList<String>())
    var transactions by mutableStateOf(emptyList<FinanceTransactionView>())
    var allTransactions by mutableStateOf(emptyList<FinanceTransactionView>())
    var kindTotals by mutableStateOf(emptyList<FinanceKindTotalView>())
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var hubPage by mutableStateOf(MoneyHubPage.HOME)
    var selectedTransactionId by mutableStateOf<String?>(null)
    var budgets by mutableStateOf(emptyList<BudgetView>())
    var budgetRevisions by mutableStateOf(emptyList<BudgetRevisionView>())
    var selectedBudgetKey by mutableStateOf<String?>(null)

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
            "${row.kind} ${row.categoryCode} ${java.math.BigDecimal.valueOf(row.amountMinor, FarmCurrency.minorDigits(row.currency)).toPlainString()} ${row.currency}"
        }
        kindTotals = totals.map { it.toKindView() }
        budgets = withContext(Dispatchers.IO) { ops.budgets().map { it.toView() } }
    }

    suspend fun refreshRevisions(budgetKey: String) {
        budgetRevisions = withContext(Dispatchers.IO) { ops.budgetRevisions(budgetKey).map { it.toRevisionView() } }
    }

    fun runWrite(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                runSuspendCatching {
                    completeModuleWrite(
                        write = block,
                        onCommitted = {},
                        enqueueSync = { enqueueSync() },
                        refresh = ::refresh,
                    )
                }.onSuccess { warning -> error = warning }
                    .onFailure { error = it.message ?: "The change could not be saved on this device" }
            } finally {
                busy = false
            }
        }
    }


    val backToHome = { hubPage = MoneyHubPage.HOME }
    val backToReports = { hubPage = MoneyHubPage.REPORTS }


}
