package com.farmos.app

import com.farmos.core.database.FarmOsDatabase
import com.farmos.feature.ops.GrazingSessionView
import com.farmos.feature.ops.PaddockView
import com.farmos.feature.ops.PastureRecords

private const val GRAZING_RECORD_LIMIT = 500

/**
 * Farm-scoped read model for the read-only pasture record pages. Nothing here writes. Per-paddock
 * grazing summaries and the session count are exhaustive aggregates; the session list is the latest
 * [GRAZING_RECORD_LIMIT] rows.
 */
internal suspend fun loadPastureRecords(database: FarmOsDatabase, farmId: String): PastureRecords {
    val paddocks = database.paddocks().forFarm(farmId)
    val paddockLabels = paddocks.associate { it.id to "${it.code} · ${it.displayName}" }
    val groupNames = database.groups().forFarm(farmId).associate { it.id to it.name }
    val grazing = database.grazing()
    val summaries = grazing.summaryByPaddock(farmId).associateBy { it.paddockId }
    return PastureRecords(
        paddocks = paddocks.map { paddock ->
            val summary = summaries[paddock.id]
            PaddockView(
                id = paddock.id,
                code = paddock.code,
                displayName = paddock.displayName,
                areaM2 = paddock.areaM2,
                waterSource = paddock.waterSource,
                shade = paddock.shade,
                active = paddock.active,
                sessionCount = summary?.sessionCount ?: 0,
                openSessions = summary?.openSessions ?: 0,
                openHeadCount = summary?.openHeadCount ?: 0,
                latestEnteredEpochDay = summary?.latestEnteredEpochDay,
                latestExitedEpochDay = summary?.latestExitedEpochDay,
            )
        },
        sessions = grazing.recent(farmId, GRAZING_RECORD_LIMIT).map {
            GrazingSessionView(
                id = it.id,
                paddockId = it.paddockId,
                paddockLabel = paddockLabels[it.paddockId] ?: "Paddock not on this device",
                groupLabel = groupNames[it.groupId] ?: "Group not on this device",
                speciesCode = it.speciesCode,
                headCount = it.headCount,
                enteredEpochDay = it.enteredEpochDay,
                exitedEpochDay = it.exitedEpochDay,
            )
        },
        sessionCount = grazing.count(farmId),
    )
}
