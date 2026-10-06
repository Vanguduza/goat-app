package com.farmos.app

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.farmos.core.database.LabourEntryEntity
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalSection
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

/** Internal pages of the labour module. */
private enum class LabourPage {
    HOME,
    ATTENDANCE,
}

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
    var page by remember(farmId) { mutableStateOf(LabourPage.HOME) }
    var rows by remember { mutableStateOf(emptyList<String>()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var records by remember(farmId) { mutableStateOf(LabourRecords()) }
    var workerOptions by remember(farmId) { mutableStateOf(emptyList<LabourWorkerOption>()) }
    var workerId by remember(farmId) { mutableStateOf<String?>(null) }
    var attendanceEntries by remember(farmId) { mutableStateOf(emptyList<LabourEntryEntity>()) }

    suspend fun refresh() {
        rows = ops.recentLabour().map { "${it.workerName} · ${it.taskCode} · ${it.minutes} min" }
        records = loadRecords()
        workerOptions = capture?.activeWorkers().orEmpty()
        attendanceEntries = ops.recentLabour()
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

    when (page) {
        LabourPage.HOME -> {
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
                    androidx.compose.material3.TextButton(
                        onClick = { page = LabourPage.ATTENDANCE },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { androidx.compose.material3.Text("Attendance") }
                    recordActions()
                    LabourWorkerPicker(workerOptions, workerId, !busy) { workerId = it }
                },
            ) }
        }
        LabourPage.ATTENDANCE -> LabourAttendancePage(
            entries = attendanceEntries,
            onBack = { page = LabourPage.HOME },
        )
    }
}

/**
 * FOS-LABOUR-006 — Attendance: days worked per worker, derived from the labour entries on this
 * device. A worker present on a day is a worker with entries that day; minutes are whole figures.
 */
@Composable
private fun LabourAttendancePage(
    entries: List<LabourEntryEntity>,
    onBack: () -> Unit,
) {
    val byDay = entries.groupBy { it.occurredEpochDay }.toSortedMap(compareByDescending { it })
    FarmOperationalPage(
        screenId = "FOS-LABOUR-006",
        title = "Attendance",
        subtitle = "Days worked per worker, from the labour entries on this device.",
        onBack = onBack,
    ) {
        if (entries.isEmpty()) {
            androidx.compose.material3.Text("No labour entries on this device.")
            return@FarmOperationalPage
        }
        androidx.compose.material3.Text("From the ${entries.size} most recent entries on this device.")
        byDay.forEach { (day, dayEntries) ->
            FarmOperationalSection(LocalDate.ofEpochDay(day).toString()) {
                dayEntries.groupBy { it.workerName }.toSortedMap().forEach { (name, workerEntries) ->
                    val minutes = workerEntries.sumOf { it.minutes }
                    androidx.compose.material3.Text("$name · $minutes min · ${workerEntries.size} entries")
                }
            }
        }
    }
}
