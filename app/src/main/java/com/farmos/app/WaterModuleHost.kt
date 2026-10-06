package com.farmos.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.farmos.core.database.WaterPointEntity
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.ops.RecordWater
import com.farmos.domain.ops.RecordWaterPoint
import com.farmos.feature.ops.SimpleCaptureScreen
import com.farmos.feature.ops.WaterRecordNavigator
import com.farmos.feature.ops.WaterRecords
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.launch
import com.farmos.core.design.runSuspendCatching

/**
 * Dedicated water-record orchestration preserving the existing command contract.
 *
 * FOS-WATER-004 — consumption capture: the home surface records source, litres and date (root tag FOS-WATER-001).
 * FOS-WATER-002 — water point list: the recorded point register with point registration.
 * FOS-WATER-003 — water point detail: the point's fields plus consumption records logged against its code.
 */
@Composable
fun WaterModuleHost(
    farmId: String,
    ops: RoomOpsRepository,
    newContext: () -> LocalCommandContext,
    enqueueSync: () -> Unit,
    onBack: () -> Unit,
    loadRecords: suspend () -> WaterRecords = { WaterRecords() },
) {
    val scope = rememberCoroutineScope()
    val busy = remember { mutableStateOf(false) }
    val saved = remember { mutableStateOf(false) }
    val error = remember { mutableStateOf<String?>(null) }
    val rows = remember { mutableStateOf(emptyList<String>()) }
    val records = remember(farmId) { mutableStateOf(WaterRecords()) }
    suspend fun refresh() {
        rows.value = ops.recentWater().map { "${it.source} · ${it.litresMilli} ml" }
        records.value = loadRecords()
    }
    LaunchedEffect(farmId) { runSuspendCatching { refresh() } }
    fun run(block: suspend () -> Unit) {
        scope.launch {
            busy.value = true; error.value = null; saved.value = false
            runSuspendCatching { block(); refresh() }.onSuccess { saved.value = true; enqueueSync() }.onFailure { error.value = it.message }
            busy.value = false
        }
    }
    val source = remember { mutableStateOf("trough") }
    val litres = remember { mutableStateOf("") }
    val day = remember { mutableStateOf("") }
    val page = remember { mutableStateOf(WaterModulePage.HOME) }
    val selectedPoint = remember { mutableStateOf<String?>(null) }
    val eventKind = remember { mutableStateOf("inspection") }

    when (page.value) {
        WaterModulePage.HOME -> WaterRecordNavigator(records.value) { recordActions -> SimpleCaptureScreen(
        screenId = "FOS-WATER-001", title = "Water",
        help = "Enter litres as a figure. The device stores milli-litres.",
        empty = "No water records on this device.", rows = rows.value,
        busy = busy.value, error = error.value,
        fields = listOf("Source" to source, "Litres" to litres, "Date" to day),
        actionLabel = "Record water",
        onSubmit = { run {
            val milliLitres = litres.value.toScaledLongExact(3, "Litres")
            ops.recordWater(RecordWater(UUID.randomUUID().toString(), source.value, milliLitres, LocalDate.parse(day.value).toEpochDay()), newContext())
        } },
        onBack = onBack,
        saved = saved.value,
        extra = {
            recordActions()
            androidx.compose.material3.TextButton(onClick = { page.value = WaterModulePage.POINTS }) { androidx.compose.material3.Text("Water points") }
            androidx.compose.material3.TextButton(onClick = { page.value = WaterModulePage.REPORT }) { androidx.compose.material3.Text("Water report") }
        },
    ) }
        WaterModulePage.POINTS -> WaterPointListScreen(
            ops = ops,
            newContext = newContext,
            enqueueSync = enqueueSync,
            onSelect = { selectedPoint.value = it; page.value = WaterModulePage.POINT_DETAIL },
            onBack = { page.value = WaterModulePage.HOME },
        )
        WaterModulePage.POINT_DETAIL -> WaterPointDetailScreen(
            ops = ops,
            pointId = selectedPoint.value.orEmpty(),
            onRecordEvent = { kind -> eventKind.value = kind; page.value = WaterModulePage.EVENT },
            onBack = { page.value = WaterModulePage.POINTS },
        )
        WaterModulePage.EVENT -> WaterPointEventCaptureScreen(
            ops = ops,
            newContext = newContext,
            enqueueSync = enqueueSync,
            pointId = selectedPoint.value,
            kind = eventKind.value,
            onBack = { page.value = if (selectedPoint.value.isNullOrBlank()) WaterModulePage.POINTS else WaterModulePage.POINT_DETAIL },
        )
        WaterModulePage.REPORT -> WaterReportScreen(
            ops = ops,
            onBack = { page.value = WaterModulePage.HOME },
        )
    }
}

private enum class WaterModulePage { HOME, POINTS, POINT_DETAIL, EVENT, REPORT }

