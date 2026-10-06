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
import kotlinx.coroutines.launch

/**
 * Dedicated water-record orchestration preserving the existing command contract.
 *
 * FOS-WATER-004 — consumption capture: the home surface records source, litres and date (root tag FOS-WATER-001).
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
    LaunchedEffect(farmId) { runCatching { refresh() } }
    fun run(block: suspend () -> Unit) {
        scope.launch {
            busy.value = true; error.value = null; saved.value = false
            runCatching { block(); refresh() }.onSuccess { saved.value = true; enqueueSync() }.onFailure { error.value = it.message }
            busy.value = false
        }
    }
    val source = remember { mutableStateOf("trough") }
    val litres = remember { mutableStateOf("") }
    val day = remember { mutableStateOf("") }
    WaterRecordNavigator(records.value) { recordActions -> SimpleCaptureScreen(
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
        extra = { recordActions() },
    ) }
}
