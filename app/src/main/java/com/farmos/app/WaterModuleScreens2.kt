package com.farmos.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.farmos.core.database.WaterPointEntity
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.ops.RecordWaterPointEvent
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.launch
import com.farmos.core.design.runSuspendCatching

private val WATER_EVENT_KINDS = mapOf(
    "inspection" to "FOS-WATER-005",
    "quality" to "FOS-WATER-006",
    "issue" to "FOS-WATER-007",
    "maintenance" to "FOS-WATER-008",
)

private val WATER_EVENT_TITLES = mapOf(
    "inspection" to "Water inspection",
    "quality" to "Water quality result",
    "issue" to "Water issue",
    "maintenance" to "Water maintenance",
)

private val WATER_EVENT_SUBTITLES = mapOf(
    "inspection" to "Record an inspection of a water point.",
    "quality" to "Record a water quality test result.",
    "issue" to "Report a problem with a water point.",
    "maintenance" to "Record maintenance carried out on a water point.",
)

/** FOS-WATER-005 — water inspection: record an inspection against a water point. */
@Composable
fun WaterInspectionScreen(
    ops: RoomOpsRepository,
    newContext: () -> LocalCommandContext,
    enqueueSync: () -> Unit,
    pointId: String?,
    onBack: () -> Unit,
) = WaterPointEventCaptureScreen(ops, newContext, enqueueSync, pointId, "inspection", onBack)

/** FOS-WATER-006 — water quality result: record a quality test result against a water point. */
@Composable
fun WaterQualityScreen(
    ops: RoomOpsRepository,
    newContext: () -> LocalCommandContext,
    enqueueSync: () -> Unit,
    pointId: String?,
    onBack: () -> Unit,
) = WaterPointEventCaptureScreen(ops, newContext, enqueueSync, pointId, "quality", onBack)

/** FOS-WATER-007 — water issue: report a problem with a water point. */
@Composable
fun WaterIssueScreen(
    ops: RoomOpsRepository,
    newContext: () -> LocalCommandContext,
    enqueueSync: () -> Unit,
    pointId: String?,
    onBack: () -> Unit,
) = WaterPointEventCaptureScreen(ops, newContext, enqueueSync, pointId, "issue", onBack)

/** FOS-WATER-008 — water maintenance: record maintenance carried out on a water point. */
@Composable
fun WaterMaintenanceScreen(
    ops: RoomOpsRepository,
    newContext: () -> LocalCommandContext,
    enqueueSync: () -> Unit,
    pointId: String?,
    onBack: () -> Unit,
) = WaterPointEventCaptureScreen(ops, newContext, enqueueSync, pointId, "maintenance", onBack)

/**
 * Shared capture surface for the four water-point event kinds. The screenId is the owning
 * FOS-WATER-005/006/007/008 identity for the active kind; the four thin wrappers above carry
 * the static KDoc tags so each screen keeps its own identity.
 */
@Composable
fun WaterPointEventCaptureScreen(
    ops: RoomOpsRepository,
    newContext: () -> LocalCommandContext,
    enqueueSync: () -> Unit,
    pointId: String?,
    kind: String,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var points by remember { mutableStateOf(emptyList<WaterPointEntity>()) }
    var selectedPoint by remember(pointId) { mutableStateOf(pointId) }
    val day = remember { mutableStateOf(LocalDate.now().toString()) }
    val result = remember { mutableStateOf("") }
    val value = remember { mutableStateOf("") }
    val unit = remember { mutableStateOf("") }
    val note = remember { mutableStateOf("") }
    LaunchedEffect(Unit) { points = ops.waterPoints() }
    FarmOperationalPage(
        screenId = WATER_EVENT_KINDS[kind] ?: "FOS-WATER-005",
        title = WATER_EVENT_TITLES[kind] ?: "Water event",
        subtitle = WATER_EVENT_SUBTITLES[kind] ?: "",
        onBack = onBack,
    ) {
        FarmOperationalSection("Water point") {
            if (points.isEmpty()) {
                androidx.compose.material3.Text("No water points recorded on this device. Register one first.")
            } else {
                points.forEach { point ->
                    androidx.compose.material3.TextButton(onClick = { selectedPoint = point.id }) {
                        androidx.compose.material3.Text("${if (point.id == selectedPoint) "● " else ""}${point.code} · ${point.name}")
                    }
                }
            }
        }
        FarmOperationalSection("Event") {
            androidx.compose.material3.OutlinedTextField(day.value, { day.value = it }, label = { androidx.compose.material3.Text("Date") }, placeholder = { androidx.compose.material3.Text("YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth())
            androidx.compose.material3.OutlinedTextField(result.value, { result.value = it }, label = { androidx.compose.material3.Text("Result") }, placeholder = { androidx.compose.material3.Text("e.g. pass, clean, leak found") }, modifier = Modifier.fillMaxWidth())
            androidx.compose.material3.OutlinedTextField(value.value, { value.value = it }, label = { androidx.compose.material3.Text("Reading (optional, numeric)") }, modifier = Modifier.fillMaxWidth())
            androidx.compose.material3.OutlinedTextField(unit.value, { unit.value = it }, label = { androidx.compose.material3.Text("Unit (optional)") }, placeholder = { androidx.compose.material3.Text("e.g. L, pH") }, modifier = Modifier.fillMaxWidth())
            androidx.compose.material3.OutlinedTextField(note.value, { note.value = it }, label = { androidx.compose.material3.Text("Note") }, modifier = Modifier.fillMaxWidth())
            error?.let { androidx.compose.material3.Text(it) }
            androidx.compose.material3.Button(
                onClick = {
                    scope.launch {
                        busy = true; error = null
                        runSuspendCatching {
                            ops.recordWaterPointEvent(
                                RecordWaterPointEvent(
                                    UUID.randomUUID().toString(),
                                    requireNotNull(selectedPoint) { "Choose a water point" },
                                    kind,
                                    LocalDate.parse(day.value).toEpochDay(),
                                    result.value.ifBlank { null },
                                    value.value.ifBlank { null }?.toLongOrNull(),
                                    unit.value.ifBlank { null },
                                    note.value.ifBlank { null },
                                ),
                                newContext(),
                            )
                        }.onSuccess { enqueueSync() }.onFailure { error = it.message }
                        busy = false
                    }
                },
                enabled = !busy && selectedPoint != null,
            ) { androidx.compose.material3.Text("Save") }
        }
    }
}

/** FOS-WATER-010 — water report: read-only aggregates over points, consumption and point events. */
@Composable
fun WaterReportScreen(
    ops: RoomOpsRepository,
    onBack: () -> Unit,
) {
    var lines by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(Unit) {
        val points = ops.waterPoints()
        val records = ops.recentWater()
        val out = mutableListOf(
            "Water points: ${points.size} (${points.count { it.active }} active)",
            "Consumption records: ${records.size}",
            "Total recorded: ${"%.1f".format(records.sumOf { it.litresMilli } / 1_000_000.0)} kL",
            "Inspections: ${ops.waterPointEventCount("inspection")}",
            "Quality results: ${ops.waterPointEventCount("quality")}",
            "Issues reported: ${ops.waterPointEventCount("issue")}",
            "Maintenance events: ${ops.waterPointEventCount("maintenance")}",
        )
        lines = out
    }
    FarmOperationalPage(
        screenId = "FOS-WATER-010",
        title = "Water report",
        subtitle = "Aggregates over this device's water records.",
        onBack = onBack,
    ) {
        FarmOperationalSection("Summary") {
            lines.forEach { androidx.compose.material3.Text(it) }
        }
    }
}
