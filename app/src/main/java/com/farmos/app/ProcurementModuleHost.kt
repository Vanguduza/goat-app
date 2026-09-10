package com.farmos.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.ops.CreateSupplier
import com.farmos.domain.ops.RecordPurchase
import com.farmos.feature.ops.SimpleCaptureScreen
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.launch

/** Dedicated procurement orchestration. Purchase recording preserves its inventory-and-expense transaction boundary. */
@Composable
fun ProcurementModuleHost(
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
    suspend fun refresh() {
        val suppliers = ops.suppliers().map { "${it.id} ${it.name} · lead ${it.leadTimeDays} d" }
        val purchases = ops.purchases().map { "${it.id} item ${it.itemId} · ${it.quantityMilli} milli · ${it.amountMinor} ${it.currency}" }
        rows.value = suppliers + purchases
    }
    LaunchedEffect(farmId) { runCatching { refresh() } }
    fun run(block: suspend () -> Unit) {
        scope.launch { busy.value = true; error.value = null; runCatching { block(); refresh() }.onSuccess { enqueueSync() }.onFailure { error.value = it.message }; busy.value = false }
    }
    val name = remember { mutableStateOf("") }; val lead = remember { mutableStateOf("0") }
    val supplierId = remember { mutableStateOf("") }; val itemId = remember { mutableStateOf("") }
    val qty = remember { mutableStateOf("") }; val amount = remember { mutableStateOf("") }; val day = remember { mutableStateOf("") }
    SimpleCaptureScreen(
        screenId = "FOS-PROC-001", title = "Procurement",
        help = "A purchase receives inventory and posts an expense in integer minor units.",
        empty = "No suppliers on this device.", rows = rows.value, busy = busy.value, error = error.value,
        fields = listOf("Supplier name" to name, "Lead time days" to lead), actionLabel = "Create supplier",
        onSubmit = { run { ops.createSupplier(CreateSupplier(UUID.randomUUID().toString(), name.value, lead.value.toIntOrNull() ?: 0), newContext()) } },
        onBack = onBack,
        extra = {
            androidx.compose.material3.OutlinedTextField(supplierId.value,{supplierId.value=it},label={androidx.compose.material3.Text("Supplier id")},modifier=androidx.compose.ui.Modifier.fillMaxWidth())
            androidx.compose.material3.OutlinedTextField(itemId.value,{itemId.value=it},label={androidx.compose.material3.Text("Inventory item id")},modifier=androidx.compose.ui.Modifier.fillMaxWidth())
            androidx.compose.material3.OutlinedTextField(qty.value,{qty.value=it},label={androidx.compose.material3.Text("Quantity")},modifier=androidx.compose.ui.Modifier.fillMaxWidth())
            androidx.compose.material3.OutlinedTextField(amount.value,{amount.value=it},label={androidx.compose.material3.Text("Amount")},modifier=androidx.compose.ui.Modifier.fillMaxWidth())
            androidx.compose.material3.OutlinedTextField(day.value,{day.value=it},label={androidx.compose.material3.Text("Date")},placeholder={androidx.compose.material3.Text("YYYY-MM-DD")},modifier=androidx.compose.ui.Modifier.fillMaxWidth())
            androidx.compose.material3.Button(onClick={run { val quantity=qty.value.replace(',','.').toDoubleOrNull()?:error("Enter a quantity"); val major=amount.value.replace(',','.').toDoubleOrNull()?:error("Enter an amount"); ops.recordPurchase(RecordPurchase(UUID.randomUUID().toString(),supplierId.value,itemId.value,(quantity*1000.0).toLong(),(major*100.0).toLong(),LocalDate.parse(day.value).toEpochDay()),newContext()) }},enabled=!busy.value&&supplierId.value.isNotBlank()&&itemId.value.isNotBlank()&&qty.value.isNotBlank()&&amount.value.isNotBlank()&&day.value.isNotBlank()){androidx.compose.material3.Text("Record purchase")}
        },
    )
}
