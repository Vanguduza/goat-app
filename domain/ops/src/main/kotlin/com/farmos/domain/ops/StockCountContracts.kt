package com.farmos.domain.ops

import kotlinx.serialization.Serializable

/** Starts a stock count. Replicated as `stock.count_start.v1`. */
@Serializable
data class StartStockCount(val countId: String)

/**
 * Records one item's counted quantity. [onHandAtCountMilli] is what this device held when the item was
 * counted, carried so every device computes the same variance. Counting an item again replaces its line;
 * across devices the later count wins. Replicated as `stock.count_line.v1`.
 */
@Serializable
data class RecordStockCountLine(val countId: String, val itemId: String, val countedMilli: Long, val onHandAtCountMilli: Long)

/** Finishes counting and sends the count for review. Replicated as `stock.count_submit.v1`. */
@Serializable
data class SubmitStockCount(val countId: String)

/** One posted adjustment: the variance of one counted item. */
@Serializable
data class StockCountAdjustment(val itemId: String, val varianceMilli: Long)

/**
 * Posts a submitted count's variances as stock adjustments. The adjustments posted are carried, so every
 * device applies exactly these, whether or not every count line has reached it. Replicated as
 * `stock.count_post.v1`.
 */
@Serializable
data class PostStockCount(val countId: String, val adjustments: List<StockCountAdjustment>)

/** Rejects a submitted count; nothing is adjusted and the count is kept. Replicated as `stock.count_reject.v1`. */
@Serializable
data class RejectStockCount(val countId: String, val reason: String)
