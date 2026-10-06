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
 * One recorded purchase. [amountMinor] is shown exactly as stored, in integer minor units of
 * [currency]; no per-currency decimal rule is applied. [quantity] is the exact recorded quantity.
 */
data class ProcurementPurchaseView(
    val id: String,
    val supplierId: String,
    val supplierName: String,
    val itemLabel: String,
    val quantity: String,
    val amountMinor: Long,
    val currency: String,
    val epochDay: Long,
)

/** Exhaustive per-supplier total in one currency; currencies are never added together. */
data class ProcurementCurrencyTotal(val currency: String, val amountMinor: Long, val purchaseCount: Int)

data class ProcurementSupplierView(
    val id: String,
    val name: String,
    val leadTimeDays: Int,
    val totals: List<ProcurementCurrencyTotal>,
    val latestPurchaseEpochDay: Long?,
) {
    val purchaseCount: Int get() = totals.sumOf { it.purchaseCount }
}

/**
 * Farm-scoped procurement records. [purchases] may be only the latest rows; [purchaseCount] and
 * the supplier totals are exhaustive aggregates. Nothing here writes.
 */
data class ProcurementRecords(
    val suppliers: List<ProcurementSupplierView> = emptyList(),
    val purchases: List<ProcurementPurchaseView> = emptyList(),
    val purchaseCount: Int? = null,
)

enum class ProcurementRecordPage(val label: String) {
    SUPPLIERS("Supplier list"),
    SUPPLIER_DETAIL("Supplier detail"),
    PURCHASE_DETAIL("Purchase detail"),
    HISTORY("Procurement history"),
}

private fun amount(minor: Long, currency: String) = "$minor $currency minor units"

private fun ProcurementCurrencyTotal.label() = "${amount(amountMinor, currency)} · $purchaseCount " + if (purchaseCount == 1) "purchase" else "purchases"

