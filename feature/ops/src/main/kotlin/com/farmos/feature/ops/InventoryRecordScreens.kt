package com.farmos.feature.ops

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farmos.core.design.AnimalFarmEmptyState
import com.farmos.core.design.AnimalFarmTheme
import com.farmos.core.design.AnimalFarmWarningSurface
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalSection
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class InventoryItemView(
    val id: String,
    val sku: String,
    val name: String,
    val unit: String,
    val quantityMilli: Long,
    /** Zero means no reorder point has been set. */
    val reorderMilli: Long,
)

data class InventoryLotView(
    val id: String,
    val itemId: String,
    val itemLabel: String,
    val lotCode: String,
    val expiresEpochDay: Long,
    val quantityMilli: Long,
    val unit: String,
)

data class InventoryMovementView(
    val id: String,
    val itemId: String,
    val itemLabel: String,
    val direction: String,
    val quantityMilli: Long,
    val unit: String,
    val occurredAtEpochMillis: Long,
)

/** Local, farm-scoped inventory records for the read-only record pages. */
data class InventoryReadModel(
    val items: List<InventoryItemView> = emptyList(),
    val openLots: List<InventoryLotView> = emptyList(),
    val movements: List<InventoryMovementView> = emptyList(),
    val movementLimit: Int = 0,
)

internal object InventoryRecords {
    /** Same rule the farm home uses: a reorder point is set and on-hand is at or below it. */
    fun isLow(item: InventoryItemView): Boolean = item.reorderMilli > 0 && item.quantityMilli <= item.reorderMilli

    fun lowStock(model: InventoryReadModel): List<InventoryItemView> =
        model.items.filter(::isLow).sortedWith(compareBy<InventoryItemView> { it.sku }.thenBy { it.id })

    fun search(model: InventoryReadModel, query: String): List<InventoryItemView> {
        val needle = query.trim()
        if (needle.isEmpty()) return emptyList()
        return model.items
            .filter { it.sku.contains(needle, ignoreCase = true) || it.name.contains(needle, ignoreCase = true) }
            .sortedWith(compareBy<InventoryItemView> { !it.sku.equals(needle, ignoreCase = true) }.thenBy { it.sku })
    }

    /** Exact decimal rendering of a milli-unit quantity; never floating point. */
    fun quantity(milli: Long, unit: String): String =
        BigDecimal.valueOf(milli, 3).stripTrailingZeros().toPlainString() + (if (unit.isBlank()) "" else " $unit")

    fun directionLabel(direction: String): String = when (direction) {
        "receive" -> "Received"
        "issue" -> "Issued"
        else -> direction
    }

    private val movementTime: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun movementTime(epochMillis: Long, zone: ZoneId): String = movementTime.format(Instant.ofEpochMilli(epochMillis).atZone(zone))
}

