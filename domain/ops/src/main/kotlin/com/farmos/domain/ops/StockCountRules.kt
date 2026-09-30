package com.farmos.domain.ops

import java.util.UUID

/** Owner decision D-021: count, then variance, then review, then post. */
enum class StockCountStatus {
    /** Workers are recording counted quantities. */
    COUNTING,

    /** Counting is finished and waits for management review. */
    SUBMITTED,

    /** Management posted the variances as stock adjustments. */
    POSTED,

    /** Management rejected the count; nothing was adjusted. The count is kept for audit. */
    REJECTED,
}

/** One counted item: what was on hand when it was counted and what was found. Quantities in milli-units. */
data class StockCountLine(val itemId: String, val onHandAtCountMilli: Long, val countedMilli: Long) {
    /** Counted minus on hand at the count: positive is a gain, negative a loss. */
    val varianceMilli: Long get() = countedMilli - onHandAtCountMilli
}

object StockCountRules {
    /** Whether a count in [status] may move to [next]. */
    fun canMove(status: StockCountStatus, next: StockCountStatus): Boolean = when (status) {
        StockCountStatus.COUNTING -> next == StockCountStatus.SUBMITTED
        StockCountStatus.SUBMITTED -> next == StockCountStatus.POSTED || next == StockCountStatus.REJECTED
        StockCountStatus.POSTED, StockCountStatus.REJECTED -> false
    }

    fun lineError(countedMilli: Long): String? = if (countedMilli < 0) "A counted quantity cannot be negative" else null

    /** Lines whose variance is not zero; posting adjusts stock for these only. */
    fun adjustments(lines: List<StockCountLine>): List<StockCountLine> = lines.filter { it.varianceMilli != 0L }

    /**
     * The stable id of a count line's adjustment movement. Every device derives the same id, so a count
     * posted on two devices offline adjusts stock once.
     */
    fun adjustmentMovementId(countId: String, itemId: String): String =
        UUID.nameUUIDFromBytes("stock-count-adjustment:$countId:$itemId".toByteArray()).toString()

    /** The stable id of the line for one item in one count, so counting an item again replaces its line. */
    fun lineId(countId: String, itemId: String): String = UUID.nameUUIDFromBytes("stock-count-line:$countId:$itemId".toByteArray()).toString()

    /** Movement direction for an adjustment: a gain adds stock, a loss removes it. */
    fun direction(line: StockCountLine): String = if (line.varianceMilli > 0) COUNT_GAIN else COUNT_LOSS

    const val COUNT_GAIN = "count_gain"
    const val COUNT_LOSS = "count_loss"
}
