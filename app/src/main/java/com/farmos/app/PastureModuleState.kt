package com.farmos.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.farmos.core.design.FarmSelectorOption
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.feature.ops.PastureRecords
import kotlinx.coroutines.launch
import com.farmos.core.design.runSuspendCatching

internal class PastureModuleState(
    val farmId: String,
    val ops: RoomOpsRepository,
    newContext: () -> LocalCommandContext,
    enqueueSync: () -> Unit,
    onBack: () -> Unit,
    loadRecords: suspend () -> PastureRecords,
    val scope: kotlinx.coroutines.CoroutineScope,
) {
    var newContext: () -> LocalCommandContext by mutableStateOf(newContext)
    var enqueueSync: () -> Unit by mutableStateOf(enqueueSync)
    var onBack: () -> Unit by mutableStateOf(onBack)
    var loadRecords: suspend () -> PastureRecords by mutableStateOf(loadRecords)

    var paddockRows by mutableStateOf(emptyList<String>())
    var grazingRows by mutableStateOf(emptyList<String>())
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var records by mutableStateOf(PastureRecords())
    var paddockOptions by mutableStateOf(emptyList<FarmSelectorOption>())
    var groupOptions by mutableStateOf(emptyList<FarmSelectorOption>())

    suspend fun refresh() {
        val paddocks = ops.paddocks()
        paddockRows = paddocks.map { "${it.id} ${it.code} · ${it.displayName} · ${it.waterSource}" }
        paddockOptions = paddocks.map { FarmSelectorOption(it.id, "${it.code} · ${it.displayName}", "Water ${it.waterSource}") }
        groupOptions = ops.groups().map { FarmSelectorOption(it.id, it.name, "${it.speciesCode} · ${it.headCount} head recorded") }
        grazingRows = ops.openGrazing().map { "${it.id} paddock ${it.paddockId} · group ${it.groupId}" }
        records = loadRecords()
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

    val code = mutableStateOf("")
    val display = mutableStateOf("")
    val water = mutableStateOf("trough")
    val paddockId = mutableStateOf("")
    val groupId = mutableStateOf("")
    val heads = mutableStateOf("")
    val entered = mutableStateOf("")
    val sessionId = mutableStateOf("")
    val exited = mutableStateOf("")
    var page by mutableStateOf(PastureModulePage.HOME)


}
