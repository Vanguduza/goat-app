package com.farmos.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.feature.ops.GroupRecords
import com.farmos.feature.ops.GroupView
import com.farmos.core.design.runSuspendCatching
import androidx.compose.runtime.SideEffect

@Composable
fun GroupsModuleHost(
    farmId: String,
    ops: RoomOpsRepository,
    newContext: () -> LocalCommandContext,
    enqueueSync: () -> Unit,
    onBack: () -> Unit,
    loadGroups: suspend () -> List<GroupView> = { emptyList() },
    loadGroup: suspend (String) -> GroupRecords = { GroupRecords() },
) {
    val scope = rememberCoroutineScope()
    val state = remember(farmId, ops) {
        GroupsModuleState(farmId, ops, newContext, enqueueSync, onBack, loadGroups, loadGroup, scope)
    }
    SideEffect {
        state.newContext = newContext
        state.enqueueSync = enqueueSync
        state.onBack = onBack
        state.loadGroups = loadGroups
        state.loadGroup = loadGroup
    }
    LaunchedEffect(state) {
        runSuspendCatching { state.refresh() }.onFailure { state.error = it.message }
    }
    GroupsModuleContent(state)
}
