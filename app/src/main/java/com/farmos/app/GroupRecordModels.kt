package com.farmos.app

import com.farmos.core.database.FarmOsDatabase
import com.farmos.feature.ops.GroupCensusView
import com.farmos.feature.ops.GroupFeedView
import com.farmos.feature.ops.GroupGrazingView
import com.farmos.feature.ops.GroupRecords
import com.farmos.feature.ops.GroupView
import java.math.BigDecimal

/** Farm-scoped groups for the read-only group record pages. Nothing here writes. */
internal suspend fun loadGroupViews(database: FarmOsDatabase, farmId: String): List<GroupView> =
    database.groups().forFarm(farmId).map { GroupView(it.id, it.name, it.speciesCode, it.headCount) }

/** Every census, grazing and feed record for one group, read exhaustively inside the farm. */
internal suspend fun loadGroupRecords(database: FarmOsDatabase, farmId: String, groupId: String): GroupRecords {
    val paddocks = database.paddocks().forFarm(farmId).associate { it.id to "${it.code} · ${it.displayName}" }
    val items = database.inventory().items(farmId).associateBy { it.id }
    return GroupRecords(
        census = database.lifecycle().censusForGroup(farmId, groupId).map { GroupCensusView(it.id, it.headCount, it.occurredEpochDay) },
        grazing = database.grazing().forGroup(farmId, groupId).map {
            GroupGrazingView(it.id, paddocks[it.paddockId] ?: "Paddock not on this device", it.headCount, it.enteredEpochDay, it.exitedEpochDay)
        },
        feed = database.feedIssues().forGroup(farmId, groupId).map { issue ->
            val item = items[issue.itemId]
            val quantity = BigDecimal.valueOf(issue.quantityMilli, 3).stripTrailingZeros().toPlainString()
            GroupFeedView(issue.id, item?.name ?: "Item not on this device", item?.let { "$quantity ${it.unit}" } ?: "$quantity (unit not on this device)", issue.occurredEpochDay)
        },
    )
}
