package com.farmos.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.feature.ops.PoultryFlockRecords
import com.farmos.feature.ops.PoultryRecords
import com.farmos.core.design.runSuspendCatching
import androidx.compose.runtime.SideEffect

@Composable
fun PoultryModuleHost(
    farmId: String,
    ops: RoomOpsRepository,
    newContext: () -> LocalCommandContext,
    enqueueSync: () -> Unit,
    onBack: () -> Unit,
    loadRecords: suspend () -> PoultryRecords = { PoultryRecords() },
    loadFlock: suspend (String) -> PoultryFlockRecords = { PoultryFlockRecords() },
) {
    val scope = rememberCoroutineScope()
    val state = remember(farmId, ops) {
        PoultryModuleState(farmId, ops, newContext, enqueueSync, onBack, loadRecords, loadFlock, scope)
    }
    SideEffect {
        state.newContext = newContext
        state.enqueueSync = enqueueSync
        state.onBack = onBack
        state.loadRecords = loadRecords
        state.loadFlock = loadFlock
    }
    LaunchedEffect(state) {
        runSuspendCatching { state.refresh() }.onFailure { state.error = it.message }
    }
    PoultryModuleContent(state)
}
