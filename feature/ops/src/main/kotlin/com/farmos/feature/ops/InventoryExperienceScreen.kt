package com.farmos.feature.ops

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import com.farmos.core.design.FarmPrimaryButton as Button
import androidx.compose.material3.MaterialTheme
import com.farmos.core.design.FarmOutlinedTextField as OutlinedTextField
import androidx.compose.material3.Text
import com.farmos.core.design.FarmActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.farmos.core.design.FarmEntitySelector
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalRows
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.design.FarmSelectionAtoms
import com.farmos.core.design.FarmSelectorOption
import com.farmos.core.design.FarmVisualClass
import java.time.LocalDate
import java.time.ZoneId

internal enum class InventoryPage {
    DASHBOARD,
    ITEMS,
    CREATE,
    RECEIVE,
    ISSUE,
    RECEIVE_LOT,
    FEFO_ISSUE,
    REORDER_RULE,
    REORDER_ALERT,
    ITEM_DETAIL,
    LOT_DETAIL,
    EXPIRY_QUEUE,
    LOW_STOCK,
    MOVEMENTS,
    SEARCH,
}

/** FOS-INV-001..014/017 — inventory operating reference family; 003/009/010/011/014/017 are read-only records. */
@Composable
fun InventoryScreen(
    rows: List<String>,
    busy: Boolean,
    error: String?,
    onCreate: (sku: String, name: String, unit: String) -> Unit,
    onMove: (itemId: String, direction: String, quantity: String) -> Unit,
    onReceiveLot: (itemId: String, lotCode: String, expiry: String, quantity: String) -> Unit = { _, _, _, _ -> },
    onIssueLot: (itemId: String, quantity: String) -> Unit = { _, _ -> },
    onSetReorder: (itemId: String, quantity: String) -> Unit = { _, _ -> },
    onRecordReorder: (itemId: String, day: String) -> Unit = { _, _ -> },
    onBack: () -> Unit,
    readModel: InventoryReadModel = InventoryReadModel(),
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
    /** This farm's inventory items; every stock command references one of these, never a typed id. */
    itemOptions: List<FarmSelectorOption> = emptyList(),
    /** Opens stock count and review (FOS-INV-016 / 015, D-021); null hides the entry. */
    onOpenStockCount: (() -> Unit)? = null,
    extraActions: @Composable () -> Unit = {},
) {
    var page by rememberSaveable { mutableStateOf(InventoryPage.DASHBOARD) }
    var backStack by rememberSaveable { mutableStateOf(emptyList<InventoryPage>()) }
    var selectedItemId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedLotId by rememberSaveable { mutableStateOf<String?>(null) }
    val home = {
        backStack = emptyList()
        page = InventoryPage.DASHBOARD
    }
    fun open(next: InventoryPage) {
        backStack = backStack + page
        page = next
    }
    val back = {
        page = backStack.lastOrNull() ?: InventoryPage.DASHBOARD
        backStack = backStack.dropLast(1)
    }
    val openItem = { id: String ->
        selectedItemId = id
        open(InventoryPage.ITEM_DETAIL)
    }
    val openLot = { id: String ->
        selectedLotId = id
        open(InventoryPage.LOT_DETAIL)
    }
    val pageStateHolder = rememberSaveableStateHolder()
    pageStateHolder.SaveableStateProvider(page.name) {
    when (page) {
        InventoryPage.DASHBOARD -> InventoryDashboard(rows, readModel, error, { open(it) }, onBack, onOpenStockCount, extraActions)
        InventoryPage.ITEM_DETAIL -> InventoryItemDetailScreen(readModel, selectedItemId, today, zone, openLot, back)
        InventoryPage.LOT_DETAIL -> InventoryLotDetailScreen(readModel, selectedLotId, today, back)
        InventoryPage.EXPIRY_QUEUE -> InventoryExpiryQueueScreen(readModel, today, openLot, back)
        InventoryPage.LOW_STOCK -> InventoryLowStockScreen(readModel, openItem, back)
        InventoryPage.MOVEMENTS -> InventoryMovementHistoryScreen(readModel, zone, openItem, back)
        InventoryPage.SEARCH -> InventorySearchScreen(readModel, openItem, back)
        InventoryPage.ITEMS -> InventoryRows(rows, error, home)
        InventoryPage.CREATE -> CreateInventoryItemScreen(busy, error, onCreate, home)
        InventoryPage.RECEIVE -> InventoryMoveScreen("receive", itemOptions, busy, error, onMove, home)
        InventoryPage.ISSUE -> InventoryMoveScreen("issue", itemOptions, busy, error, onMove, home)
        InventoryPage.RECEIVE_LOT -> InventoryLotReceiveScreen(itemOptions, busy, error, onReceiveLot, home)
        InventoryPage.FEFO_ISSUE -> InventoryFefoIssueScreen(itemOptions, busy, error, onIssueLot, home)
        InventoryPage.REORDER_RULE -> InventoryReorderRuleScreen(itemOptions, busy, error, onSetReorder, home)
        InventoryPage.REORDER_ALERT -> InventoryReorderAlertScreen(itemOptions, busy, error, onRecordReorder, home)
    }
    }
}

