package com.farmos.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.ops.RecordLabour
import com.farmos.feature.ops.LabourRecordNavigator
import com.farmos.feature.ops.LabourRecords
import com.farmos.feature.ops.LabourWorkerOption
import com.farmos.feature.ops.LabourWorkerPicker
import com.farmos.feature.ops.SimpleCaptureScreen
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.launch

/** Dedicated labour/work-log orchestration boundary. */
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
    var rows by remember { mutableStateOf(emptyList<String>()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var records by remember(farmId) { mutableStateOf(LabourRecords()) }
    var workerOptions by remember(farmId) { mutableStateOf(emptyList<LabourWorkerOption>()) }
    var workerId by remember(farmId) { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        rows = ops.recentLabour().map { "${it.workerName} · ${it.taskCode} · ${it.minutes} min" }
        records = loadRecords()
        workerOptions = capture?.activeWorkers().orEmpty()
    }

    LaunchedEffect(farmId) { runCatching { refresh() } }

    fun run(block: suspend () -> Unit) {
        scope.launch {
            busy = true
            error = null
            runCatching {
                block()
                refresh()
            }.onSuccess { enqueueSync() }
                .onFailure { error = it.message }
            busy = false
        }
    }

    val worker = remember { mutableStateOf("") }
    val code = remember { mutableStateOf("CHECK") }
    val minutes = remember { mutableStateOf("") }
    val day = remember { mutableStateOf("") }

    // Returning from the register refreshes the worker choice, so a worker just added can be chosen.
    val register: (@Composable (onBack: () -> Unit) -> Unit)? = if (workers == null) null else { back -> workers { back(); scope.launch { runCatching { refresh() } } } }
    LabourRecordNavigator(records, register) { recordActions -> SimpleCaptureScreen(
        screenId = "FOS-LABOUR-001",
        title = "Labour",
        help = "Minutes are whole figures.",
        empty = "No labour entries on this device.",
        rows = rows,
        busy = busy,
        error = error,
        fields = (if (workerOptions.isEmpty()) listOf("Worker" to worker) else emptyList()) + listOf("Task code" to code, "Minutes" to minutes, "Date" to day),
        actionLabel = "Record labour",
        onSubmit = {
            run {
                if (capture != null) {
                    capture.record(workerOptions, workerId, worker.value, code.value, minutes.value, day.value, newContext())
                } else {
                    ops.recordLabour(RecordLabour(UUID.randomUUID().toString(), worker.value, code.value, minutes.value.toIntOrNull() ?: 0, LocalDate.parse(day.value).toEpochDay()), newContext())
                }
            }
        },
        onBack = onBack,
        extra = {
            recordActions()
            LabourWorkerPicker(workerOptions, workerId, !busy) { workerId = it }
        },
    ) }
}
