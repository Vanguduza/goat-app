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
) {
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf(emptyList<String>()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        rows = ops.recentLabour().map { "${it.workerName} · ${it.taskCode} · ${it.minutes} min" }
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

    SimpleCaptureScreen(
        screenId = "FOS-LABOUR-001",
        title = "Labour",
        help = "Minutes are whole figures. Worker name is a farm label, not a login.",
        empty = "No labour entries on this device.",
        rows = rows,
        busy = busy,
        error = error,
        fields = listOf("Worker" to worker, "Task code" to code, "Minutes" to minutes, "Date" to day),
        actionLabel = "Record labour",
        onSubmit = {
            run {
                ops.recordLabour(
                    RecordLabour(
                        UUID.randomUUID().toString(),
                        worker.value,
                        code.value,
                        minutes.value.toIntOrNull() ?: 0,
                        LocalDate.parse(day.value).toEpochDay(),
                    ),
                    newContext(),
                )
            }
        },
        onBack = onBack,
    )
}
