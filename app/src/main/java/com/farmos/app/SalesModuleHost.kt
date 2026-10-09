package com.farmos.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.farmos.core.design.FarmSelectorSearch
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.CustomerCommands
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.ops.FarmCurrency
import com.farmos.feature.ops.SalesRecords
import com.farmos.core.design.runSuspendCatching
import androidx.compose.runtime.SideEffect

@Composable
internal fun SalesModuleHost(
    farmId: String,
    ops: RoomOpsRepository,
    newContext: () -> LocalCommandContext,
    enqueueSync: () -> Unit,
    onBack: () -> Unit,
    loadRecords: suspend () -> SalesRecords = { SalesRecords() },
    loadCurrency: suspend () -> String = { FarmCurrency.DEFAULT_CODE },
    /** The customer register (FOS-SALES-002/003), opened from the sales home. */
    customers: (@Composable (onBack: () -> Unit) -> Unit)? = null,
    /** Whole-farm customer search for the sale's customer (D-004); null hides the selector. */
    customerSearch: FarmSelectorSearch? = null,
    customerCommands: CustomerCommands? = null,
    /** Animal sale (FOS-SALES-007), opened from the sales home. */
    animalSale: (@Composable (onBack: () -> Unit) -> Unit)? = null,
    /** Agreed rabbit contracts (FOS-SALES-009); null hides the contract picker, manual capture stays. */
    loadRabbitContracts: (suspend () -> List<RabbitContractView>)? = null,
) {
    val scope = rememberCoroutineScope()
    val currencyState = rememberFarmCurrency(farmId, loadCurrency)
    val state = remember(farmId, ops) {
        SalesModuleState(farmId, ops, newContext, enqueueSync, onBack, loadRecords, loadCurrency, customers, customerSearch, customerCommands, animalSale, loadRabbitContracts, scope, currencyState)
    }
    SideEffect {
        state.newContext = newContext
        state.enqueueSync = enqueueSync
        state.onBack = onBack
        state.loadRecords = loadRecords
        state.loadCurrency = loadCurrency
        state.customers = customers
        state.customerSearch = customerSearch
        state.customerCommands = customerCommands
        state.animalSale = animalSale
        state.loadRabbitContracts = loadRabbitContracts
    }
    LaunchedEffect(state) {
        runSuspendCatching { state.refresh() }.onFailure { state.error.value = it.message }
    }
    SalesModuleContent(state)
}
