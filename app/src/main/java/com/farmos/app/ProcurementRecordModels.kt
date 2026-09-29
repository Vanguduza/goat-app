package com.farmos.app

import com.farmos.core.database.FarmOsDatabase
import com.farmos.feature.ops.ProcurementCurrencyTotal
import com.farmos.feature.ops.ProcurementPurchaseView
import com.farmos.feature.ops.ProcurementRecords
import com.farmos.feature.ops.ProcurementSupplierView
import java.math.BigDecimal

private const val PROCUREMENT_RECORD_LIMIT = 500

/**
 * Farm-scoped read model for the read-only procurement record pages. Nothing here writes.
 * Supplier totals and the purchase count are exhaustive aggregates; the purchase list is the latest
 * [PROCUREMENT_RECORD_LIMIT] rows. Amounts stay in stored integer minor units per currency.
 */
internal suspend fun loadProcurementRecords(database: FarmOsDatabase, farmId: String): ProcurementRecords {
    val lifecycle = database.lifecycle()
    val suppliers = lifecycle.suppliers(farmId)
    val supplierNames = suppliers.associate { it.id to it.name }
    val items = database.inventory().items(farmId).associateBy { it.id }
    val totals = lifecycle.purchaseTotalsBySupplier(farmId).groupBy { it.supplierId }
    return ProcurementRecords(
        suppliers = suppliers.map { supplier ->
            val rows = totals[supplier.id].orEmpty()
            ProcurementSupplierView(
                id = supplier.id,
                name = supplier.name,
                leadTimeDays = supplier.leadTimeDays,
                totals = rows.map { ProcurementCurrencyTotal(it.currency, it.amountMinor, it.purchaseCount) },
                latestPurchaseEpochDay = rows.maxOfOrNull { it.latestEpochDay },
            )
        },
        purchases = lifecycle.purchases(farmId, PROCUREMENT_RECORD_LIMIT).map { purchase ->
            val item = items[purchase.itemId]
            val quantity = BigDecimal.valueOf(purchase.quantityMilli, 3).stripTrailingZeros().toPlainString()
            ProcurementPurchaseView(
                id = purchase.id,
                supplierId = purchase.supplierId,
                supplierName = supplierNames[purchase.supplierId] ?: "Supplier not on this device",
                itemLabel = item?.name ?: "Item not on this device",
                quantity = item?.let { "$quantity ${it.unit}" } ?: "$quantity (unit not on this device)",
                amountMinor = purchase.amountMinor,
                currency = purchase.currency,
                epochDay = purchase.occurredEpochDay,
            )
        },
        purchaseCount = lifecycle.purchaseCount(farmId),
    )
}
