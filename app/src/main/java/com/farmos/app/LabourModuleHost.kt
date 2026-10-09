package com.farmos.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.feature.ops.LabourRecords
import com.farmos.core.design.runSuspendCatching
import androidx.compose.runtime.SideEffect

@Composable
fun LabourModuleHost(
    farmId: String,
    ops: RoomOpsRepository,
    newContext: () -> LocalCommandContext,
    enqueueSync: () -> Unit,
    onBack: () -> Unit,
    loadRecords: suspend () -> LabourRecords = { LabourRecords() },
    /** The worker register (resolution R1), opened from the labour home. */
    workers: (@Composable (onBack: () -> Unit) -> Unit)? = null,
    /** Records labour against a registered worker (R1). */
    capture: LabourCapture? = null,
) {
    val scope = rememberCoroutineScope()
    val state = remember(farmId, ops) {
        LabourModuleState(farmId, ops, newContext, enqueueSync, onBack, loadRecords, workers, capture, scope)
    }
    SideEffect {
        state.newContext = newContext
        state.enqueueSync = enqueueSync
        state.onBack = onBack
        state.loadRecords = loadRecords
        state.workers = workers
        state.capture = capture
    }
    LaunchedEffect(state) {
        runSuspendCatching { state.refresh() }.onFailure { state.error = it.message }
    }
    LabourModuleContent(state)
}
