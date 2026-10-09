package com.farmos.app

import com.farmos.core.database.FeedItemTotal
import com.farmos.core.database.FeedPlanEntity
import com.farmos.core.database.InventoryItemEntity
import com.farmos.core.database.PurchaseEntity
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationsReportMathTest {
    private fun purchase(id: String, currency: String, amount: Long, quantity: Long = 1_000) =
        PurchaseEntity(id, "farm", "supplier", "feed", quantity, amount, currency, 20_000)
    private fun item(unit: String) = InventoryItemEntity("feed", "farm", "FEED", "Hay", unit, 10_000, 0)

    @Test
    fun everyPurchaseContributesBeyondTheFormerFiftyRowLimit() {
        val purchases = (1..60).map { purchase("p$it", "USD", if (it <= 50) 100 else 1_000) }
        val totals = listOf(FeedItemTotal("feed", 60_000, 60, 1, 60))
        val lines = feedCostLines(totals, purchases, listOf(item("kg")))
        assertTrue(lines.contains("Priced-item estimate: USD 150.00"))
        assertTrue(lines.any { it.contains("60 kg issued") })
    }

    @Test
    fun currenciesRemainSeparateAndInventoryUnitsArePreserved() {
        val lines = feedCostLines(
            listOf(FeedItemTotal("feed", 2_000, 2, 1, 2)),
            listOf(purchase("usd", "USD", 125), purchase("jpy", "JPY", 250)),
            listOf(item("bag")),
        )
        assertTrue(lines.contains("Priced-item estimate: USD 2.50"))
        assertTrue(lines.contains("Priced-item estimate: JPY 500"))
        assertTrue(lines.filter { "issued" in it }.all { "2 bag issued" in it })
        assertEquals(2, feedPurchaseRates(listOf(purchase("a", "USD", 125), purchase("b", "JPY", 250))).size)
    }

    @Test
    fun eachCurrencyUsesItsOwnMinorUnitScale() {
        assertEquals("USD 1.25", reportMoney(BigDecimal("125"), "USD"))
        assertEquals("JPY 125", reportMoney(BigDecimal("125"), "JPY"))
        assertEquals("BHD 1.250", reportMoney(BigDecimal("1250"), "BHD"))
    }

    @Test
    fun massConversionDistinguishesMilliKilogramsFromMilliGramsAndRefusesBags() {
        assertEquals(0, BigDecimal("1000").compareTo(rationQuantityMilli(BigDecimal("1000"), "kg")))
        assertEquals(0, BigDecimal("1000000").compareTo(rationQuantityMilli(BigDecimal("1000"), "g")))
        assertNull(rationQuantityMilli(BigDecimal("1000"), "bag"))
        assertNull(rationQuantityMilli(BigDecimal("1000"), "L"))
    }

    @Test
    fun feedRequirementsDoNotOverflowLongMultiplication() {
        val plan = FeedPlanEntity("plan", "farm", "Plan", "goat", Long.MAX_VALUE, 2, 1, 2, null)
        assertEquals("36893488147419103228", feedPlanTotalGrams(plan).toPlainString())
    }

    @Test
    fun absentPricesAreUnavailableRatherThanZero() {
        val lines = feedCostLines(listOf(FeedItemTotal("feed", 1_000, 1, 1, 1)), emptyList(), listOf(item("kg")))
        assertEquals(listOf("Hay · 1 kg issued · no purchase price recorded"), lines)
    }
}
