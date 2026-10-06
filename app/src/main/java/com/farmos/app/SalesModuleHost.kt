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
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalRows
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.design.FarmSearchSelector
import com.farmos.core.design.FarmSelectionAtoms
import com.farmos.core.design.FarmSelectorOption
import com.farmos.core.design.FarmSelectorSearch
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.CustomerCommands
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.ops.FarmCurrency
import com.farmos.domain.ops.RecordCustomerSale
import com.farmos.domain.ops.RecordSale
import com.farmos.feature.ops.SaleCurrencyTotalView
import com.farmos.feature.ops.SaleView
import com.farmos.feature.ops.SalesRecordNavigator
import com.farmos.feature.ops.SalesRecords
import com.farmos.feature.ops.SimpleCaptureScreen
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.launch

private enum class SalesModulePage { HOME, ORDERS, CREATE, PRODUCE, RESERVATION, REPORT }

/**
 * Dedicated sales-record orchestration preserving the existing unit-economics command path.
 *
 * FOS-SALES-004 — sales orders: the recorded sales register with sale detail.
 * FOS-SALES-006 — create sale: the governed sale capture form (root tag FOS-SALES-001).
 * FOS-SALES-008 — produce sale: produce-kind sale capture over the same command path.
 * FOS-SALES-009 — rabbit reservation sale: reservation settlement capture over the same command path.
 * FOS-SALES-012 — sales report: read-only totals per currency from local records.
 */
@Composable
fun SalesModuleHost(
    farmId: String,
    ops: RoomOpsRepository,
    newContext: () -> LocalCommandContext,
    enqueueSync: () -> Unit,
    onBack: () -> Unit,
    loadRecords: suspend () -> SalesRecords = { SalesRecords() },
    loadCurrency: suspend () -> String = { FarmCurrency.DEFAULT_CODE },
    /** The customer register (FOS-SALES-002/003), opened from the sales home. */
    customers: (@Composable (onBack: () -> Unit) -> Unit)? = null,
    /** Whole-farm customer search for the sale's customer (D-004); null hides the selector. */
    customerSearch: FarmSelectorSearch? = null,
    customerCommands: CustomerCommands? = null,
    /** Animal sale (FOS-SALES-007), opened from the sales home. */
    animalSale: (@Composable (onBack: () -> Unit) -> Unit)? = null,
    /** Agreed rabbit contracts (FOS-SALES-009); null hides the contract picker, manual capture stays. */
    loadRabbitContracts: (suspend () -> List<RabbitContractView>)? = null,
) {
    val scope = rememberCoroutineScope()
    val currency by rememberFarmCurrency(farmId, loadCurrency)
    val busy = remember { mutableStateOf(false) }
    val saved = remember { mutableStateOf(false) }
    val error = remember { mutableStateOf<String?>(null) }
    val rows = remember { mutableStateOf(emptyList<String>()) }
    val records = remember(farmId) { mutableStateOf(SalesRecords()) }
    suspend fun refresh() {
        rows.value = ops.recentSales().map { "${it.itemKind} · ${it.amountMinor} ${it.currency}" }
        records.value = loadRecords()
    }
    LaunchedEffect(farmId) { runCatching { refresh() } }
    fun run(block: suspend () -> Unit) {
        scope.launch { busy.value = true; error.value = null; saved.value = false; runCatching { block(); refresh() }.onSuccess { saved.value = true; enqueueSync() }.onFailure { error.value = it.message }; busy.value = false }
    }
    val page = remember { mutableStateOf(SalesModulePage.HOME) }
    val selectedSale = remember { mutableStateOf<SaleView?>(null) }
    val backHome = { page.value = SalesModulePage.HOME; selectedSale.value = null }

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
    }
}

private val PRODUCE_KINDS = listOf("milk", "eggs", "honey", "wool", "meat", "manure", "live_bird", "other")

private fun money(minor: Long, currency: String): String =
    BigDecimal.valueOf(minor, FarmCurrency.minorDigits(currency)).stripTrailingZeros().toPlainString() + " " + currency

