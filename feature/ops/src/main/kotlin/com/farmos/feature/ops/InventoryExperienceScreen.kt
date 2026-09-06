package com.farmos.feature.ops

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalRows
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.design.FarmVisualClass
import java.time.LocalDate

private enum class InventoryPage {
    DASHBOARD,
    ITEMS,
    CREATE,
    RECEIVE,
    ISSUE,
    RECEIVE_LOT,
    FEFO_ISSUE,
    REORDER_RULE,
    REORDER_ALERT,
}

/** FOS-INV-001/002/004/005/006/007/008/012/013 — inventory operating reference family. */
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
) {
    var page by remember { mutableStateOf(InventoryPage.DASHBOARD) }
    val home = { page = InventoryPage.DASHBOARD }
    when (page) {
        InventoryPage.DASHBOARD -> InventoryDashboard(rows, error, { page = it }, onBack)
        InventoryPage.ITEMS -> InventoryRows(rows, error, home)
        InventoryPage.CREATE -> CreateInventoryItemScreen(busy, error, onCreate, home)
        InventoryPage.RECEIVE -> InventoryMoveScreen("receive", busy, error, onMove, home)
        InventoryPage.ISSUE -> InventoryMoveScreen("issue", busy, error, onMove, home)
        InventoryPage.RECEIVE_LOT -> InventoryLotReceiveScreen(busy, error, onReceiveLot, home)
        InventoryPage.FEFO_ISSUE -> InventoryFefoIssueScreen(busy, error, onIssueLot, home)
        InventoryPage.REORDER_RULE -> InventoryReorderRuleScreen(busy, error, onSetReorder, home)
        InventoryPage.REORDER_ALERT -> InventoryReorderAlertScreen(busy, error, onRecordReorder, home)
    }
}

@Composable
private fun InventoryDashboard(
    rows: List<String>,
    error: String?,
    onOpen: (InventoryPage) -> Unit,
    onBack: () -> Unit,
) {
    FarmOperationalPage("FOS-INV-001", "Inventory", "On-hand lots, dates and reorder points.", FarmVisualClass.I2, onBack) {
        FarmOperationalSection("Stock overview") {
            Text("${rows.size} inventory item(s)", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            TextButton(onClick = { onOpen(InventoryPage.ITEMS) }) { Text("Open inventory list") }
        }
        FarmOperationalSection("Stock movement") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Button(onClick = { onOpen(InventoryPage.RECEIVE) }) { Text("Receive") }
                Button(onClick = { onOpen(InventoryPage.ISSUE) }) { Text("Issue") }
            }
            TextButton(onClick = { onOpen(InventoryPage.CREATE) }) { Text("Create inventory item") }
        }
        FarmOperationalSection("Dated lots and FEFO") {
            TextButton(onClick = { onOpen(InventoryPage.RECEIVE_LOT) }) { Text("Receive dated lot") }
            TextButton(onClick = { onOpen(InventoryPage.FEFO_ISSUE) }) { Text("Issue oldest-expiry lot") }
        }
        FarmOperationalSection("Reorder") {
            TextButton(onClick = { onOpen(InventoryPage.REORDER_RULE) }) { Text("Set reorder point") }
            TextButton(onClick = { onOpen(InventoryPage.REORDER_ALERT) }) { Text("Record reorder alert") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
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
    var sku by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("kg") }
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
    busy: Boolean,
    error: String?,
    onMove: (String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var itemId by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    val receive = kind == "receive"
    FarmOperationalPage(
        if (receive) "FOS-INV-005" else "FOS-INV-007",
        if (receive) "Receive stock" else "Issue stock",
        if (receive) "Add ordinary on-hand quantity." else "Deduct ordinary on-hand quantity.",
        onBack = onBack,
    ) {
        FarmOperationalSection("Stock movement") {
            OutlinedTextField(itemId, {
                itemId = it
            }, label = { Text("Item id") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
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
    busy: Boolean,
    error: String?,
    onReceive: (String, String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var itemId by remember { mutableStateOf("") }
    var lotCode by remember { mutableStateOf("") }
    var expiry by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    FarmOperationalPage("FOS-INV-006", "Receive dated lot", "Capture lot identity, expiry and quantity.", onBack = onBack) {
        FarmOperationalSection("Lot details") {
            OutlinedTextField(itemId, {
                itemId = it
            }, label = { Text("Item id") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
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
    busy: Boolean,
    error: String?,
    onIssue: (String, String) -> Unit,
    onBack: () -> Unit,
) {
    var itemId by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    FarmOperationalPage("FOS-INV-008", "FEFO issue", "Issue from the oldest-expiry dated lot first.", FarmVisualClass.I4, onBack) {
        FarmOperationalSection("Issue dated stock", "Non-lot stock is not substituted for this operation.") {
            OutlinedTextField(itemId, {
                itemId = it
            }, label = { Text("Item id") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
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
    busy: Boolean,
    error: String?,
    onSet: (String, String) -> Unit,
    onBack: () -> Unit,
) {
    var itemId by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    FarmOperationalPage("FOS-INV-012", "Reorder rules", "Set the on-hand point that should trigger attention.", onBack = onBack) {
        FarmOperationalSection("Reorder point") {
            OutlinedTextField(itemId, {
                itemId = it
            }, label = { Text("Item id") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
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
    busy: Boolean,
    error: String?,
    onRecord: (String, String) -> Unit,
    onBack: () -> Unit,
) {
    var itemId by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    FarmOperationalPage("FOS-INV-013", "Reorder alert", "Record that stock reached its reorder point.", FarmVisualClass.I4, onBack) {
        FarmOperationalSection("Alert record", "This is an auditable record, not a forecast engine.") {
            OutlinedTextField(itemId, {
                itemId = it
            }, label = { Text("Item id") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
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
