package com.farmos.app

import com.farmos.core.design.FarmMetricStrip
import com.farmos.core.design.FarmMetricValue
import com.farmos.core.design.FarmMetricBars
import com.farmos.core.design.FarmBarValue
import com.farmos.core.design.FarmModuleTools
import com.farmos.core.design.FarmToolAction
import com.farmos.core.design.FarmToolGroup
import com.farmos.core.design.FarmSectionTabs
import com.farmos.core.design.FarmWorkflowTimeline
import com.farmos.core.design.FarmWorkflowStep
import com.farmos.core.design.FarmWorkflowState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import com.farmos.core.design.FarmActionButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.farmos.feature.ops.SalesRecordNavigator
import com.farmos.feature.ops.SimpleCaptureScreen

@Composable
internal fun SalesModuleContent(state: SalesModuleState) {
    with(state) {
    var homeSection by rememberSaveable(farmId) { mutableStateOf("Overview") }
    when (page.value) {
        SalesModulePage.HOME -> SalesRecordNavigator(records.value, customers, animalSale) { recordActions -> SimpleCaptureScreen(
            screenId = "FOS-SALES-001", title = "Sales",
            help = "Sales and customers",
            empty = "No sales on this device.", rows = rows.value, busy = busy.value, error = error.value,
            fields = emptyList(),
            actionLabel = "Record quick sale",
            onSubmit = { page.value = SalesModulePage.CREATE },
            onBack = onBack,
            overview = {
                FarmSectionTabs(listOf("Overview", "Record", "Tools", "Records"), homeSection, { homeSection = it })
                if (homeSection == "Overview") {
                FarmMetricStrip(listOf(FarmMetricValue("Recorded sales", records.value.saleCount?.toString() ?: "—"), FarmMetricValue("Currencies", records.value.totals.size.toString())))
                records.value.totals.forEach { total -> FarmMetricStrip(listOf(FarmMetricValue(total.currency, salesMoney(total.amountMinor, total.currency), "${total.saleCount} sales"))) }
                com.farmos.core.design.FarmPrimaryButton(onClick = { page.value = SalesModulePage.CREATE }, modifier = Modifier.fillMaxWidth()) { Text("Record quick sale") }
                }
            },
            navigation = { if (homeSection == "Tools") {
                FarmModuleTools(listOf(
                    FarmToolGroup("Sell", listOf(
                        FarmToolAction("Create sale", { page.value = SalesModulePage.CREATE }),
                        FarmToolAction("Produce sale", { page.value = SalesModulePage.PRODUCE }),
                        FarmToolAction("Rabbit reservation sale", { page.value = SalesModulePage.RESERVATION }),
                    )),
                    FarmToolGroup("Review", listOf(
                        FarmToolAction("Sales orders", { page.value = SalesModulePage.ORDERS }),
                        FarmToolAction("Delivery or collection", { page.value = SalesModulePage.DELIVERY }),
                        FarmToolAction("Sales report", { page.value = SalesModulePage.REPORT }),
                    )),
                ), title = "Sales tools")
                recordActions()
            } },
            captureVisible = homeSection == "Record",
            recordsVisible = homeSection == "Records",
            showPrimaryAction = true,
            saved = saved.value,
            extra = {
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
            help = "A recorded sale also records the income.",
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
            help = "Record produce sold and the income received.",
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
