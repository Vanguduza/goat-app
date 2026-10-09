package com.farmos.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.ops.RecordWater
import com.farmos.feature.ops.SimpleCaptureScreen
import com.farmos.feature.ops.WaterRecordNavigator
import com.farmos.feature.ops.WaterRecords
import java.time.LocalDate
import java.util.UUID
import com.farmos.core.design.runSuspendCatching

/** Water command orchestration and owning routes. FOS-WATER-001/004. */
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
    fun run(block: suspend () -> Unit) = launchCommittedModuleWrite(
        scope = scope,
        write = block,
        isBusy = { busy.value },
        setBusy = { busy.value = it },
        setError = { error.value = it },
        enqueueSync = enqueueSync,
        refresh = ::refresh,
        onStarted = { saved.value = false },
        onCommitted = { saved.value = true },
    )
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