@Composable
private fun InventoryRecordRow(
    primary: String,
    secondary: String,
    tag: String,
    onClick: (() -> Unit)? = null,
    detail: String? = null,
) {
    val base = Modifier.fillMaxWidth().heightIn(min = AnimalFarmTheme.minimumTouchDp.dp)
    Column(
        (if (onClick != null) base.clickable(role = Role.Button, onClick = onClick) else base)
            .padding(vertical = 6.dp)
            .testTag(tag),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(primary, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        Text(secondary)
        detail?.let { Text(it, color = AnimalFarmTheme.colors.mutedInk) }
    }
}

/** FOS-INV-003 — one stock item with its open lots and recorded movements. */
@Composable
internal fun InventoryItemDetailScreen(
    model: InventoryReadModel,
    itemId: String?,
    today: LocalDate,
    zone: ZoneId,
    onOpenLot: (String) -> Unit,
    onBack: () -> Unit,
) {
    val item = model.items.firstOrNull { it.id == itemId }
    FarmOperationalPage("FOS-INV-003", item?.name ?: "Inventory item", item?.sku ?: "Item record on this device", onBack = onBack) {
        if (item == null) {
            AnimalFarmEmptyState("This item is not on this device.")
            return@FarmOperationalPage
        }
        if (InventoryRecords.isLow(item)) {
            AnimalFarmWarningSurface { Text("At or below its reorder point", fontWeight = FontWeight.Bold) }
        }
        FarmOperationalSection("Stock") {
            InventoryRecordRow("On hand", InventoryRecords.quantity(item.quantityMilli, item.unit), "inventory-item-on-hand")
            InventoryRecordRow(
                "Reorder point",
                if (item.reorderMilli > 0) InventoryRecords.quantity(item.reorderMilli, item.unit) else "Not set",
                "inventory-item-reorder",
            )
        }
        val lots = model.openLots.filter { it.itemId == item.id }
        FarmOperationalSection("Open lots") {
            if (lots.isEmpty()) Text("No open dated lots.", color = AnimalFarmTheme.colors.mutedInk)
            lots.forEachIndexed { index, lot ->
                if (index > 0) HorizontalDivider()
                InventoryRecordRow(
                    lot.lotCode,
                    InventoryRecords.quantity(lot.quantityMilli, lot.unit),
                    "inventory-item-lot:${lot.id}",
                    onClick = { onOpenLot(lot.id) },
                    detail = expiryLabel(lot, today),
                )
            }
        }
        val movements = model.movements.filter { it.itemId == item.id }
        FarmOperationalSection("Recorded movements") {
            if (movements.isEmpty()) Text("No recorded movements.", color = AnimalFarmTheme.colors.mutedInk)
            movements.forEach { movement ->
                InventoryRecordRow(
                    InventoryRecords.directionLabel(movement.direction),
                    InventoryRecords.quantity(movement.quantityMilli, movement.unit),
                    "inventory-item-movement:${movement.id}",
                    detail = InventoryRecords.movementTime(movement.occurredAtEpochMillis, zone),
                )
            }
        }
    }
}

private fun expiryLabel(lot: InventoryLotView, today: LocalDate): String {
    val expires = LocalDate.ofEpochDay(lot.expiresEpochDay)
    return if (lot.expiresEpochDay < today.toEpochDay()) "Expired $expires" else "Expires $expires"
}

/** FOS-INV-009 — one dated lot. */
@Composable
internal fun InventoryLotDetailScreen(model: InventoryReadModel, lotId: String?, today: LocalDate, onBack: () -> Unit) {
    val lot = model.openLots.firstOrNull { it.id == lotId }
    FarmOperationalPage("FOS-INV-009", "Lot ${lot?.lotCode.orEmpty()}".trim(), lot?.itemLabel ?: "Lot record on this device", onBack = onBack) {
        if (lot == null) {
            AnimalFarmEmptyState("This lot has no remaining quantity on this device.")
            return@FarmOperationalPage
        }
        if (lot.expiresEpochDay < today.toEpochDay()) {
            AnimalFarmWarningSurface { Text("Expired ${LocalDate.ofEpochDay(lot.expiresEpochDay)}", fontWeight = FontWeight.Bold) }
        }
        FarmOperationalSection("Lot") {
            InventoryRecordRow("Item", lot.itemLabel, "inventory-lot-item")
            InventoryRecordRow("Remaining", InventoryRecords.quantity(lot.quantityMilli, lot.unit), "inventory-lot-remaining")
            InventoryRecordRow("Expiry", LocalDate.ofEpochDay(lot.expiresEpochDay).toString(), "inventory-lot-expiry")
            Text("Issues draw from the earliest-expiring open lot first.", color = AnimalFarmTheme.colors.mutedInk)
        }
    }
}

/** FOS-INV-010 — open lots in expiry order. */
@Composable
internal fun InventoryExpiryQueueScreen(model: InventoryReadModel, today: LocalDate, onOpenLot: (String) -> Unit, onBack: () -> Unit) {
    FarmOperationalPage("FOS-INV-010", "Expiry queue", "Open dated lots, earliest expiry first.", onBack = onBack) {
        if (model.openLots.isEmpty()) {
            AnimalFarmEmptyState("No open dated lots on this device.")
            return@FarmOperationalPage
        }
        FarmOperationalSection("Open lots · ${model.openLots.size}") {
            model.openLots.forEachIndexed { index, lot ->
                if (index > 0) HorizontalDivider()
                InventoryRecordRow(
                    "${lot.lotCode} · ${lot.itemLabel}",
                    expiryLabel(lot, today),
                    "inventory-expiry-lot:${lot.id}",
                    onClick = { onOpenLot(lot.id) },
                    detail = InventoryRecords.quantity(lot.quantityMilli, lot.unit),
                )
            }
        }
    }
}

/** FOS-INV-011 — items at or below their reorder point. */
@Composable
internal fun InventoryLowStockScreen(model: InventoryReadModel, onOpenItem: (String) -> Unit, onBack: () -> Unit) {
    FarmOperationalPage("FOS-INV-011", "Low stock", "Items at or below their reorder point.", onBack = onBack) {
        val low = InventoryRecords.lowStock(model)
        val withoutRule = model.items.count { it.reorderMilli <= 0 }
        if (low.isEmpty()) {
            AnimalFarmEmptyState("No items at or below their reorder point.")
        } else {
            FarmOperationalSection("Below reorder point · ${low.size}") {
                low.forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider()
                    InventoryRecordRow(
                        "${item.sku} · ${item.name}",
                        "${InventoryRecords.quantity(item.quantityMilli, item.unit)} on hand",
                        "inventory-low-item:${item.id}",
                        onClick = { onOpenItem(item.id) },
                        detail = "Reorder point ${InventoryRecords.quantity(item.reorderMilli, item.unit)}",
                    )
                }
            }
        }
        if (withoutRule > 0) Text("$withoutRule item(s) have no reorder point.", color = AnimalFarmTheme.colors.mutedInk)
    }
}

