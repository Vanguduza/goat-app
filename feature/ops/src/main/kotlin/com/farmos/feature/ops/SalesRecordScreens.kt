package com.farmos.feature.ops

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farmos.core.design.AnimalFarmEmptyState
import com.farmos.core.design.AnimalFarmTheme
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.design.FarmVisualClass
import java.time.LocalDate

/**
 * One recorded sale. [amountMinor] is shown exactly as stored, in integer minor units of
 * [currency]; no per-currency decimal rule is applied. [quantity] is the exact recorded quantity.
 */
data class SaleView(val id: String, val itemKind: String, val quantity: String, val amountMinor: Long, val currency: String, val epochDay: Long)

/** Exhaustive sales total in one currency; currencies are never added together. */
data class SaleCurrencyTotalView(val currency: String, val amountMinor: Long, val saleCount: Int)

/**
 * Farm-scoped sales records. [sales] may be only the latest rows; [saleCount] and [totals] are
 * exhaustive aggregates. Nothing here writes; this is not a statutory ledger.
 */
data class SalesRecords(
    val sales: List<SaleView> = emptyList(),
    val saleCount: Int? = null,
    val totals: List<SaleCurrencyTotalView> = emptyList(),
)

enum class SalesRecordPage(val label: String) {
    HISTORY("Sales history"),
    DETAIL("Sale detail"),
}

private fun saleAmount(minor: Long, currency: String) = "$minor $currency minor units"

@Composable
private fun SalesRow(primary: String, secondary: String, tag: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag(tag), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(primary, color = AnimalFarmTheme.colors.mutedInk)
        Text(secondary, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Sales record navigation. [home] renders the sales home (FOS-SALES-001) and places the supplied
 * record actions; each action opens a read-only record page whose back returns home.
 */
@Composable
fun SalesRecordNavigator(
    records: SalesRecords,
    /** The customer register (FOS-SALES-002/003); null hides the entry. */
    customers: (@Composable (onBack: () -> Unit) -> Unit)? = null,
    /** Animal sale (FOS-SALES-007): the money for animals sold through a sale exit; null hides the entry. */
    animalSale: (@Composable (onBack: () -> Unit) -> Unit)? = null,
    home: @Composable (recordActions: @Composable () -> Unit) -> Unit,
) {
    var page by rememberSaveable { mutableStateOf<SalesRecordPage?>(null) }
    var register by rememberSaveable { mutableStateOf(false) }
    var animalSaleOpen by rememberSaveable { mutableStateOf(false) }
    val back = { page = null }
    if (register && customers != null) {
        customers { register = false }
        return
    }
    if (animalSaleOpen && animalSale != null) {
        animalSale { animalSaleOpen = false }
        return
    }
    when (page) {
        null -> home {
            FarmOperationalSection("Records") {
                SalesRecordPage.entries.forEach { target ->
                    TextButton(onClick = { page = target }, modifier = Modifier.fillMaxWidth()) { Text("Open ${target.label}") }
                }
                if (customers != null) {
                    TextButton(onClick = { register = true }, modifier = Modifier.fillMaxWidth()) { Text("Customers") }
                }
                if (animalSale != null) {
                    TextButton(onClick = { animalSaleOpen = true }, modifier = Modifier.fillMaxWidth()) { Text("Animal sale") }
                }
            }
        }
        SalesRecordPage.HISTORY -> SalesHistoryScreen(records, back)
        SalesRecordPage.DETAIL -> SaleDetailScreen(records, back)
    }
}

/** FOS-SALES-011 — exhaustive per-currency totals and recorded sales, newest first. */
@Composable
internal fun SalesHistoryScreen(records: SalesRecords, onBack: () -> Unit) {
    FarmOperationalPage("FOS-SALES-011", "Sales history", "Recorded sales on this device, newest first. Not a statutory ledger.", FarmVisualClass.I3, onBack) {
        if (records.sales.isEmpty()) {
            AnimalFarmEmptyState("No sales recorded on this device.")
            return@FarmOperationalPage
        }
        FarmOperationalSection("Totals by currency, all recorded sales") {
            records.totals.forEach {
                SalesRow(it.currency, "${saleAmount(it.amountMinor, it.currency)} · ${it.saleCount} " + if (it.saleCount == 1) "sale" else "sales", "sales-total:${it.currency}")
            }
        }
        FarmOperationalSection(recordListTitle("Sales", records.sales.size, records.saleCount)) {
            records.sales.forEachIndexed { index, sale ->
                if (index > 0) HorizontalDivider()
                SalesRow(
                    "${LocalDate.ofEpochDay(sale.epochDay)} · ${sale.itemKind}",
                    "quantity ${sale.quantity} · ${saleAmount(sale.amountMinor, sale.currency)}",
                    "sale:${sale.id}",
                )
            }
        }
    }
}

/** FOS-SALES-005 — one recorded sale exactly as stored. */
@Composable
internal fun SaleDetailScreen(records: SalesRecords, onBack: () -> Unit) {
    var selectedId by rememberSaveable { mutableStateOf(records.sales.firstOrNull()?.id) }
    FarmOperationalPage("FOS-SALES-005", "Sale detail", "One recorded sale exactly as stored.", FarmVisualClass.I3, onBack) {
        if (records.sales.isEmpty()) {
            AnimalFarmEmptyState("No sales recorded on this device.")
            return@FarmOperationalPage
        }
        FarmOperationalSection(recordListTitle("Sales", records.sales.size, records.saleCount)) {
            records.sales.forEach { sale ->
                val selected = sale.id == selectedId
                TextButton(
                    onClick = { selectedId = sale.id },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = AnimalFarmTheme.minimumTouchDp.dp)
                        .semantics { this.selected = selected }
                        .testTag("sale-option:${sale.id}"),
                ) { Text("${LocalDate.ofEpochDay(sale.epochDay)} · ${sale.itemKind}", fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) }
            }
        }
        val sale = records.sales.firstOrNull { it.id == selectedId } ?: return@FarmOperationalPage
        FarmOperationalSection("Sale") {
            SalesRow("Date", LocalDate.ofEpochDay(sale.epochDay).toString(), "sale-date")
            SalesRow("Item kind", sale.itemKind, "sale-item-kind")
            SalesRow("Quantity", sale.quantity, "sale-quantity")
            SalesRow("Amount", saleAmount(sale.amountMinor, sale.currency), "sale-amount")
        }
    }
}
