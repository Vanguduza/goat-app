package com.farmos.app

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.farmos.core.database.InventoryItemEntity
import com.farmos.core.design.FarmEntitySelector
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalRows
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.design.FarmSelectionAtoms
import com.farmos.core.design.FarmSelectorOption
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.ops.CreateSupplier
import com.farmos.domain.ops.FarmCurrency
import com.farmos.domain.ops.RecordPurchase
import com.farmos.feature.ops.ProcurementCurrencyTotal
import com.farmos.feature.ops.ProcurementPurchaseView
import com.farmos.feature.ops.ProcurementRecordNavigator
import com.farmos.feature.ops.ProcurementRecords
import com.farmos.feature.ops.SimpleCaptureScreen
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.launch

private enum class ProcurementModulePage { HOME, ORDERS, CREATE, RECEIVE, TO_INVENTORY, REPORT }

/**
 * Dedicated procurement orchestration. Purchase recording preserves its inventory-and-expense
 * transaction boundary.
 *
 * FOS-PROC-004 — purchase orders: the recorded purchase register with purchase detail.
 * FOS-PROC-006 — create purchase: the governed purchase capture form (root tag FOS-PROC-001).
 * FOS-PROC-007 — receive purchase: receipt detail; purchases are received at record by the domain
 * boundary (RecordPurchase posts the inventory receive movement and the expense atomically), so
 * there is no separate mark-received transition.
 * FOS-PROC-008 — purchase to inventory: trace of each purchase's inventory posting.
 * FOS-PROC-010 — procurement report: read-only totals per supplier and currency.
 */
