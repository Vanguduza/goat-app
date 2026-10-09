package com.farmos.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.farmos.core.design.FarmSelectorSearch
import com.farmos.core.design.NoFarmSelectorSearch
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomHerdRepository
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.feature.rabbit.RabbitAnimalView
import com.farmos.feature.rabbit.RabbitPedigreePorts
import com.farmos.feature.rabbit.RabbitRecords
import com.farmos.core.design.runSuspendCatching
import androidx.compose.runtime.SideEffect

@Composable
fun RabbitModuleHost(
    farmId: String,
    ops: RoomOpsRepository,
    rabbitHerd: RoomHerdRepository,
    newContext: () -> LocalCommandContext,
    enqueueSync: () -> Unit,
    onBack: () -> Unit,
    loadRecords: suspend () -> RabbitRecords = { RabbitRecords() },
    searchRabbits: FarmSelectorSearch = NoFarmSelectorSearch,
    /** A rabbit's exit record and reversal (D-022); onRecorded refreshes the rabbitry. */
    exitFor: @Composable (rabbit: RabbitAnimalView, onRecorded: () -> Unit) -> Unit = { _, _ -> },
    pedigree: RabbitPedigreePorts? = null,
    attachmentsFor: @Composable (rabbit: RabbitAnimalView) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val state = remember(farmId, ops, rabbitHerd) {
        RabbitModuleState(farmId, ops, rabbitHerd, newContext, enqueueSync, onBack, loadRecords, searchRabbits, exitFor, pedigree, attachmentsFor, scope)
    }
    SideEffect {
        state.newContext = newContext
        state.enqueueSync = enqueueSync
        state.onBack = onBack
        state.loadRecords = loadRecords
        state.searchRabbits = searchRabbits
        state.exitFor = exitFor
        state.pedigree = pedigree
        state.attachmentsFor = attachmentsFor
    }
    LaunchedEffect(state) {
        runSuspendCatching { state.refresh() }.onFailure { state.error = it.message }
    }
    RabbitModuleContent(state)
}
