package com.farmos.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.feature.ops.AssetRecords
import com.farmos.core.design.runSuspendCatching
import androidx.compose.runtime.SideEffect

@Composable
fun AssetsModuleHost(
    farmId: String,
    ops: RoomOpsRepository,
    newContext: () -> LocalCommandContext,
    enqueueSync: () -> Unit,
    onBack: () -> Unit,
    loadRecords: suspend () -> AssetRecords = { AssetRecords() },
) {
    val scope = rememberCoroutineScope()
    val state = remember(farmId, ops) {
        AssetsModuleState(farmId, ops, newContext, enqueueSync, onBack, loadRecords, scope)
    }
    SideEffect {
        state.newContext = newContext
        state.enqueueSync = enqueueSync
        state.onBack = onBack
        state.loadRecords = loadRecords
    }
    LaunchedEffect(state) {
        runSuspendCatching { state.refresh() }.onFailure { state.error = it.message }
    }
    AssetsModuleContent(state)
}
