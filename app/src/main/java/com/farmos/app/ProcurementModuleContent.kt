package com.farmos.app

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.farmos.domain.ops.CreateSupplier
import com.farmos.feature.ops.ProcurementRecordNavigator
import com.farmos.feature.ops.SimpleCaptureScreen
import java.util.UUID

@Composable
internal fun ProcurementModuleContent(state: ProcurementModuleState) {
    with(state) {
    when (page.value) {
        ProcurementModulePage.HOME -> {
            val name = remember { mutableStateOf("") }; val lead = remember { mutableStateOf("0") }
            ProcurementRecordNavigator(records.value) { recordActions -> SimpleCaptureScreen(
                screenId = "FOS-PROC-001", title = "Procurement",
                help = "A purchase receives inventory and posts an expense in integer minor units of ${currency ?: "the farm currency"}.",
                empty = "No suppliers on this device.", rows = rows.value, busy = busy.value, error = error.value,
                fields = listOf("Supplier name" to name, "Lead time days" to lead), actionLabel = "Create supplier",
                onSubmit = { run { ops.createSupplier(CreateSupplier(UUID.randomUUID().toString(), name.value, lead.value.toIntOrNull() ?: 0), newContext()) } },
                onBack = onBack,
                saved = saved.value,
                extra = {
                    recordActions()
                    TextButton(onClick = { page.value = ProcurementModulePage.ORDERS }, modifier = Modifier.fillMaxWidth()) { Text("Purchase orders") }
                    TextButton(onClick = { page.value = ProcurementModulePage.CREATE }, modifier = Modifier.fillMaxWidth()) { Text("Create purchase") }
                    TextButton(onClick = { page.value = ProcurementModulePage.RECEIVE }, modifier = Modifier.fillMaxWidth()) { Text("Receive purchase") }
                    TextButton(onClick = { page.value = ProcurementModulePage.TO_INVENTORY }, modifier = Modifier.fillMaxWidth()) { Text("Purchase to inventory") }
                    TextButton(onClick = { page.value = ProcurementModulePage.REPORT }, modifier = Modifier.fillMaxWidth()) { Text("Procurement report") }
                },
            ) }
        }
        ProcurementModulePage.ORDERS -> PurchaseOrderListScreen(
            records = records.value,
            onSelect = { selectedPurchase.value = it },
            selected = selectedPurchase.value,
            onBack = backHome,
        )
        ProcurementModulePage.CREATE -> PurchaseCaptureScreen(
            currency = currency,
            supplierOptions = supplierOptions.value,
            itemOptions = itemOptions.value,
            ops = ops,
            newContext = newContext,
            busy = busy,
            error = error,
            saved = saved,
            run = ::run,
            onBack = backHome,
        )
        ProcurementModulePage.RECEIVE -> PurchaseReceiptScreen(
            records = records.value,
            items = items.value,
            onBack = backHome,
        )
        ProcurementModulePage.TO_INVENTORY -> PurchaseToInventoryScreen(
            records = records.value,
            items = items.value,
            onBack = backHome,
        )
        ProcurementModulePage.REPORT -> ProcurementReportScreen(
            records = records.value,
            onBack = backHome,
        )
    }

    }
}
