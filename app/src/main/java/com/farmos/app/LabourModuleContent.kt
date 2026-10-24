package com.farmos.app

import com.farmos.core.design.FarmMetricStrip
import com.farmos.core.design.FarmMetricValue
import com.farmos.core.design.FarmMetricBars
import com.farmos.core.design.FarmBarValue
import com.farmos.core.design.FarmModuleTools
import com.farmos.core.design.FarmToolAction
import com.farmos.core.design.FarmToolGroup
import com.farmos.core.design.FarmSectionTabs
import com.farmos.core.design.FarmWorkflowTimeline
import com.farmos.core.design.FarmWorkflowStep
import com.farmos.core.design.FarmWorkflowState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
    var homeSection by rememberSaveable(farmId) { mutableStateOf("Overview") }
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
            overview = {
                FarmSectionTabs(listOf("Overview", "Record", "Tools", "Records"), homeSection, { homeSection = it })
                if (homeSection == "Overview") {
                    FarmMetricStrip(listOf(FarmMetricValue("Work entries", records.entryCount?.toString() ?: "—"), FarmMetricValue("Workers with logs", records.workers.size.toString())))
                    if (records.workers.isNotEmpty()) FarmMetricBars("Recorded workload", records.workers.map { FarmBarValue(it.workerName, it.minutes.toDouble(), "${it.minutes} min", "${it.entryCount} entries") }, "minutes", caption = "Recorded work, not assigned hours or availability.")
                }
            },
            navigation = { if (homeSection == "Tools") {
                    FarmModuleTools(listOf(FarmToolGroup("Review", listOf(
                        FarmToolAction("Attendance", { page = LabourPage.ATTENDANCE }),
                        FarmToolAction("Labour cost", { page = LabourPage.COST }),
                        FarmToolAction("Labour report", { page = LabourPage.REPORT }),
                    ))), title = "Labour tools")
                    recordActions()
            } },
            captureVisible = homeSection == "Record",
            recordsVisible = homeSection == "Records",
            showPrimaryAction = true,
                extra = {
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
