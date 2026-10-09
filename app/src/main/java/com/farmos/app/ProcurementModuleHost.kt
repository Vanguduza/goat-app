package com.farmos.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.ops.FarmCurrency
import com.farmos.feature.ops.ProcurementRecords
import com.farmos.core.design.runSuspendCatching
import androidx.compose.runtime.SideEffect

@Composable
fun ProcurementModuleHost(
    farmId: String,
    ops: RoomOpsRepository,
    newContext: () -> LocalCommandContext,
    enqueueSync: () -> Unit,
    onBack: () -> Unit,
    loadRecords: suspend () -> ProcurementRecords = { ProcurementRecords() },
    loadCurrency: suspend () -> String = { FarmCurrency.DEFAULT_CODE },
) {
    val scope = rememberCoroutineScope()
    val currencyState = rememberFarmCurrency(farmId, loadCurrency)
    val state = remember(farmId, ops) {
        ProcurementModuleState(farmId, ops, newContext, enqueueSync, onBack, loadRecords, loadCurrency, scope, currencyState)
    }
    SideEffect {
        state.newContext = newContext
        state.enqueueSync = enqueueSync
        state.onBack = onBack
        state.loadRecords = loadRecords
        state.loadCurrency = loadCurrency
    }
    LaunchedEffect(state) {
        runSuspendCatching { state.refresh() }.onFailure { state.error.value = it.message }
    }
    ProcurementModuleContent(state)
}
