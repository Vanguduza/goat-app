package com.farmos.app

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.farmos.feature.ops.SalesRecords
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import com.farmos.core.design.runSuspendCatching

internal enum class SalesModulePage { HOME, ORDERS, CREATE, PRODUCE, RESERVATION, REPORT, DELIVERY }

/**
 * Dedicated sales-record orchestration preserving the existing unit-economics command path.
 *
 * FOS-SALES-004 — sales orders: the recorded sales register with sale detail.
 * FOS-SALES-006 — create sale: the governed sale capture form (root tag FOS-SALES-001).
 * FOS-SALES-008 — produce sale: produce-kind sale capture over the same command path.
 * FOS-SALES-009 — rabbit reservation sale: reservation settlement capture over the same command path.
 * FOS-SALES-012 — sales report: read-only totals per currency from local records.
 */



internal val PRODUCE_KINDS = listOf("milk", "eggs", "honey", "wool", "meat", "manure", "live_bird", "other")

internal fun salesMoney(minor: Long, currency: String): String =
    BigDecimal.valueOf(minor, FarmCurrency.minorDigits(currency)).stripTrailingZeros().toPlainString() + " " + currency

/** FOS-SALES-004 — sales orders: the recorded sales register with sale detail. */
@Composable
internal fun SalesOrderListScreen(
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
                Text("Amount: ${salesMoney(selected.amountMinor, selected.currency)}")
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
                    ) { Text("${sale.itemKind} · ${sale.quantity} · ${salesMoney(sale.amountMinor, sale.currency)}", modifier = Modifier.fillMaxWidth()) }
                }
            }
        }
    }
}

/** FOS-SALES-006 — create sale; FOS-SALES-008 — produce sale. One governed form over RecordSale. */
@Composable
internal fun SaleCaptureScreen(
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
internal fun RabbitReservationSaleScreen(
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
            runSuspendCatching { contracts.value = loadRabbitContracts() }
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
                    ) { Text("${contract.buyerName} · ${salesMoney(contract.amountMinor, contract.currency)} · ${contract.status}", modifier = Modifier.fillMaxWidth()) }
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
internal fun SalesReportScreen(
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
                Text("${total.currency}: ${salesMoney(total.amountMinor, total.currency)} across ${total.saleCount} sales")
            }
            if (records.totals.isEmpty()) {
                Text("No sales recorded on this device.")
            }
        }
        FarmOperationalRows(
            rows = records.sales.map { "${LocalDate.ofEpochDay(it.epochDay)} · ${it.itemKind} · ${salesMoney(it.amountMinor, it.currency)}" },
            emptyTitle = "No sales",
            emptyHint = "Record a sale to see it here.",
        )
    }
}

internal val NO_CUSTOMER = FarmSelectorOption("no-customer", "No customer recorded")

/**
 * FOS-SALES-010 — Delivery or Collection.
 *
 * GENUINE GAP — not implemented: there is no sale-delivery entity, DAO, or governed command
 * (RecordSaleDelivery) in the local schema, and delivery status must not be bolted onto the
 * sale record as an ungoverned field.
 * Required domain piece: a farm-scoped sale delivery/collection record with a governed command,
 * validator, journal operation and migration. This screen fails closed.
 */
@Composable
internal fun SaleDeliveryScreen(onBack: () -> Unit) {
    FarmOperationalPage(
        screenId = "FOS-SALES-010",
        title = "Delivery or collection",
        subtitle = "Not available in this build.",
        onBack = onBack,
        backLabel = "Sales",
    ) {
        FarmOperationalSection("Unavailable") {
            Text(
                "Delivery and collection status is not recorded in this build: there is no delivery " +
                    "table or governed delivery command in the local schema. No status is shown rather than a fabricated one.",
            )
            Text("Required: a farm-scoped sale delivery record with a governed command, validator, journal operation and migration.")
        }
    }
}
