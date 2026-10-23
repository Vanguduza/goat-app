package com.farmos.feature.ops

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.farmos.core.design.AnimalFarmTheme
import com.farmos.core.design.FarmMetricStrip
import com.farmos.core.design.FarmMetricValue
import com.farmos.core.design.FarmModuleTools
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmPrimaryButton
import com.farmos.core.design.FarmToolAction
import com.farmos.core.design.FarmToolGroup
import com.farmos.core.design.FarmVisualClass

/** FOS-INV-001. P04 inventory overview; counts and stock changes remain separate. */
@Composable
internal fun InventoryDashboard(rows: List<String>, model: InventoryReadModel, error: String?, onOpen: (InventoryPage) -> Unit, onBack: () -> Unit, onOpenStockCount: (() -> Unit)?, extraActions: @Composable () -> Unit) {
    fun tool(label: String, page: InventoryPage) = FarmToolAction(label, { onOpen(page) }, tag = "inventory-action:" + page.name)
    FarmOperationalPage("FOS-INV-001", "Inventory", "", FarmVisualClass.I2, onBack) {
        FarmMetricStrip(listOf(
            FarmMetricValue("Loaded items", rows.size.toString(), onClick = { onOpen(InventoryPage.ITEMS) }),
            FarmMetricValue("Low stock", InventoryRecords.lowStock(model).size.toString(), onClick = { onOpen(InventoryPage.LOW_STOCK) }),
            FarmMetricValue("Dated lots", model.openLots.size.toString(), onClick = { onOpen(InventoryPage.EXPIRY_QUEUE) }),
        ))
        FarmPrimaryButton(onClick = { onOpen(InventoryPage.RECEIVE) }, modifier = Modifier.fillMaxWidth()) { Text("Receive") }
        FarmModuleTools(listOf(
            FarmToolGroup("Stock movement", listOf(
                tool("Issue", InventoryPage.ISSUE), tool("Receive dated lot", InventoryPage.RECEIVE_LOT),
                tool("Issue oldest-expiry lot", InventoryPage.FEFO_ISSUE),
            )),
            FarmToolGroup("Stock records", listOf(
                tool("Open inventory list", InventoryPage.ITEMS), tool("Search inventory", InventoryPage.SEARCH),
                tool("Low stock", InventoryPage.LOW_STOCK), tool("Expiry queue", InventoryPage.EXPIRY_QUEUE),
                tool("Movement history", InventoryPage.MOVEMENTS),
            )),
            FarmToolGroup("Count and review", listOfNotNull(onOpenStockCount?.let { FarmToolAction("Count stock and review differences", it) })),
            FarmToolGroup("Setup and reorder", listOf(
                tool("Create inventory item", InventoryPage.CREATE), tool("Set reorder point", InventoryPage.REORDER_RULE),
                tool("Record reorder alert", InventoryPage.REORDER_ALERT),
            )),
        ).filter { it.actions.isNotEmpty() }, title = "Inventory tools")
        extraActions()
        error?.let { Text(it, color = AnimalFarmTheme.colors.critical) }
    }
}
