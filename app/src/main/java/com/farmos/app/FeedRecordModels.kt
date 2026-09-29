package com.farmos.app

import com.farmos.core.database.FarmOsDatabase
import com.farmos.feature.ops.FeedIssueView
import com.farmos.feature.ops.FeedItemView
import com.farmos.feature.ops.FeedRecords
import java.math.BigDecimal

private const val FEED_RECORD_LIMIT = 500

private fun milli(value: Long): String = BigDecimal.valueOf(value, 3).stripTrailingZeros().toPlainString()

/**
 * Farm-scoped read model for the read-only feed record pages. Nothing here writes. Per-item totals
 * and the issue count are exhaustive aggregates; the list is the latest [FEED_RECORD_LIMIT] rows.
 * Quantities are only summed within one inventory item, and shown with that item's recorded unit.
 */
internal suspend fun loadFeedRecords(database: FarmOsDatabase, farmId: String): FeedRecords {
    val feed = database.feedIssues()
    val items = database.inventory().items(farmId).associateBy { it.id }
    val groups = database.groups().forFarm(farmId).associate { it.id to it.name }
    fun quantity(itemId: String, quantityMilli: Long): String =
        items[itemId]?.let { "${milli(quantityMilli)} ${it.unit}" } ?: "${milli(quantityMilli)} (unit not on this device)"
    fun itemLabel(itemId: String): String = items[itemId]?.name ?: "Item not on this device"
    return FeedRecords(
        issues = feed.recent(farmId, FEED_RECORD_LIMIT).map { issue ->
            FeedIssueView(
                id = issue.id,
                itemId = issue.itemId,
                itemLabel = itemLabel(issue.itemId),
                quantity = quantity(issue.itemId, issue.quantityMilli),
                groupLabel = issue.groupId?.let { groups[it] ?: "Group not on this device" } ?: "No group recorded",
                epochDay = issue.occurredEpochDay,
            )
        },
        issueCount = feed.count(farmId),
        items = feed.totalsByItem(farmId).map { total ->
            FeedItemView(
                itemId = total.itemId,
                itemLabel = itemLabel(total.itemId),
                totalIssued = quantity(total.itemId, total.quantityMilli),
                issueCount = total.issueCount,
                firstEpochDay = total.firstEpochDay,
                latestEpochDay = total.latestEpochDay,
                onHand = items[total.itemId]?.let { "${milli(it.quantityMilli)} ${it.unit}" },
            )
        },
    )
}