@Composable
fun ProcurementModuleHost(
    farmId: String,
    ops: RoomOpsRepository,
    newContext: () -> LocalCommandContext,
    enqueueSync: () -> Unit,
    onBack: () -> Unit,
    loadRecords: suspend () -> ProcurementRecords = { ProcurementRecords() },
    loadCurrency: suspend () -> String = { FarmCurrency.DEFAULT_CODE },
) {
    val scope = rememberCoroutineScope()
    val currency by rememberFarmCurrency(farmId, loadCurrency)
    val busy = remember { mutableStateOf(false) }
    val saved = remember { mutableStateOf(false) }
    val error = remember { mutableStateOf<String?>(null) }
    val rows = remember { mutableStateOf(emptyList<String>()) }
    val records = remember(farmId) { mutableStateOf(ProcurementRecords()) }
    val supplierOptions = remember(farmId) { mutableStateOf(emptyList<FarmSelectorOption>()) }
    val itemOptions = remember(farmId) { mutableStateOf(emptyList<FarmSelectorOption>()) }
    val items = remember(farmId) { mutableStateOf(emptyList<InventoryItemEntity>()) }
    suspend fun refresh() {
        val supplierRows = ops.suppliers()
        supplierOptions.value = supplierRows.map { FarmSelectorOption(it.id, it.name, "Lead time ${it.leadTimeDays} days") }
        val itemRows = ops.items()
        items.value = itemRows
        itemOptions.value = itemRows.map { FarmSelectorOption(it.id, "${it.name} · ${it.sku}", "${BigDecimal.valueOf(it.quantityMilli, 3).stripTrailingZeros().toPlainString()} ${it.unit} on hand") }
        val suppliers = supplierRows.map { "${it.id} ${it.name} · lead ${it.leadTimeDays} d" }
        val purchases = ops.purchases().map { "${it.id} item ${it.itemId} · ${it.quantityMilli} milli · ${it.amountMinor} ${it.currency}" }
        rows.value = suppliers + purchases
        records.value = loadRecords()
    }
    LaunchedEffect(farmId) { runCatching { refresh() } }
    fun run(block: suspend () -> Unit) {
        scope.launch { busy.value = true; error.value = null; saved.value = false; runCatching { block(); refresh() }.onSuccess { saved.value = true; enqueueSync() }.onFailure { error.value = it.message }; busy.value = false }
    }
    val page = remember { mutableStateOf(ProcurementModulePage.HOME) }
    val selectedPurchase = remember { mutableStateOf<ProcurementPurchaseView?>(null) }
    val backHome = { page.value = ProcurementModulePage.HOME; selectedPurchase.value = null }

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

private fun money(minor: Long, currency: String): String =
    BigDecimal.valueOf(minor, FarmCurrency.minorDigits(currency)).stripTrailingZeros().toPlainString() + " " + currency

/** FOS-PROC-004 — purchase orders: the recorded purchase register with purchase detail. */
@Composable
private fun PurchaseOrderListScreen(
    records: ProcurementRecords,
    onSelect: (ProcurementPurchaseView?) -> Unit,
    selected: ProcurementPurchaseView?,
    onBack: () -> Unit,
) {
    FarmOperationalPage(screenId = "FOS-PROC-004", title = "Purchase orders", subtitle = "Recorded purchases on this device, most recent first.", onBack = onBack) {
        if (selected != null) {
            FarmOperationalSection("Purchase detail") {
                Text("Supplier: ${selected.supplierName}")
                Text("Item: ${selected.itemLabel}")
                Text("Quantity: ${selected.quantity}")
                Text("Amount: ${money(selected.amountMinor, selected.currency)}")
                Text("Date: ${LocalDate.ofEpochDay(selected.epochDay)}")
                TextButton(onClick = { onSelect(null) }) { Text("Close detail") }
            }
        }
        FarmOperationalSection("Orders") {
            if (records.purchases.isEmpty()) {
                Text("No purchases recorded on this device.")
            } else {
                records.purchases.forEach { purchase ->
                    TextButton(onClick = { onSelect(purchase) }, modifier = Modifier.fillMaxWidth()) {
                        Text("${purchase.supplierName} · ${purchase.itemLabel} · ${money(purchase.amountMinor, purchase.currency)}", modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

/** FOS-PROC-006 — create purchase: the governed purchase capture form. */
@Composable
private fun PurchaseCaptureScreen(
    currency: String?,
    supplierOptions: List<FarmSelectorOption>,
    itemOptions: List<FarmSelectorOption>,
    ops: RoomOpsRepository,
    newContext: () -> LocalCommandContext,
    busy: MutableState<Boolean>,
    error: MutableState<String?>,
    saved: MutableState<Boolean>,
    run: (suspend () -> Unit) -> Unit,
    onBack: () -> Unit,
) {
    val supplierId = remember { mutableStateOf("") }
    val itemId = remember { mutableStateOf("") }
    val qty = remember { mutableStateOf("") }
    val amount = remember { mutableStateOf("") }
    val day = remember { mutableStateOf(LocalDate.now().toString()) }
    FarmOperationalPage(
        screenId = "FOS-PROC-006",
        title = "Create purchase",
        subtitle = "Recording a purchase receives the quantity into inventory and posts the expense in one governed boundary.",
        onBack = onBack,
    ) {
        if (saved.value) {
            FarmOperationalSection("Recorded") { Text("The purchase committed locally; inventory and expense posted.") }
        }
        error.value?.let { FarmOperationalSection("Needs attention") { Text(it) } }
        FarmOperationalSection("Purchase") {
            FarmEntitySelector(FarmSelectionAtoms.SUPPLIER_SELECTOR, "Supplier", supplierOptions, supplierId.value.ifBlank { null }, { supplierId.value = it }, "Create a supplier first.", enabled = !busy.value)
            FarmEntitySelector(FarmSelectionAtoms.INVENTORY_ITEM_SELECTOR, "Inventory item", itemOptions, itemId.value.ifBlank { null }, { itemId.value = it }, "No inventory items on this device.", enabled = !busy.value)
            OutlinedTextField(qty.value, { qty.value = it }, label = { Text("Quantity") }, modifier = Modifier.fillMaxWidth(), enabled = !busy.value)
            OutlinedTextField(amount.value, { amount.value = it }, label = { Text("Amount (${currency ?: "…"})") }, modifier = Modifier.fillMaxWidth(), enabled = !busy.value)
            OutlinedTextField(day.value, { day.value = it }, label = { Text("Date") }, placeholder = { Text("YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth(), enabled = !busy.value)
        }
        Button(
            onClick = {
                run {
                    val code = checkNotNull(currency) { "The farm currency is still loading" }
                    require(supplierId.value.isNotBlank()) { "Choose a supplier" }
                    require(itemId.value.isNotBlank()) { "Choose an inventory item" }
                    val quantityMilli = qty.value.toScaledLongExact(3, "Quantity")
                    val amountMinor = amount.value.toScaledLongExact(FarmCurrency.minorDigits(code), "Amount")
                    val epochDay = day.value.ifBlank { LocalDate.now().toString() }.let { LocalDate.parse(it).toEpochDay() }
                    ops.recordPurchase(
                        RecordPurchase(UUID.randomUUID().toString(), supplierId.value, itemId.value, quantityMilli, amountMinor, code, epochDay),
                        newContext(),
                    )
                }
            },
            enabled = !busy.value,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (busy.value) "Recording…" else "Record purchase") }
    }
}

/**
 * FOS-PROC-007 — receive purchase: receipt detail for each recorded purchase. The domain records a
 * purchase and its inventory receive in one atomic boundary (RecordPurchase posts the inventory
 * "receive" movement and the expense together), so receipt state is shown truthfully and there is
 * no separate mark-received transition to offer.
 */
@Composable
private fun PurchaseReceiptScreen(
    records: ProcurementRecords,
    items: List<InventoryItemEntity>,
    onBack: () -> Unit,
) {
    val picked = remember { mutableStateOf<ProcurementPurchaseView?>(null) }
    val itemsByName = remember(items) { items.associateBy { it.name } }
    FarmOperationalPage(
        screenId = "FOS-PROC-007",
        title = "Receive purchase",
        subtitle = "Each purchase below was received into inventory at record. Select one to see its receipt.",
        onBack = onBack,
    ) {
        picked.value?.let { purchase ->
            val item = itemsByName[purchase.itemLabel]
            FarmOperationalSection("Receipt") {
                Text("Supplier: ${purchase.supplierName}")
                Text("Item: ${purchase.itemLabel}")
                Text("Received quantity: ${purchase.quantity}")
                Text("Received date: ${LocalDate.ofEpochDay(purchase.epochDay)}")
                Text("Inventory on hand: ${item?.let { BigDecimal.valueOf(it.quantityMilli, 3).stripTrailingZeros().toPlainString() + " " + it.unit } ?: "item not on this device"}")
                Text("Status: received — the receive movement and expense posted with the purchase record.")
                TextButton(onClick = { picked.value = null }) { Text("Close receipt") }
            }
        }
        FarmOperationalSection("Purchases") {
            if (records.purchases.isEmpty()) {
                Text("No purchases recorded on this device.")
            } else {
                records.purchases.forEach { purchase ->
                    TextButton(onClick = { picked.value = purchase }, modifier = Modifier.fillMaxWidth()) {
                        Text("${purchase.supplierName} · ${purchase.itemLabel} · received ${LocalDate.ofEpochDay(purchase.epochDay)}", modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

/**
 * FOS-PROC-008 — purchase to inventory: trace of each purchase's inventory posting. The posting
 * happens inside the RecordPurchase boundary; this surface makes it visible per purchase.
 */
@Composable
private fun PurchaseToInventoryScreen(
    records: ProcurementRecords,
    items: List<InventoryItemEntity>,
    onBack: () -> Unit,
) {
    val itemsByName = remember(items) { items.associateBy { it.name } }
    FarmOperationalPage(
        screenId = "FOS-PROC-008",
        title = "Purchase to inventory",
        subtitle = "How each recorded purchase posted into inventory. Postings are made by the governed purchase boundary, not edited here.",
        onBack = onBack,
    ) {
        if (records.purchases.isEmpty()) {
            FarmOperationalSection("No purchases") { Text("No purchases recorded on this device.") }
        } else {
            records.purchases.forEach { purchase ->
                val item = itemsByName[purchase.itemLabel]
                FarmOperationalSection("${purchase.supplierName} · ${LocalDate.ofEpochDay(purchase.epochDay)}") {
                    Text("Item: ${purchase.itemLabel}")
                    Text("Posted quantity: ${purchase.quantity}")
                    Text("Movement: receive (posted with the purchase record)")
                    Text("Item on hand now: ${item?.let { BigDecimal.valueOf(it.quantityMilli, 3).stripTrailingZeros().toPlainString() + " " + it.unit } ?: "item not on this device"}")
                }
            }
        }
    }
}

/** FOS-PROC-010 — procurement report: read-only totals per supplier and currency. */
@Composable
private fun ProcurementReportScreen(
    records: ProcurementRecords,
    onBack: () -> Unit,
) {
    FarmOperationalPage(
        screenId = "FOS-PROC-010",
        title = "Procurement report",
        subtitle = "Totals are exhaustive per supplier and currency from this device's records. Currencies are never added together.",
        onBack = onBack,
    ) {
        FarmOperationalSection("Suppliers") {
            if (records.suppliers.isEmpty()) {
                Text("No suppliers on this device.")
            } else {
                records.suppliers.forEach { supplier ->
                    Text(supplier.name, style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                    supplier.totals.forEach { total: ProcurementCurrencyTotal ->
                        Text("  ${total.currency}: ${money(total.amountMinor, total.currency)} across ${total.purchaseCount} purchases")
                    }
                }
            }
        }
        FarmOperationalRows(
            rows = records.purchases.map { "${LocalDate.ofEpochDay(it.epochDay)} · ${it.supplierName} · ${it.itemLabel} · ${money(it.amountMinor, it.currency)}" },
            emptyTitle = "No purchases",
            emptyHint = "Record a purchase to see it here.",
        )
    }
}
