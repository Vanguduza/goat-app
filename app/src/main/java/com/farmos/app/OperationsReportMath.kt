package com.farmos.app

import com.farmos.core.database.FeedItemTotal
import com.farmos.core.database.FeedPlanEntity
import com.farmos.core.database.InventoryItemEntity
import com.farmos.core.database.PurchaseEntity
import com.farmos.domain.ops.FarmCurrency
import com.farmos.domain.ops.UnitSystems
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

internal fun reportMilli(value: Long): String =
    BigDecimal.valueOf(value, 3).stripTrailingZeros().toPlainString()

internal fun feedDailyGrams(rationGrams: Long, headCount: Int): BigDecimal =
    BigDecimal.valueOf(rationGrams).multiply(BigDecimal.valueOf(headCount.toLong()))

internal fun dailyKgLabel(rationGramsPerHeadPerDay: Long, headCount: Int): String =
    feedDailyGrams(rationGramsPerHeadPerDay, headCount).movePointLeft(3).stripTrailingZeros().toPlainString() + " kg/day"

internal fun feedPlanTotalGrams(plan: FeedPlanEntity): BigDecimal =
    feedDailyGrams(plan.rationGramsPerHeadPerDay, plan.headCount)
        .multiply(BigDecimal.valueOf(plan.endEpochDay).subtract(BigDecimal.valueOf(plan.startEpochDay)).add(BigDecimal.ONE))

internal fun reportKg(grams: BigDecimal): String = grams.movePointLeft(3).stripTrailingZeros().toPlainString() + " kg"

/** Inventory quantities are milli-units of the declared unit; bags and litres are never assumed to be kg. */
internal fun rationQuantityMilli(grams: BigDecimal, inventoryUnit: String): BigDecimal? =
    if (UnitSystems.isValid("weight", inventoryUnit)) {
        UnitSystems.toDisplay("weight", inventoryUnit, grams).movePointRight(3)
    } else null

internal data class FeedPurchaseRate(
    val itemId: String,
    val currency: String,
    val quantityMilli: BigDecimal,
    val amountMinor: BigDecimal,
) {
    fun estimateMinor(quantity: BigDecimal): BigDecimal =
        amountMinor.multiply(quantity).divide(quantityMilli, MathContext.DECIMAL128)
}

/** Complete purchase input, grouped by both item and currency. No exchange rate is invented. */
internal fun feedPurchaseRates(purchases: List<PurchaseEntity>): List<FeedPurchaseRate> =
    purchases.groupBy { it.itemId to it.currency }.mapNotNull { (key, rows) ->
        val quantity = rows.fold(BigDecimal.ZERO) { total, row -> total.add(BigDecimal.valueOf(row.quantityMilli)) }
        if (quantity.signum() <= 0) null else FeedPurchaseRate(
            key.first, key.second, quantity,
            rows.fold(BigDecimal.ZERO) { total, row -> total.add(BigDecimal.valueOf(row.amountMinor)) },
        )
    }.sortedWith(compareBy<FeedPurchaseRate> { it.itemId }.thenBy { it.currency })

internal fun reportMoney(amountMinor: BigDecimal, currency: String): String {
    val digits = runCatching { FarmCurrency.minorDigits(currency) }.getOrNull()
        ?: return "${amountMinor.stripTrailingZeros().toPlainString()} minor units ($currency)"
    return "$currency ${amountMinor.movePointLeft(digits).setScale(digits, RoundingMode.HALF_EVEN).toPlainString()}"
}

/** Totals contain every issue. Each currency's price is a separate estimate, never a ledger posting. */
internal fun feedCostLines(
    totals: List<FeedItemTotal>,
    purchases: List<PurchaseEntity>,
    items: List<InventoryItemEntity>,
): List<String> {
    val byId = items.associateBy { it.id }
    val rates = feedPurchaseRates(purchases).groupBy { it.itemId }
    val currencyTotals = sortedMapOf<String, BigDecimal>()
    val lines = totals.flatMap { total ->
        val item = byId[total.itemId]
        val quantity = "${reportMilli(total.quantityMilli)} ${item?.unit ?: "(unit unavailable)"}"
        val label = "${item?.name ?: total.itemId} · $quantity issued"
        val itemRates = rates[total.itemId].orEmpty()
        if (itemRates.isEmpty()) listOf("$label · no purchase price recorded") else itemRates.map { rate ->
            val estimate = rate.estimateMinor(BigDecimal.valueOf(total.quantityMilli))
            currencyTotals[rate.currency] = (currencyTotals[rate.currency] ?: BigDecimal.ZERO).add(estimate)
            "$label · ${reportMoney(estimate, rate.currency)} at ${rate.currency} purchase prices"
        }
    }
    return currencyTotals.map { (currency, total) ->
        "Priced-item estimate: ${reportMoney(total, currency)}"
    } + lines.ifEmpty { listOf("No feed issues recorded on this device.") }
}
