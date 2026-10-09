package com.farmos.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.farmos.core.database.LabourEntryEntity
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.feature.ops.LabourRecords
import com.farmos.feature.ops.LabourWorkerOption
import kotlinx.coroutines.launch
import com.farmos.core.design.runSuspendCatching

internal class LabourModuleState(
    val farmId: String,
    val ops: RoomOpsRepository,
    newContext: () -> LocalCommandContext,
    enqueueSync: () -> Unit,
    onBack: () -> Unit,
    loadRecords: suspend () -> LabourRecords,
    workers: (@Composable (onBack: () -> Unit) -> Unit)?,
    capture: LabourCapture?,
    val scope: kotlinx.coroutines.CoroutineScope,
) {
    var newContext: () -> LocalCommandContext by mutableStateOf(newContext)
    var enqueueSync: () -> Unit by mutableStateOf(enqueueSync)
    var onBack: () -> Unit by mutableStateOf(onBack)
    var loadRecords: suspend () -> LabourRecords by mutableStateOf(loadRecords)
    var workers: (@Composable (onBack: () -> Unit) -> Unit)? by mutableStateOf(workers)
    var capture: LabourCapture? by mutableStateOf(capture)

    var page by mutableStateOf(LabourPage.HOME)
    var rows by mutableStateOf(emptyList<String>())
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var records by mutableStateOf(LabourRecords())
    var workerOptions by mutableStateOf(emptyList<LabourWorkerOption>())
    var workerId by mutableStateOf<String?>(null)
    var attendanceEntries by mutableStateOf(emptyList<LabourEntryEntity>())

    suspend fun refresh() {
        rows = ops.recentLabour().map { "${it.workerName} · ${it.taskCode} · ${it.minutes} min" }
        records = loadRecords()
        workerOptions = capture?.activeWorkers().orEmpty()
        attendanceEntries = ops.allLabour()
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

    val worker = mutableStateOf("")
    val code = mutableStateOf("CHECK")
    val minutes = mutableStateOf("")
    val day = mutableStateOf("")


}
