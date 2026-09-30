package com.farmos.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.ops.FarmCurrency
import com.farmos.domain.ops.RecordSale
import com.farmos.feature.ops.SalesRecordNavigator
import com.farmos.feature.ops.SalesRecords
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
    loadRecords: suspend () -> SalesRecords = { SalesRecords() },
    loadCurrency: suspend () -> String = { FarmCurrency.DEFAULT_CODE },
) {
    val scope = rememberCoroutineScope()
    val currency by rememberFarmCurrency(farmId, loadCurrency)
    val busy = remember { mutableStateOf(false) }
    val saved = remember { mutableStateOf(false) }
    val error = remember { mutableStateOf<String?>(null) }
    val rows = remember { mutableStateOf(emptyList<String>()) }
    val records = remember(farmId) { mutableStateOf(SalesRecords()) }
    suspend fun refresh() {
        rows.value = ops.recentSales().map { "${it.itemKind} · ${it.amountMinor} ${it.currency}" }
        records.value = loadRecords()
    }
    LaunchedEffect(farmId) { runCatching { refresh() } }
    fun run(block: suspend () -> Unit) {
        scope.launch { busy.value = true; error.value = null; saved.value = false; runCatching { block(); refresh() }.onSuccess { saved.value = true; enqueueSync() }.onFailure { error.value = it.message }; busy.value = false }
    }
    val kind = remember { mutableStateOf("live_goat") }
    val qty = remember { mutableStateOf("1") }
    val amount = remember { mutableStateOf("") }
    val day = remember { mutableStateOf("") }
    SalesRecordNavigator(records.value) { recordActions -> SimpleCaptureScreen(
        screenId = "FOS-SALES-001", title = "Sales",
        help = "A sale posts income in integer minor units of ${currency ?: "the farm currency"}. This is farm unit economics, not a statutory ledger.",
        empty = "No sales on this device.", rows = rows.value, busy = busy.value, error = error.value,
        fields = listOf("Item kind" to kind, "Quantity" to qty, "Amount" to amount, "Date" to day),
        actionLabel = "Record sale",
        onSubmit = { run {
            val code = checkNotNull(currency) { "The farm currency is still loading" }
            val amountMinor = amount.value.toScaledLongExact(FarmCurrency.minorDigits(code), "Amount")
            val quantityMilli = qty.value.toScaledLongExact(3, "Quantity")
            ops.recordSale(
                RecordSale(
                    saleId = UUID.randomUUID().toString(),
                    itemKind = kind.value,
                    quantityMilli = quantityMilli,
                    amountMinor = amountMinor,
                    currency = code,
                    occurredEpochDay = LocalDate.parse(day.value).toEpochDay(),
                ),
                newContext(),
            )
        } },
        onBack = onBack,
        saved = saved.value,
        extra = { recordActions() },
    ) }
}
