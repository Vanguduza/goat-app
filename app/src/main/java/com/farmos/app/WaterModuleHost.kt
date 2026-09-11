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
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.launch

/** Dedicated water-record orchestration preserving the existing command contract. */
@Composable
fun WaterModuleHost(
    farmId: String,
    ops: RoomOpsRepository,
    newContext: () -> LocalCommandContext,
    enqueueSync: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val busy = remember { mutableStateOf(false) }
    val error = remember { mutableStateOf<String?>(null) }
    val rows = remember { mutableStateOf(emptyList<String>()) }
    suspend fun refresh() { rows.value = ops.recentWater().map { "${it.source} · ${it.litresMilli} ml" } }
    LaunchedEffect(farmId) { runCatching { refresh() } }
    fun run(block: suspend () -> Unit) {
        scope.launch {
            busy.value = true; error.value = null
            runCatching { block(); refresh() }.onSuccess { enqueueSync() }.onFailure { error.value = it.message }
            busy.value = false
        }
    }
    val source = remember { mutableStateOf("trough") }
    val litres = remember { mutableStateOf("") }
    val day = remember { mutableStateOf("") }
    SimpleCaptureScreen(
        screenId = "FOS-WATER-001", title = "Water",
        help = "Enter litres as a figure. The device stores milli-litres.",
        empty = "No water records on this device.", rows = rows.value,
        busy = busy.value, error = error.value,
        fields = listOf("Source" to source, "Litres" to litres, "Date" to day),
        actionLabel = "Record water",
        onSubmit = { run {
            val amount = litres.value.replace(',', '.').toDoubleOrNull() ?: error("Enter litres")
            ops.recordWater(RecordWater(UUID.randomUUID().toString(), source.value, (amount * 1000.0).toLong(), LocalDate.parse(day.value).toEpochDay()), newContext())
        } },
        onBack = onBack,
    )
}
