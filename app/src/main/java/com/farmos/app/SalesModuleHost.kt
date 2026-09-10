package com.farmos.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.ops.RecordSale
import com.farmos.feature.ops.SimpleCaptureScreen
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.launch

/** Dedicated sales-record orchestration preserving the existing unit-economics command path. */
@Composable
fun SalesModuleHost(
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
    suspend fun refresh() { rows.value = ops.recentSales().map { "${it.itemKind} · ${it.amountMinor} ${it.currency}" } }
    LaunchedEffect(farmId) { runCatching { refresh() } }
    fun run(block: suspend () -> Unit) {
        scope.launch { busy.value = true; error.value = null; runCatching { block(); refresh() }.onSuccess { enqueueSync() }.onFailure { error.value = it.message }; busy.value = false }
    }
    val kind = remember { mutableStateOf("live_goat") }
    val qty = remember { mutableStateOf("1") }
    val amount = remember { mutableStateOf("") }
    val day = remember { mutableStateOf("") }
    SimpleCaptureScreen(
        screenId = "FOS-SALES-001", title = "Sales",
        help = "A sale posts income in integer minor units. This is farm unit economics, not a statutory ledger.",
        empty = "No sales on this device.", rows = rows.value, busy = busy.value, error = error.value,
        fields = listOf("Item kind" to kind, "Quantity" to qty, "Amount" to amount, "Date" to day),
        actionLabel = "Record sale",
        onSubmit = { run {
            val major = amount.value.replace(',', '.').toDoubleOrNull() ?: error("Enter an amount")
            val quantity = qty.value.replace(',', '.').toDoubleOrNull() ?: error("Enter a quantity")
            ops.recordSale(RecordSale(UUID.randomUUID().toString(), kind.value, (quantity * 1000.0).toLong(), (major * 100.0).toLong(), LocalDate.parse(day.value).toEpochDay()), newContext())
        } },
        onBack = onBack,
    )
}
