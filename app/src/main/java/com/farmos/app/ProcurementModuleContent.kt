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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import com.farmos.core.design.FarmActionButton as TextButton
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
    var homeSection by rememberSaveable(farmId) { mutableStateOf("Overview") }
    when (page.value) {
        ProcurementModulePage.HOME -> {
            val name = remember { mutableStateOf("") }; val lead = remember { mutableStateOf("0") }
            ProcurementRecordNavigator(records.value) { recordActions -> SimpleCaptureScreen(
                screenId = "FOS-PROC-001", title = "Procurement",
                help = "Suppliers, purchases and receipts",
                empty = "No suppliers on this device.", rows = rows.value, busy = busy.value, error = error.value,
                fields = listOf("Supplier name" to name, "Lead time days" to lead), actionLabel = "Create supplier",
                onSubmit = { run { ops.createSupplier(CreateSupplier(UUID.randomUUID().toString(), name.value, lead.value.toIntOrNull() ?: 0), newContext()) } },
                onBack = onBack,
            overview = {
                FarmSectionTabs(listOf("Overview", "Record", "Tools", "Records"), homeSection, { homeSection = it })
                if (homeSection == "Overview") {
                    FarmMetricStrip(listOf(FarmMetricValue("Suppliers", records.value.suppliers.size.toString()), FarmMetricValue("Recorded purchases", records.value.purchaseCount?.toString() ?: "—")))
                    if (records.value.suppliers.isNotEmpty()) FarmMetricBars("Purchases by supplier", records.value.suppliers.map { FarmBarValue(it.name, it.purchaseCount.toDouble(), "${it.purchaseCount} purchases", "Lead time ${it.leadTimeDays} days") }, "purchases")
                }
            },
            navigation = { if (homeSection == "Tools") {
                    FarmModuleTools(listOf(
                        FarmToolGroup("Purchase", listOf(
                            FarmToolAction("Create purchase", { openPage(ProcurementModulePage.CREATE) }),
                            FarmToolAction("Purchase orders", { openPage(ProcurementModulePage.ORDERS) }),
                        )),
                        FarmToolGroup("Receipts", listOf(
                            FarmToolAction("Receive purchase", { openPage(ProcurementModulePage.RECEIVE) }),
                            FarmToolAction("Purchase to inventory", { openPage(ProcurementModulePage.TO_INVENTORY) }),
                            FarmToolAction("Procurement report", { openPage(ProcurementModulePage.REPORT) }),
                        )),
                    ), title = "Procurement tools")
                    recordActions()
            } },
            captureVisible = homeSection == "Record",
            recordsVisible = homeSection == "Records",
            showPrimaryAction = true,
                saved = saved.value,
                extra = {
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