@Composable
private fun ProcurementRow(primary: String, secondary: String, tag: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag(tag), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(primary, color = AnimalFarmTheme.colors.mutedInk)
        Text(secondary, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ProcurementSwitcher(title: String, options: List<Pair<String, String>>, selectedId: String?, onSelect: (String) -> Unit) {
    FarmOperationalSection(title) {
        options.forEach { (id, label) ->
            val selected = id == selectedId
            TextButton(
                onClick = { onSelect(id) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = AnimalFarmTheme.minimumTouchDp.dp)
                    .semantics { this.selected = selected }
                    .testTag("procurement-option:$id"),
            ) { Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) }
        }
    }
}

/**
 * Procurement record navigation. [home] renders the procurement home (FOS-PROC-001) and places
 * the supplied record actions; each action opens a read-only record page whose back returns home.
 */
@Composable
fun ProcurementRecordNavigator(records: ProcurementRecords, home: @Composable (recordActions: @Composable () -> Unit) -> Unit) {
    var page by rememberSaveable { mutableStateOf<ProcurementRecordPage?>(null) }
    val back = { page = null }
    when (page) {
        null -> home {
            FarmOperationalSection("Records") {
                ProcurementRecordPage.entries.forEach { target ->
                    TextButton(onClick = { page = target }, modifier = Modifier.fillMaxWidth()) { Text("Open ${target.label}") }
                }
            }
        }
        ProcurementRecordPage.SUPPLIERS -> ProcurementSupplierListScreen(records, back)
        ProcurementRecordPage.SUPPLIER_DETAIL -> ProcurementSupplierDetailScreen(records, back)
        ProcurementRecordPage.PURCHASE_DETAIL -> ProcurementPurchaseDetailScreen(records, back)
        ProcurementRecordPage.HISTORY -> ProcurementHistoryScreen(records, back)
    }
}

/** FOS-PROC-002 — suppliers with exhaustive recorded purchase totals per currency. */
@Composable
internal fun ProcurementSupplierListScreen(records: ProcurementRecords, onBack: () -> Unit) {
    FarmOperationalPage("FOS-PROC-002", "Supplier list", "Suppliers on this device with all recorded purchases totalled per currency.", FarmVisualClass.I3, onBack) {
        if (records.suppliers.isEmpty()) {
            AnimalFarmEmptyState("No suppliers on this device.")
            return@FarmOperationalPage
        }
        FarmOperationalSection("Suppliers · ${records.suppliers.size}") {
            records.suppliers.forEachIndexed { index, supplier ->
                if (index > 0) HorizontalDivider()
                ProcurementRow(
                    "${supplier.name} · lead time ${supplier.leadTimeDays} days",
                    if (supplier.totals.isEmpty()) "No purchases recorded" else supplier.totals.joinToString(" · ") { it.label() },
                    "procurement-supplier:${supplier.id}",
                )
            }
        }
    }
}

/** FOS-PROC-003 — one supplier: recorded lead time, exhaustive totals and its latest purchases. */
@Composable
internal fun ProcurementSupplierDetailScreen(records: ProcurementRecords, onBack: () -> Unit) {
    var selectedId by rememberSaveable { mutableStateOf(records.suppliers.firstOrNull()?.id) }
    FarmOperationalPage("FOS-PROC-003", "Supplier detail", "Recorded lead time, purchase totals and purchases for one supplier.", FarmVisualClass.I3, onBack) {
        if (records.suppliers.isEmpty()) {
            AnimalFarmEmptyState("No suppliers on this device.")
            return@FarmOperationalPage
        }
        ProcurementSwitcher("Suppliers", records.suppliers.map { it.id to it.name }, selectedId) { selectedId = it }
        val supplier = records.suppliers.firstOrNull { it.id == selectedId } ?: return@FarmOperationalPage
        FarmOperationalSection(supplier.name) {
            ProcurementRow("Recorded lead time", "${supplier.leadTimeDays} days", "procurement-supplier-lead")
            ProcurementRow("Purchases recorded", supplier.purchaseCount.toString(), "procurement-supplier-count")
            supplier.latestPurchaseEpochDay?.let { ProcurementRow("Latest purchase", LocalDate.ofEpochDay(it).toString(), "procurement-supplier-latest") }
        }
        FarmOperationalSection("Totals by currency") {
            if (supplier.totals.isEmpty()) Text("No purchases recorded for this supplier.", color = AnimalFarmTheme.colors.mutedInk)
            supplier.totals.forEach { ProcurementRow(it.currency, it.label(), "procurement-supplier-total:${it.currency}") }
        }
        val purchases = records.purchases.filter { it.supplierId == supplier.id }
        FarmOperationalSection(recordListTitle("Purchases", purchases.size, supplier.purchaseCount)) {
            purchases.forEach {
                ProcurementRow("${LocalDate.ofEpochDay(it.epochDay)} · ${it.itemLabel}", "${it.quantity} · ${amount(it.amountMinor, it.currency)}", "procurement-supplier-purchase:${it.id}")
            }
        }
    }
}

/** FOS-PROC-005 — one recorded purchase exactly as stored. */
@Composable
internal fun ProcurementPurchaseDetailScreen(records: ProcurementRecords, onBack: () -> Unit) {
    var selectedId by rememberSaveable { mutableStateOf(records.purchases.firstOrNull()?.id) }
    FarmOperationalPage("FOS-PROC-005", "Purchase detail", "One recorded purchase exactly as stored.", FarmVisualClass.I3, onBack) {
        if (records.purchases.isEmpty()) {
            AnimalFarmEmptyState("No purchases recorded on this device.")
            return@FarmOperationalPage
        }
        ProcurementSwitcher(
            recordListTitle("Purchases", records.purchases.size, records.purchaseCount),
            records.purchases.map { it.id to "${LocalDate.ofEpochDay(it.epochDay)} · ${it.supplierName} · ${it.itemLabel}" },
            selectedId,
        ) { selectedId = it }
        val purchase = records.purchases.firstOrNull { it.id == selectedId } ?: return@FarmOperationalPage
        FarmOperationalSection("Purchase") {
            ProcurementRow("Date", LocalDate.ofEpochDay(purchase.epochDay).toString(), "procurement-purchase-date")
            ProcurementRow("Supplier", purchase.supplierName, "procurement-purchase-supplier")
            ProcurementRow("Inventory item", purchase.itemLabel, "procurement-purchase-item")
            ProcurementRow("Quantity", purchase.quantity, "procurement-purchase-quantity")
            ProcurementRow("Amount", amount(purchase.amountMinor, purchase.currency), "procurement-purchase-amount")
        }
    }
}

/** FOS-PROC-009 — recorded purchases, newest first; labelled when only the latest are listed. */
@Composable
internal fun ProcurementHistoryScreen(records: ProcurementRecords, onBack: () -> Unit) {
    FarmOperationalPage("FOS-PROC-009", "Procurement history", "Recorded purchases on this device, newest first.", FarmVisualClass.I3, onBack) {
        if (records.purchases.isEmpty()) {
            AnimalFarmEmptyState("No purchases recorded on this device.")
            return@FarmOperationalPage
        }
        FarmOperationalSection(recordListTitle("Purchases", records.purchases.size, records.purchaseCount)) {
            records.purchases.forEachIndexed { index, purchase ->
                if (index > 0) HorizontalDivider()
                ProcurementRow(
                    "${LocalDate.ofEpochDay(purchase.epochDay)} · ${purchase.supplierName}",
                    "${purchase.itemLabel} · ${purchase.quantity} · ${amount(purchase.amountMinor, purchase.currency)}",
                    "procurement-purchase:${purchase.id}",
                )
            }
        }
    }
}
