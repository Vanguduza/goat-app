package com.farmos.app

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.farmos.domain.ops.RecordLabour
import com.farmos.feature.ops.LabourRecordNavigator
import com.farmos.feature.ops.LabourWorkerPicker
import com.farmos.feature.ops.SimpleCaptureScreen
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.launch
import com.farmos.core.design.runSuspendCatching

@Composable
internal fun LabourModuleContent(state: LabourModuleState) {
    with(state) {
    when (page) {
        LabourPage.HOME -> {
            // Returning from the register refreshes the worker choice, so a worker just added can be chosen.
            val workerRegister = workers
            val register: (@Composable (onBack: () -> Unit) -> Unit)? = if (workerRegister == null) null else { back -> workerRegister { back(); scope.launch { runSuspendCatching { refresh() } } } }
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
                        val captureCommand = capture
                        if (captureCommand != null) {
                            captureCommand.record(workerOptions, workerId, worker.value, code.value, minutes.value, day.value, newContext())
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
                    androidx.compose.material3.TextButton(
                        onClick = { page = LabourPage.COST },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { androidx.compose.material3.Text("Labour cost") }
                    androidx.compose.material3.TextButton(
                        onClick = { page = LabourPage.REPORT },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { androidx.compose.material3.Text("Labour report") }
                    recordActions()
                    LabourWorkerPicker(workerOptions, workerId, !busy) { workerId = it }
                },
            ) }
        }
        LabourPage.ATTENDANCE -> LabourAttendancePage(
            entries = attendanceEntries,
            onBack = { page = LabourPage.HOME },
        )
        LabourPage.COST -> LabourCostPage(
            entries = attendanceEntries,
            onBack = { page = LabourPage.HOME },
        )
        LabourPage.REPORT -> LabourReportPage(
            entries = attendanceEntries,
            onBack = { page = LabourPage.HOME },
        )
    }

    }
}
