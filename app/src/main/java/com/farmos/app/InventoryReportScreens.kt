package com.farmos.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.design.FarmSelectorOption
import com.farmos.core.design.runSuspendCatching
import com.farmos.feature.ops.InventoryReadModel

/** Internal pages of the inventory module. */
internal enum class InventoryPage {
    HOME,
    SUPPLIERS,
    REPORT,
}

/**
 * FOS-INV-018 — Supplier Binding: which suppliers have supplied each inventory item, from the
 * purchase records on this device. Purchases bind a supplier to an item (RecordPurchase carries
 * both); this surface makes those bindings visible per item.
 */
@Composable
internal fun SupplierBindingPage(
    items: List<FarmSelectorOption>,
    loadSuppliers: suspend () -> List<com.farmos.core.database.SupplierEntity>,
    loadPurchases: suspend () -> List<com.farmos.core.database.PurchaseEntity>,
    onBack: () -> Unit,
) {
    var suppliers by remember { mutableStateOf<List<com.farmos.core.database.SupplierEntity>?>(null) }
    var purchases by remember { mutableStateOf<List<com.farmos.core.database.PurchaseEntity>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        runSuspendCatching { loadSuppliers() to loadPurchases() }
            .onSuccess { (loadedSuppliers, loadedPurchases) -> suppliers = loadedSuppliers; purchases = loadedPurchases }
            .onFailure { error = it.message ?: "Supplier records could not be read" }
    }
    val names = suppliers.orEmpty().associate { it.id to it.name }
    val byItem = purchases.orEmpty().groupBy { it.itemId }
    FarmOperationalPage(
        screenId = "FOS-INV-018",
        title = "Supplier bindings",
        subtitle = "Which suppliers have supplied each item, from purchase records.",
        onBack = onBack,
    ) {
        if (error != null) {
            androidx.compose.material3.Text(error.orEmpty())
            return@FarmOperationalPage
        }
        if (suppliers == null) {
            androidx.compose.material3.Text("Reading suppliers")
            return@FarmOperationalPage
        }
        if (items.isEmpty()) {
            androidx.compose.material3.Text("No inventory items on this device.")
            return@FarmOperationalPage
        }
        FarmOperationalSection(
            title = "Bindings",
            description = "A binding is a recorded purchase linking a supplier to an item.",
        ) {
            items.forEach { item ->
                val itemPurchases = byItem[item.id].orEmpty()
                val supplierNames = itemPurchases.mapNotNull { names[it.supplierId] }.distinct()
                val label = if (supplierNames.isEmpty()) "no supplier on record" else supplierNames.joinToString(", ")
                androidx.compose.material3.Text("${item.label} · $label · ${itemPurchases.size} purchases")
            }
        }
    }
}

/**
 * FOS-INV-019 — Inventory Report: on-hand quantities per item with reorder state, from the
 * records on this device.
 */
@Composable
internal fun InventoryReportPage(
    items: List<FarmSelectorOption>,
    readModel: InventoryReadModel,
    onBack: () -> Unit,
) {
    FarmOperationalPage(
        screenId = "FOS-INV-019",
        title = "Inventory report",
        subtitle = "Stock on hand per item, from the records on this device.",
        onBack = onBack,
    ) {
        if (items.isEmpty()) {
            androidx.compose.material3.Text("No inventory items on this device.")
            return@FarmOperationalPage
        }
        FarmOperationalSection("On hand") {
            readModel.items.forEach { item ->
                val state = if (item.reorderMilli > 0 && item.quantityMilli <= item.reorderMilli) " · At or below reorder point" else ""
                androidx.compose.material3.Text("${item.sku} · ${item.name}: ${reportMilli(item.quantityMilli)} ${item.unit}$state")
            }
        }
    }
}


/** Extra dashboard routes use the existing section and button family inside the dashboard layout. */
@Composable
internal fun InventoryAdditionalActions(onOpen: (InventoryPage) -> Unit) {
    FarmOperationalSection("Suppliers and reports") {
        androidx.compose.material3.TextButton(onClick = { onOpen(InventoryPage.SUPPLIERS) }) {
            androidx.compose.material3.Text("Supplier bindings")
        }
        androidx.compose.material3.TextButton(onClick = { onOpen(InventoryPage.REPORT) }) {
            androidx.compose.material3.Text("Inventory report")
        }
    }
}
