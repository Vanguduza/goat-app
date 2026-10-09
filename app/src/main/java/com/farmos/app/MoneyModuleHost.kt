package com.farmos.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.ops.FarmCurrency
import com.farmos.core.design.runSuspendCatching
import androidx.compose.runtime.SideEffect

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
    val currencyState = rememberFarmCurrency(farmId, loadCurrency)
    val state = remember(farmId, ops) {
        MoneyModuleState(farmId, ops, newContext, enqueueSync, onBack, loadCurrency, scope, database, currencyState)
    }
    SideEffect {
        state.newContext = newContext
        state.enqueueSync = enqueueSync
        state.onBack = onBack
        state.loadCurrency = loadCurrency
    }
    LaunchedEffect(state) {
        runSuspendCatching { state.refresh() }.onFailure { state.error = it.message }
    }
    MoneyModuleContent(state)
}