/** FOS-INV-014 — recorded stock movements, newest first. */
@Composable
internal fun InventoryMovementHistoryScreen(model: InventoryReadModel, zone: ZoneId, onOpenItem: (String) -> Unit, onBack: () -> Unit) {
    FarmOperationalPage("FOS-INV-014", "Movement history", "Recorded receive and issue movements. Dated lot receipts are listed in the expiry queue.", onBack = onBack) {
        if (model.movements.isEmpty()) {
            AnimalFarmEmptyState("No recorded stock movements on this device.")
            return@FarmOperationalPage
        }
        if (model.movementLimit > 0 && model.movements.size >= model.movementLimit) {
            Text("Showing the latest ${model.movementLimit} movements.", color = AnimalFarmTheme.colors.mutedInk)
        }
        FarmOperationalSection("Movements") {
            model.movements.forEachIndexed { index, movement ->
                if (index > 0) HorizontalDivider()
                InventoryRecordRow(
                    "${InventoryRecords.directionLabel(movement.direction)} · ${movement.itemLabel}",
                    InventoryRecords.quantity(movement.quantityMilli, movement.unit),
                    "inventory-movement:${movement.id}",
                    onClick = { onOpenItem(movement.itemId) },
                    detail = InventoryRecords.movementTime(movement.occurredAtEpochMillis, zone),
                )
            }
        }
    }
}

/** FOS-INV-017 — local search over stock items by SKU or name. */
@Composable
internal fun InventorySearchScreen(model: InventoryReadModel, onOpenItem: (String) -> Unit, onBack: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    FarmOperationalPage("FOS-INV-017", "Search inventory", "Searches stock items on this device.", onBack = onBack) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("SKU or name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("inventory-search-field"),
        )
        val results = InventoryRecords.search(model, query)
        when {
            query.isBlank() -> Text("Enter a SKU or item name.", color = AnimalFarmTheme.colors.mutedInk)
            results.isEmpty() -> AnimalFarmEmptyState("No matching inventory items.")
            else -> FarmOperationalSection("${results.size} match(es)") {
                results.forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider()
                    InventoryRecordRow(
                        "${item.sku} · ${item.name}",
                        "${InventoryRecords.quantity(item.quantityMilli, item.unit)} on hand",
                        "inventory-search-item:${item.id}",
                        onClick = { onOpenItem(item.id) },
                    )
                }
            }
        }
    }
}
