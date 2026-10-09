package com.farmos.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import com.farmos.core.design.FarmSelectorSearch
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.CustomerCommands
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.feature.ops.SaleView
import com.farmos.feature.ops.SalesRecords
import kotlinx.coroutines.launch
import com.farmos.core.design.runSuspendCatching

internal class SalesModuleState(
    val farmId: String,
    val ops: RoomOpsRepository,
    newContext: () -> LocalCommandContext,
    enqueueSync: () -> Unit,
    onBack: () -> Unit,
    loadRecords: suspend () -> SalesRecords,
    loadCurrency: suspend () -> String,
    customers: (@Composable (onBack: () -> Unit) -> Unit)?,
    customerSearch: FarmSelectorSearch?,
    customerCommands: CustomerCommands?,
    animalSale: (@Composable (onBack: () -> Unit) -> Unit)?,
    loadRabbitContracts: (suspend () -> List<RabbitContractView>)?,
    val scope: kotlinx.coroutines.CoroutineScope,
    private val currencyState: androidx.compose.runtime.State<String?>,
) {
    var newContext: () -> LocalCommandContext by mutableStateOf(newContext)
    var enqueueSync: () -> Unit by mutableStateOf(enqueueSync)
    var onBack: () -> Unit by mutableStateOf(onBack)
    var loadRecords: suspend () -> SalesRecords by mutableStateOf(loadRecords)
    var loadCurrency: suspend () -> String by mutableStateOf(loadCurrency)
    var customers: (@Composable (onBack: () -> Unit) -> Unit)? by mutableStateOf(customers)
    var customerSearch: FarmSelectorSearch? by mutableStateOf(customerSearch)
    var customerCommands: CustomerCommands? by mutableStateOf(customerCommands)
    var animalSale: (@Composable (onBack: () -> Unit) -> Unit)? by mutableStateOf(animalSale)
    var loadRabbitContracts: (suspend () -> List<RabbitContractView>)? by mutableStateOf(loadRabbitContracts)

    val currency: String? get() = currencyState.value
    val busy = mutableStateOf(false)
    val saved = mutableStateOf(false)
    val error = mutableStateOf<String?>(null)
    val rows = mutableStateOf(emptyList<String>())
    val records = mutableStateOf(SalesRecords())
    suspend fun refresh() {
        rows.value = ops.recentSales().map { "${it.itemKind} · ${salesMoney(it.amountMinor, it.currency)}" }
        records.value = loadRecords()
    }
    fun run(block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        error.value = null
        saved.value = false
        scope.launch {
            try {
                runSuspendCatching {
                    completeModuleWrite(
                        write = block,
                        onCommitted = { saved.value = true },
                        enqueueSync = { enqueueSync() },
                        refresh = ::refresh,
                    )
                }.onSuccess { warning -> error.value = warning }
                    .onFailure { error.value = it.message ?: "The change could not be saved on this device" }
            } finally {
                busy.value = false
            }
        }
    }
    val page = mutableStateOf(SalesModulePage.HOME)
    val selectedSale = mutableStateOf<SaleView?>(null)
    val backHome = { page.value = SalesModulePage.HOME; selectedSale.value = null }


}
