package com.farmos.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.farmos.core.design.FarmSelectorOption
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.feature.ops.GroupRecords
import com.farmos.feature.ops.GroupView
import kotlinx.coroutines.launch
import com.farmos.core.design.runSuspendCatching

internal class GroupsModuleState(
    val farmId: String,
    val ops: RoomOpsRepository,
    newContext: () -> LocalCommandContext,
    enqueueSync: () -> Unit,
    onBack: () -> Unit,
    loadGroups: suspend () -> List<GroupView>,
    loadGroup: suspend (String) -> GroupRecords,
    val scope: kotlinx.coroutines.CoroutineScope,
) {
    var newContext: () -> LocalCommandContext by mutableStateOf(newContext)
    var enqueueSync: () -> Unit by mutableStateOf(enqueueSync)
    var onBack: () -> Unit by mutableStateOf(onBack)
    var loadGroups: suspend () -> List<GroupView> by mutableStateOf(loadGroups)
    var loadGroup: suspend (String) -> GroupRecords by mutableStateOf(loadGroup)

    var page by mutableStateOf(GroupsPage.HOME)
    var rows by mutableStateOf(emptyList<String>())
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var groupViews by mutableStateOf(emptyList<GroupView>())
    var groupOptions by mutableStateOf(emptyList<FarmSelectorOption>())

    suspend fun refresh() {
        val groups = ops.groups()
        rows = groups.map { "${it.id} ${it.name} · ${it.speciesCode} · ${it.headCount}" }
        groupOptions = groups.map { FarmSelectorOption(it.id, it.name, "${it.speciesCode} · ${it.headCount} head recorded") }
        groupViews = loadGroups()
    }


    fun run(block: suspend () -> Unit) {
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

    val name = mutableStateOf("")
    val species = mutableStateOf("goat")
    val heads = mutableStateOf("")
    val day = mutableStateOf("")
    val groupId = mutableStateOf("")


}
