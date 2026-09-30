package com.farmos.domain.ops

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StockCountRulesTest {
    @Test
    fun aCountMovesOnlyForwardThroughReview() {
        assertTrue(StockCountRules.canMove(StockCountStatus.COUNTING, StockCountStatus.SUBMITTED))
        assertTrue(StockCountRules.canMove(StockCountStatus.SUBMITTED, StockCountStatus.POSTED))
        assertTrue(StockCountRules.canMove(StockCountStatus.SUBMITTED, StockCountStatus.REJECTED))
        assertFalse(StockCountRules.canMove(StockCountStatus.COUNTING, StockCountStatus.POSTED))
        assertFalse(StockCountRules.canMove(StockCountStatus.POSTED, StockCountStatus.REJECTED))
        assertFalse(StockCountRules.canMove(StockCountStatus.REJECTED, StockCountStatus.SUBMITTED))
    }

    @Test
    fun varianceIsCountedMinusOnHandAndOnlyNonZeroLinesAdjust() {
        val gain = StockCountLine("mash", onHandAtCountMilli = 10_000, countedMilli = 12_500)
        val loss = StockCountLine("salt", onHandAtCountMilli = 4_000, countedMilli = 3_000)
        val exact = StockCountLine("wire", onHandAtCountMilli = 7_000, countedMilli = 7_000)
        assertEquals(2_500, gain.varianceMilli)
        assertEquals(-1_000, loss.varianceMilli)
        assertEquals(listOf(gain, loss), StockCountRules.adjustments(listOf(gain, loss, exact)))
        assertEquals(StockCountRules.COUNT_GAIN, StockCountRules.direction(gain))
        assertEquals(StockCountRules.COUNT_LOSS, StockCountRules.direction(loss))
        assertEquals("A counted quantity cannot be negative", StockCountRules.lineError(-1))
        assertNull(StockCountRules.lineError(0))
    }

    @Test
    fun idsAreStablePerCountAndItem() {
        assertEquals(StockCountRules.adjustmentMovementId("c1", "mash"), StockCountRules.adjustmentMovementId("c1", "mash"))
        assertNotEquals(StockCountRules.adjustmentMovementId("c1", "mash"), StockCountRules.adjustmentMovementId("c2", "mash"))
        assertNotEquals(StockCountRules.lineId("c1", "mash"), StockCountRules.adjustmentMovementId("c1", "mash"))
    }
}
