package com.farmos.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.farmos.core.database.FarmAssetEntity
import com.farmos.core.database.MaintenanceEventEntity
import com.farmos.core.design.FarmSelectorOption
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.feature.ops.AssetRecords
import kotlinx.coroutines.launch
import com.farmos.core.design.runSuspendCatching

internal class AssetsModuleState(
    val farmId: String,
    val ops: RoomOpsRepository,
    newContext: () -> LocalCommandContext,
    enqueueSync: () -> Unit,
    onBack: () -> Unit,
    loadRecords: suspend () -> AssetRecords,
    val scope: kotlinx.coroutines.CoroutineScope,
) {
    var newContext: () -> LocalCommandContext by mutableStateOf(newContext)
    var enqueueSync: () -> Unit by mutableStateOf(enqueueSync)
    var onBack: () -> Unit by mutableStateOf(onBack)
    var loadRecords: suspend () -> AssetRecords by mutableStateOf(loadRecords)

    var page by mutableStateOf(AssetsPage.HOME)
    var rows by mutableStateOf(emptyList<String>())
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var records by mutableStateOf(AssetRecords())
    var assetOptions by mutableStateOf(emptyList<FarmSelectorOption>())
    var assetEntities by mutableStateOf(emptyList<FarmAssetEntity>())
    var maintenanceEvents by mutableStateOf(emptyList<MaintenanceEventEntity>())

    suspend fun refresh() {
        val assets = ops.assets()
        rows = assets.map { "${it.id} ${it.code} · ${it.name}" }
        assetOptions = assets.map { FarmSelectorOption(it.id, "${it.code} · ${it.name}", it.kind) }
        assetEntities = assets
        maintenanceEvents = ops.allMaintenance()
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
    val name = mutableStateOf("")
    val kind = mutableStateOf("equipment")
    val assetId = mutableStateOf("")
    val title = mutableStateOf("")
    val day = mutableStateOf("")


}
