package com.farmos.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.design.FarmSelectorOption
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.ops.CreateInventoryItem
import com.farmos.domain.ops.IssueInventoryLot
import com.farmos.domain.ops.MoveInventory
import com.farmos.domain.ops.ReceiveInventoryLot
import com.farmos.domain.ops.RecordReorderAlert
import com.farmos.domain.ops.SetInventoryReorder
import com.farmos.feature.ops.InventoryReadModel
import com.farmos.feature.ops.InventoryScreen
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.launch

/** Internal pages of the inventory module. */
private enum class InventoryPage {
    HOME,
    SUPPLIERS,
    REPORT,
}

@Composable
fun InventoryModuleHost(
    farmId: String,
    ops: RoomOpsRepository,
    newContext: () -> LocalCommandContext,
    enqueueSync: () -> Unit,
    onBack: () -> Unit,
    loadReadModel: suspend () -> InventoryReadModel = { InventoryReadModel() },
    /** Stock count and review (D-021), shown in place of the inventory pages while open. */
    stockCount: (@Composable (onBack: () -> Unit) -> Unit)? = null,
) {
    var counting by remember { mutableStateOf(false) }
    if (counting && stockCount != null) return stockCount { counting = false }
    var page by remember(farmId) { mutableStateOf(InventoryPage.HOME) }
    val scope = rememberCoroutineScope()
    var rows by remember(farmId) { mutableStateOf(emptyList<String>()) }
    var itemOptions by remember(farmId) { mutableStateOf(emptyList<FarmSelectorOption>()) }
    var readModel by remember(farmId) { mutableStateOf(InventoryReadModel()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        val items = ops.items()
        rows = items.map { item ->
            "${item.id} ${item.sku} · ${item.name} · ${item.quantityMilli} ${item.unit}"
        }
        itemOptions = items.map { item ->
            FarmSelectorOption(item.id, "${item.name} · ${item.sku}", "${BigDecimal.valueOf(item.quantityMilli, 3).stripTrailingZeros().toPlainString()} ${item.unit} on hand")
        }
        readModel = loadReadModel()
    }

    fun quantityMilli(text: String): Long = BigDecimal(text.replace(',', '.'))
        .movePointRight(3)
        .longValueExact()

    fun runWrite(block: suspend () -> Unit) {
        scope.launch {
            busy = true
            error = null
            runCatching {
                block()
                refresh()
            }.onSuccess {
                enqueueSync()
            }.onFailure { failure ->
                error = failure.message
            }
            busy = false
        }
    }

    LaunchedEffect(farmId) {
        runCatching { refresh() }
            .onFailure { error = it.message }
    }

    when (page) {
        InventoryPage.HOME -> {
            androidx.compose.material3.TextButton(
                onClick = { page = InventoryPage.SUPPLIERS },
                modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
            ) { androidx.compose.material3.Text("Supplier bindings") }
            androidx.compose.material3.TextButton(
                onClick = { page = InventoryPage.REPORT },
                modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
            ) { androidx.compose.material3.Text("Inventory report") }
            InventoryScreen(
            rows = rows,
        itemOptions = itemOptions,
        busy = busy,
        error = error,
        onCreate = { sku, name, unit ->
            runWrite {
                ops.createItem(
                    CreateInventoryItem(UUID.randomUUID().toString(), sku, name, unit),
                    newContext(),
                )
            }
        },
        onMove = { itemId, direction, quantity ->
            runWrite {
                ops.move(
                    MoveInventory(
                        movementId = UUID.randomUUID().toString(),
                        itemId = itemId,
                        direction = direction,
                        quantityMilli = quantityMilli(quantity),
                        occurredAtEpochMillis = System.currentTimeMillis(),
                    ),
                    newContext(),
                )
            }
        },
        onReceiveLot = { itemId, lotCode, expiry, quantity ->
            runWrite {
                ops.receiveLot(
                    ReceiveInventoryLot(
                        lotId = UUID.randomUUID().toString(),
                        itemId = itemId,
                        lotCode = lotCode,
                        expiresEpochDay = LocalDate.parse(expiry).toEpochDay(),
                        quantityMilli = quantityMilli(quantity),
                    ),
                    newContext(),
                )
            }
        },
        onIssueLot = { itemId, quantity ->
            runWrite {
                ops.issueLot(
                    IssueInventoryLot(
                        issueId = UUID.randomUUID().toString(),
                        itemId = itemId,
                        quantityMilli = quantityMilli(quantity),
                    ),
                    newContext(),
                )
            }
        },
        onSetReorder = { itemId, quantity ->
            runWrite {
                ops.setReorder(
                    SetInventoryReorder(itemId, quantityMilli(quantity)),
                    newContext(),
                )
            }
        },
        onRecordReorder = { itemId, day ->
            runWrite {
                ops.recordReorderAlert(
                    RecordReorderAlert(
                        alertId = UUID.randomUUID().toString(),
                        itemId = itemId,
                        occurredEpochDay = LocalDate.parse(day).toEpochDay(),
                    ),
                    newContext(),
                )
            }
        },
        onBack = onBack,
        readModel = readModel,
        onOpenStockCount = stockCount?.let { { counting = true } },
        )
        }
        InventoryPage.SUPPLIERS -> SupplierBindingPage(
            items = itemOptions,
            loadSuppliers = { ops.suppliers() },
            loadPurchases = { ops.purchases() },
            onBack = { page = InventoryPage.HOME },
        )
        InventoryPage.REPORT -> InventoryReportPage(
            items = itemOptions,
            readModel = readModel,
            onBack = { page = InventoryPage.HOME },
        )
    }
}

/**
 * FOS-INV-018 — Supplier Binding: which suppliers have supplied each inventory item, from the
 * purchase records on this device. Purchases bind a supplier to an item (RecordPurchase carries
 * both); this surface makes those bindings visible per item.
 */
@Composable
private fun SupplierBindingPage(
    items: List<FarmSelectorOption>,
    loadSuppliers: suspend () -> List<com.farmos.core.database.SupplierEntity>,
    loadPurchases: suspend () -> List<com.farmos.core.database.PurchaseEntity>,
    onBack: () -> Unit,
) {
    var suppliers by remember { mutableStateOf<List<com.farmos.core.database.SupplierEntity>?>(null) }
    var purchases by remember { mutableStateOf<List<com.farmos.core.database.PurchaseEntity>?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        suppliers = runCatching { loadSuppliers() }.getOrElse { emptyList() }
        purchases = runCatching { loadPurchases() }.getOrElse { emptyList() }
    }
    val names = suppliers.orEmpty().associate { it.id to it.name }
    val byItem = purchases.orEmpty().groupBy { it.itemId }
    FarmOperationalPage(
        screenId = "FOS-INV-018",
        title = "Supplier bindings",
        subtitle = "Which suppliers have supplied each item, from purchase records.",
        onBack = onBack,
    ) {
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
private fun InventoryReportPage(
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
            items.forEach { item ->
                androidx.compose.material3.Text("${item.label} · ${item.description ?: ""}")
            }
        }
    }
}
