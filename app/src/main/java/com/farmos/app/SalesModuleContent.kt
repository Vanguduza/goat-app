package com.farmos.app

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.farmos.feature.ops.SalesRecordNavigator
import com.farmos.feature.ops.SimpleCaptureScreen

@Composable
internal fun SalesModuleContent(state: SalesModuleState) {
    with(state) {
    when (page.value) {
        SalesModulePage.HOME -> SalesRecordNavigator(records.value, customers, animalSale) { recordActions -> SimpleCaptureScreen(
            screenId = "FOS-SALES-001", title = "Sales",
            help = "A sale posts income in integer minor units of ${currency ?: "the farm currency"}. This is farm unit economics, not a statutory ledger.",
            empty = "No sales on this device.", rows = rows.value, busy = busy.value, error = error.value,
            fields = emptyList(),
            actionLabel = "Record quick sale",
            onSubmit = { page.value = SalesModulePage.CREATE },
            onBack = onBack,
            saved = saved.value,
            extra = {
                recordActions()
                TextButton(onClick = { page.value = SalesModulePage.ORDERS }, modifier = Modifier.fillMaxWidth()) { Text("Sales orders") }
                TextButton(onClick = { page.value = SalesModulePage.CREATE }, modifier = Modifier.fillMaxWidth()) { Text("Create sale") }
                TextButton(onClick = { page.value = SalesModulePage.PRODUCE }, modifier = Modifier.fillMaxWidth()) { Text("Produce sale") }
                TextButton(onClick = { page.value = SalesModulePage.RESERVATION }, modifier = Modifier.fillMaxWidth()) { Text("Rabbit reservation sale") }
                TextButton(onClick = { page.value = SalesModulePage.REPORT }, modifier = Modifier.fillMaxWidth()) { Text("Sales report") }
                TextButton(onClick = { page.value = SalesModulePage.DELIVERY }, modifier = Modifier.fillMaxWidth()) { Text("Delivery or collection") }
                if (customerSearch != null) {
                    Text("Customer search is available on the create-sale form.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                }
            },
        ) }
        SalesModulePage.ORDERS -> SalesOrderListScreen(
            records = records.value,
            onSelect = { selectedSale.value = it },
            selected = selectedSale.value,
            onBack = backHome,
        )
        SalesModulePage.CREATE -> SaleCaptureScreen(
            title = "Create sale",
            screenId = "FOS-SALES-006",
            help = "Record a sale. The amount posts income in integer minor units of the farm currency.",
            currency = currency,
            customerSearch = customerSearch,
            customerCommands = customerCommands,
            ops = ops,
            newContext = newContext,
            busy = busy,
            error = error,
            saved = saved,
            run = ::run,
            itemKinds = null,
            onBack = backHome,
        )
        SalesModulePage.PRODUCE -> SaleCaptureScreen(
            title = "Produce sale",
            screenId = "FOS-SALES-008",
            help = "Record a sale of farm produce (milk, eggs, honey, wool). Same governed command path as any sale.",
            currency = currency,
            customerSearch = customerSearch,
            customerCommands = customerCommands,
            ops = ops,
            newContext = newContext,
            busy = busy,
            error = error,
            saved = saved,
            run = ::run,
            itemKinds = PRODUCE_KINDS,
            onBack = backHome,
        )
        SalesModulePage.RESERVATION -> RabbitReservationSaleScreen(
            currency = currency,
            loadRabbitContracts = loadRabbitContracts,
            ops = ops,
            newContext = newContext,
            busy = busy,
            error = error,
            saved = saved,
            run = ::run,
            onBack = backHome,
        )
        SalesModulePage.REPORT -> SalesReportScreen(
            records = records.value,
            onBack = backHome,
        )
        SalesModulePage.DELIVERY -> SaleDeliveryScreen(onBack = backHome)
    }

    }
}