@Composable
private fun InventoryRows(
    rows: List<String>,
    error: String?,
    onBack: () -> Unit,
) {
    FarmOperationalPage("FOS-INV-002", "Inventory list", "Farm stock available on this device.", onBack = onBack) {
        FarmOperationalRows(rows, "No inventory items yet", "Create an item before receiving stock.")
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun CreateInventoryItemScreen(
    busy: Boolean,
    error: String?,
    onCreate: (String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var sku by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf("kg") }
    FarmOperationalPage("FOS-INV-004", "Create inventory item", "Define the stock identity before receiving quantity.", onBack = onBack) {
        FarmOperationalSection("Item identity") {
            OutlinedTextField(
                sku,
                { sku = it },
                label = { Text("SKU") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                singleLine = true,
            )
            OutlinedTextField(
                name,
                { name = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                singleLine = true,
            )
            OutlinedTextField(
                unit,
                { unit = it },
                label = { Text("Unit") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                singleLine = true,
            )
            Button(onClick = {
                onCreate(sku, name, unit)
            }, enabled = !busy && sku.isNotBlank() && name.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Create item") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun InventoryMoveScreen(
    kind: String,
    itemOptions: List<FarmSelectorOption>,
    busy: Boolean,
    error: String?,
    onMove: (String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var itemId by rememberSaveable { mutableStateOf("") }
    var quantity by rememberSaveable { mutableStateOf("") }
    val receive = kind == "receive"
    FarmOperationalPage(
        if (receive) "FOS-INV-005" else "FOS-INV-007",
        if (receive) "Receive stock" else "Issue stock",
        if (receive) "Add ordinary on-hand quantity." else "Deduct ordinary on-hand quantity.",
        onBack = onBack,
    ) {
        FarmOperationalSection("Stock movement") {
            InventoryItemSelector(itemOptions, itemId, busy) { itemId = it }
            OutlinedTextField(quantity, {
                quantity = it
            }, label = { Text("Quantity") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            Button(onClick = {
                onMove(itemId, kind, quantity)
            }, enabled = !busy && itemId.isNotBlank() && quantity.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text(if (receive) "Receive stock" else "Issue stock")
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun InventoryLotReceiveScreen(
    itemOptions: List<FarmSelectorOption>,
    busy: Boolean,
    error: String?,
    onReceive: (String, String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var itemId by rememberSaveable { mutableStateOf("") }
    var lotCode by rememberSaveable { mutableStateOf("") }
    var expiry by rememberSaveable { mutableStateOf("") }
    var quantity by rememberSaveable { mutableStateOf("") }
    FarmOperationalPage("FOS-INV-006", "Receive dated lot", "Capture lot identity, expiry and quantity.", onBack = onBack) {
        FarmOperationalSection("Lot details") {
            InventoryItemSelector(itemOptions, itemId, busy) { itemId = it }
            OutlinedTextField(lotCode, {
                lotCode = it
            }, label = { Text("Lot code") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            OutlinedTextField(expiry, {
                expiry = it
            }, label = {
                Text("Expiry date")
            }, placeholder = { Text("YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            OutlinedTextField(quantity, {
                quantity = it
            }, label = { Text("Quantity") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            Button(
                onClick = { onReceive(itemId, lotCode, expiry, quantity) },
                enabled =
                    !busy && itemId.isNotBlank() && lotCode.isNotBlank() && quantity.isNotBlank() &&
                        runCatching { LocalDate.parse(expiry) }.isSuccess,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Receive lot") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun InventoryFefoIssueScreen(
    itemOptions: List<FarmSelectorOption>,
    busy: Boolean,
    error: String?,
    onIssue: (String, String) -> Unit,
    onBack: () -> Unit,
) {
    var itemId by rememberSaveable { mutableStateOf("") }
    var quantity by rememberSaveable { mutableStateOf("") }
    FarmOperationalPage("FOS-INV-008", "FEFO issue", "Issue from the oldest-expiry dated lot first.", FarmVisualClass.I4, onBack) {
        FarmOperationalSection("Issue dated stock", "Non-lot stock is not substituted for this operation.") {
            InventoryItemSelector(itemOptions, itemId, busy) { itemId = it }
            OutlinedTextField(quantity, {
                quantity = it
            }, label = { Text("Quantity") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            Button(onClick = {
                onIssue(itemId, quantity)
            }, enabled = !busy && itemId.isNotBlank() && quantity.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Issue oldest lot",
                )
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun InventoryReorderRuleScreen(
    itemOptions: List<FarmSelectorOption>,
    busy: Boolean,
    error: String?,
    onSet: (String, String) -> Unit,
    onBack: () -> Unit,
) {
    var itemId by rememberSaveable { mutableStateOf("") }
    var quantity by rememberSaveable { mutableStateOf("") }
    FarmOperationalPage("FOS-INV-012", "Reorder rules", "Set the on-hand point that should trigger attention.", onBack = onBack) {
        FarmOperationalSection("Reorder point") {
            InventoryItemSelector(itemOptions, itemId, busy) { itemId = it }
            OutlinedTextField(quantity, {
                quantity = it
            }, label = { Text("Reorder quantity") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            Button(onClick = {
                onSet(itemId, quantity)
            }, enabled = !busy && itemId.isNotBlank() && quantity.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Set reorder point",
                )
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun InventoryReorderAlertScreen(
    itemOptions: List<FarmSelectorOption>,
    busy: Boolean,
    error: String?,
    onRecord: (String, String) -> Unit,
    onBack: () -> Unit,
) {
    var itemId by rememberSaveable { mutableStateOf("") }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    FarmOperationalPage("FOS-INV-013", "Reorder alert", "Record that stock reached its reorder point.", FarmVisualClass.I4, onBack) {
        FarmOperationalSection("Alert record", "This is an auditable record, not a forecast engine.") {
            InventoryItemSelector(itemOptions, itemId, busy) { itemId = it }
            OutlinedTextField(
                day,
                { day = it },
                label = { Text("Alert date") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                singleLine = true,
            )
            Button(
                onClick = { onRecord(itemId, day) },
                enabled =
                    !busy && itemId.isNotBlank() && runCatching { LocalDate.parse(day) }.isSuccess,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Record alert") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

/** FOS-ATOM-009 over this farm's inventory items, so a stock command never carries a mistyped id. */
@Composable
private fun InventoryItemSelector(itemOptions: List<FarmSelectorOption>, selected: String, busy: Boolean, onSelect: (String) -> Unit) {
    FarmEntitySelector(
        atomTag = FarmSelectionAtoms.INVENTORY_ITEM_SELECTOR,
        title = "Item",
        options = itemOptions,
        selectedId = selected.ifBlank { null },
        onSelect = onSelect,
        emptyText = "Create an inventory item first.",
        enabled = !busy,
    )
}