/** FOS-WATER-002 — water point register: recorded points with kind and active state, plus point registration. */
@Composable
fun WaterPointListScreen(
    ops: RoomOpsRepository,
    newContext: () -> LocalCommandContext,
    enqueueSync: () -> Unit,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val busy = remember { mutableStateOf(false) }
    val error = remember { mutableStateOf<String?>(null) }
    val points = remember { mutableStateOf(emptyList<WaterPointEntity>()) }
    suspend fun refresh() {
        points.value = ops.waterPoints()
    }
    LaunchedEffect(Unit) { runSuspendCatching { refresh() } }
    fun run(block: suspend () -> Unit) {
        scope.launch {
            busy.value = true; error.value = null
            runSuspendCatching { block(); refresh() }.onSuccess { enqueueSync() }.onFailure { error.value = it.message }
            busy.value = false
        }
    }
    val code = remember { mutableStateOf("") }
    val name = remember { mutableStateOf("") }
    val kind = remember { mutableStateOf("trough") }
    val active = remember { mutableStateOf(true) }
    FarmOperationalPage(
        screenId = "FOS-WATER-002",
        title = "Water points",
        subtitle = "Fixed points where water is drawn: troughs, dams, boreholes, tanks.",
        onBack = onBack,
    ) {
        FarmOperationalSection("Register a water point") {
            androidx.compose.material3.OutlinedTextField(code.value, { code.value = it }, label = { androidx.compose.material3.Text("Code") }, modifier = Modifier.fillMaxWidth())
            androidx.compose.material3.OutlinedTextField(name.value, { name.value = it }, label = { androidx.compose.material3.Text("Name") }, modifier = Modifier.fillMaxWidth())
            androidx.compose.material3.OutlinedTextField(kind.value, { kind.value = it }, label = { androidx.compose.material3.Text("Kind") }, modifier = Modifier.fillMaxWidth())
            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                androidx.compose.material3.Checkbox(active.value, { active.value = it }, enabled = !busy.value)
                androidx.compose.material3.Text("Active")
            }
            error.value?.let { androidx.compose.material3.Text(it) }
            androidx.compose.material3.Button(
                onClick = {
                    run {
                        ops.recordWaterPoint(
                            RecordWaterPoint(UUID.randomUUID().toString(), code.value, name.value.ifBlank { code.value }, kind.value, active.value),
                            newContext(),
                        )
                    }
                },
                enabled = !busy.value && code.value.isNotBlank(),
            ) { androidx.compose.material3.Text("Add water point") }
        }
        FarmOperationalSection("Water points") {
            if (points.value.isEmpty()) {
                androidx.compose.material3.Text("No water points recorded on this device.")
            } else {
                points.value.forEach { point ->
                    androidx.compose.material3.TextButton(onClick = { onSelect(point.id) }) {
                        androidx.compose.material3.Text("${point.code} · ${point.name} — ${point.kind} · ${if (point.active) "active" else "inactive"}")
                    }
                }
            }
        }
    }
}

/** FOS-WATER-003 — water point detail: the point's recorded fields plus consumption records logged against its code. */
@Composable
fun WaterPointDetailScreen(
    ops: RoomOpsRepository,
    pointId: String,
    onRecordEvent: (String) -> Unit,
    onBack: () -> Unit,
) {
    val point = remember { mutableStateOf<WaterPointEntity?>(null) }
    val records = remember { mutableStateOf(emptyList<String>()) }
    val events = remember { mutableStateOf(emptyList<com.farmos.core.database.WaterPointEventEntity>()) }
    LaunchedEffect(pointId) {
        val loaded = ops.waterPoint(pointId)
        point.value = loaded
        records.value = loaded
            ?.let { ops.waterRecordsForSource(it.code) }
            ?.map { "${LocalDate.ofEpochDay(it.occurredEpochDay)} · ${it.litresMilli} ml" }
            ?: emptyList()
        events.value = loaded?.let { ops.waterPointEvents(it.id) } ?: emptyList()
    }
    FarmOperationalPage(
        screenId = "FOS-WATER-003",
        title = "Water point",
        subtitle = "Point detail with consumption records logged against its code.",
        onBack = onBack,
    ) {
        val current = point.value
        if (current == null) {
            FarmOperationalSection("Not found", "This water point is not on this device.") {}
        } else {
            FarmOperationalSection("${current.code} · ${current.name}") {
                androidx.compose.material3.Text("Kind: ${current.kind}")
                androidx.compose.material3.Text("Status: ${if (current.active) "active" else "inactive"}")
            }
            FarmOperationalSection("Record a point event") {
                androidx.compose.foundation.layout.Row {
                    androidx.compose.material3.TextButton(onClick = { onRecordEvent("inspection") }) { androidx.compose.material3.Text("Inspection") }
                    androidx.compose.material3.TextButton(onClick = { onRecordEvent("quality") }) { androidx.compose.material3.Text("Quality result") }
                }
                androidx.compose.foundation.layout.Row {
                    androidx.compose.material3.TextButton(onClick = { onRecordEvent("issue") }) { androidx.compose.material3.Text("Report issue") }
                    androidx.compose.material3.TextButton(onClick = { onRecordEvent("maintenance") }) { androidx.compose.material3.Text("Maintenance") }
                }
            }
            FarmOperationalSection("Water records for this source") {
                if (records.value.isEmpty()) {
                    androidx.compose.material3.Text("No consumption records logged against ${current.code} yet.")
                } else {
                    records.value.forEach { androidx.compose.material3.Text(it) }
                }
            }
            FarmOperationalSection("Point events") {
                if (events.value.isEmpty()) {
                    androidx.compose.material3.Text("No inspections, quality results, issues or maintenance recorded for this point.")
                } else {
                    events.value.forEach { event ->
                        androidx.compose.material3.Text(
                            "${LocalDate.ofEpochDay(event.occurredEpochDay)} · ${event.kind}" +
                                (event.resultText?.let { " — $it" } ?: "") +
                                (event.note?.let { " ($it)" } ?: ""),
                        )
                    }
                }
            }
        }
    }
}

