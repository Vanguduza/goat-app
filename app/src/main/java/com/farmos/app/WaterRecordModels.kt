package com.farmos.app

import com.farmos.core.database.FarmOsDatabase
import com.farmos.feature.ops.WaterRecordView
import com.farmos.feature.ops.WaterRecords
import com.farmos.feature.ops.WaterSourceTotalView

private const val WATER_RECORD_LIMIT = 500

/**
 * Farm-scoped read model for the read-only water history. Nothing here writes. Per-source totals
 * and the record count are exhaustive aggregates; the list is the latest [WATER_RECORD_LIMIT] rows.
 */
internal suspend fun loadWaterRecords(database: FarmOsDatabase, farmId: String): WaterRecords {
    val water = database.water()
    return WaterRecords(
        records = water.recent(farmId, WATER_RECORD_LIMIT).map { WaterRecordView(it.id, it.source, it.litresMilli, it.occurredEpochDay) },
        recordCount = water.count(farmId),
        sources = water.totalsBySource(farmId).map { WaterSourceTotalView(it.source, it.litresMilli, it.recordCount, it.firstEpochDay, it.latestEpochDay) },
    )
}