/** FOS-SALES-004 — sales orders: the recorded sales register with sale detail. */
@Composable
private fun SalesOrderListScreen(
    records: SalesRecords,
    onSelect: (SaleView?) -> Unit,
    selected: SaleView?,
    onBack: () -> Unit,
) {
    FarmOperationalPage(screenId = "FOS-SALES-004", title = "Sales orders", subtitle = "Recorded sales on this device, most recent first.", onBack = onBack) {
        if (selected != null) {
            FarmOperationalSection("Sale detail") {
                Text("Item: ${selected.itemKind}")
                Text("Quantity: ${selected.quantity}")
                Text("Amount: ${money(selected.amountMinor, selected.currency)}")
                Text("Date: ${LocalDate.ofEpochDay(selected.epochDay)}")
                TextButton(onClick = { onSelect(null) }) { Text("Close detail") }
            }
        }
        FarmOperationalSection("Orders") {
            if (records.sales.isEmpty()) {
                Text("No sales recorded on this device.")
            } else {
                records.sales.forEach { sale ->
                    TextButton(
                        onClick = { onSelect(sale) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("${sale.itemKind} · ${sale.quantity} · ${money(sale.amountMinor, sale.currency)}", modifier = Modifier.fillMaxWidth()) }
                }
            }
        }
    }
}

/** FOS-SALES-006 — create sale; FOS-SALES-008 — produce sale. One governed form over RecordSale. */
@Composable
private fun SaleCaptureScreen(
    title: String,
    screenId: String,
    help: String,
    currency: String?,
    customerSearch: FarmSelectorSearch?,
    customerCommands: CustomerCommands?,
    ops: RoomOpsRepository,
    newContext: () -> LocalCommandContext,
    busy: MutableState<Boolean>,
    error: MutableState<String?>,
    saved: MutableState<Boolean>,
    run: (suspend () -> Unit) -> Unit,
    /** Fixed item-kind options; null keeps a free item-kind field. */
    itemKinds: List<String>?,
    onBack: () -> Unit,
) {
    val kind = remember { mutableStateOf(itemKinds?.firstOrNull() ?: "live_goat") }
    val qty = remember { mutableStateOf("1") }
    val amount = remember { mutableStateOf("") }
    val day = remember { mutableStateOf(LocalDate.now().toString()) }
    val customer = remember { mutableStateOf(NO_CUSTOMER) }
    FarmOperationalPage(screenId = screenId, title = title, subtitle = help, onBack = onBack) {
        if (saved.value) {
            FarmOperationalSection("Recorded") { Text("The sale committed locally.") }
        }
        error.value?.let { FarmOperationalSection("Needs attention") { Text(it) } }
        FarmOperationalSection("Sale") {
            if (itemKinds != null) {
                Text("Produce kind", style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                itemKinds.chunked(3).forEach { row ->
                    androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxWidth()) {
                        row.forEach { option ->
                            TextButton(
                                onClick = { kind.value = option },
                                enabled = !busy.value,
                                modifier = Modifier.weight(1f),
                            ) { Text(if (kind.value == option) "● $option" else option) }
                        }
                    }
                }
            } else {
                OutlinedTextField(kind.value, { kind.value = it }, label = { Text("Item kind") }, modifier = Modifier.fillMaxWidth(), enabled = !busy.value)
            }
            OutlinedTextField(qty.value, { qty.value = it }, label = { Text("Quantity") }, modifier = Modifier.fillMaxWidth(), enabled = !busy.value)
            OutlinedTextField(amount.value, { amount.value = it }, label = { Text("Amount (${currency ?: "…"})") }, modifier = Modifier.fillMaxWidth(), enabled = !busy.value)
            OutlinedTextField(day.value, { day.value = it }, label = { Text("Date") }, placeholder = { Text("YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth(), enabled = !busy.value)
        }
        if (customerSearch != null) {
            FarmOperationalSection("Customer (optional)") {
                FarmSearchSelector(FarmSelectionAtoms.CUSTOMER_SELECTOR, "Customer", customerSearch, customer.value, { customer.value = it }, "No customers match on this device", enabled = !busy.value, pinned = listOf(NO_CUSTOMER))
            }
        }
        Button(
            onClick = {
                run {
                    val code = checkNotNull(currency) { "The farm currency is still loading" }
                    val amountMinor = amount.value.toScaledLongExact(FarmCurrency.minorDigits(code), "Amount")
                    val quantityMilli = qty.value.toScaledLongExact(3, "Quantity")
                    val epochDay = day.value.ifBlank { LocalDate.now().toString() }.let { LocalDate.parse(it).toEpochDay() }
                    val chosen = customer.value.takeUnless { it.id == NO_CUSTOMER.id }
                    if (chosen != null && customerCommands != null) {
                        customerCommands.recordSale(
                            RecordCustomerSale(UUID.randomUUID().toString(), chosen.id, chosen.label, kind.value.trim(), quantityMilli, amountMinor, code, epochDay),
                            newContext(),
                        )
                    } else {
                        ops.recordSale(
                            RecordSale(UUID.randomUUID().toString(), kind.value.trim(), quantityMilli, amountMinor, code, epochDay),
                            newContext(),
                        )
                    }
                }
            },
            enabled = !busy.value,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (busy.value) "Recording…" else "Record sale") }
    }
}

/** FOS-SALES-009 — rabbit reservation sale: settle an agreed rabbit reservation as a recorded sale. */
@Composable
private fun RabbitReservationSaleScreen(
    currency: String?,
    loadRabbitContracts: (suspend () -> List<RabbitContractView>)?,
    ops: RoomOpsRepository,
    newContext: () -> LocalCommandContext,
    busy: MutableState<Boolean>,
    error: MutableState<String?>,
    saved: MutableState<Boolean>,
    run: (suspend () -> Unit) -> Unit,
    onBack: () -> Unit,
) {
    val contracts = remember { mutableStateOf(emptyList<RabbitContractView>()) }
    val picked = remember { mutableStateOf<RabbitContractView?>(null) }
    val buyer = remember { mutableStateOf("") }
    val amount = remember { mutableStateOf("") }
    val day = remember { mutableStateOf(LocalDate.now().toString()) }
    LaunchedEffect(loadRabbitContracts) {
        if (loadRabbitContracts != null) {
            runCatching { contracts.value = loadRabbitContracts() }
        }
    }
    FarmOperationalPage(
        screenId = "FOS-SALES-009",
        title = "Rabbit reservation sale",
        subtitle = "Settle an agreed rabbit reservation as a recorded sale. The sale posts income exactly like any other sale.",
        onBack = onBack,
    ) {
        if (saved.value) {
            FarmOperationalSection("Recorded") { Text("The reservation sale committed locally.") }
        }
        error.value?.let { FarmOperationalSection("Needs attention") { Text(it) } }
        if (contracts.value.isNotEmpty()) {
            FarmOperationalSection("Agreed reservations") {
                contracts.value.forEach { contract ->
                    TextButton(
                        onClick = {
                            picked.value = contract
                            buyer.value = contract.buyerName
                            amount.value = BigDecimal.valueOf(contract.amountMinor, FarmCurrency.minorDigits(contract.currency)).stripTrailingZeros().toPlainString()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("${contract.buyerName} · ${money(contract.amountMinor, contract.currency)} · ${contract.status}", modifier = Modifier.fillMaxWidth()) }
                }
            }
        }
        FarmOperationalSection("Reservation sale") {
            OutlinedTextField(buyer.value, { buyer.value = it }, label = { Text("Buyer name") }, modifier = Modifier.fillMaxWidth(), enabled = !busy.value)
            OutlinedTextField(amount.value, { amount.value = it }, label = { Text("Amount (${currency ?: "…"})") }, modifier = Modifier.fillMaxWidth(), enabled = !busy.value)
            OutlinedTextField(day.value, { day.value = it }, label = { Text("Date") }, placeholder = { Text("YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth(), enabled = !busy.value)
            picked.value?.let { Text("Settling reservation for ${it.buyerName}.") }
        }
        Button(
            onClick = {
                run {
                    val code = checkNotNull(currency) { "The farm currency is still loading" }
                    require(buyer.value.isNotBlank()) { "Buyer name is required" }
                    val amountMinor = amount.value.toScaledLongExact(FarmCurrency.minorDigits(code), "Amount")
                    val epochDay = day.value.ifBlank { LocalDate.now().toString() }.let { LocalDate.parse(it).toEpochDay() }
                    ops.recordSale(
                        RecordSale(UUID.randomUUID().toString(), "rabbit_reservation", 1_000, amountMinor, code, epochDay),
                        newContext(),
                    )
                }
            },
            enabled = !busy.value,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (busy.value) "Recording…" else "Record reservation sale") }
    }
}

/** FOS-SALES-012 — sales report: read-only totals per currency from local records. */
@Composable
private fun SalesReportScreen(
    records: SalesRecords,
    onBack: () -> Unit,
) {
    FarmOperationalPage(
        screenId = "FOS-SALES-012",
        title = "Sales report",
        subtitle = "Totals are exhaustive per currency from this device's records. Currencies are never added together.",
        onBack = onBack,
    ) {
        FarmOperationalSection("Totals") {
            records.totals.forEach { total: SaleCurrencyTotalView ->
                Text("${total.currency}: ${money(total.amountMinor, total.currency)} across ${total.saleCount} sales")
            }
            if (records.totals.isEmpty()) {
                Text("No sales recorded on this device.")
            }
        }
        FarmOperationalRows(
            rows = records.sales.map { "${LocalDate.ofEpochDay(it.epochDay)} · ${it.itemKind} · ${money(it.amountMinor, it.currency)}" },
            emptyTitle = "No sales",
            emptyHint = "Record a sale to see it here.",
        )
    }
}

private val NO_CUSTOMER = FarmSelectorOption("no-customer", "No customer recorded")
