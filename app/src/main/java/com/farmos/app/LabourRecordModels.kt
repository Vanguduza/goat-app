package com.farmos.app

import com.farmos.core.database.FarmOsDatabase
import com.farmos.feature.ops.LabourEntryView
import com.farmos.feature.ops.LabourRecords
import com.farmos.feature.ops.LabourWorkerTotalView

private const val LABOUR_RECORD_LIMIT = 500

/**
 * Farm-scoped read model for the read-only work log. Nothing here writes. Per-worker-label minutes
 * and the entry count are exhaustive aggregates; the list is the latest [LABOUR_RECORD_LIMIT] rows.
 */
internal suspend fun loadLabourRecords(database: FarmOsDatabase, farmId: String): LabourRecords {
    val labour = database.labour()
    return LabourRecords(
        entries = labour.recent(farmId, LABOUR_RECORD_LIMIT).map { LabourEntryView(it.id, it.workerName, it.taskCode, it.minutes, it.note, it.occurredEpochDay) },
        entryCount = labour.count(farmId),
        workers = labour.totalsByWorkerName(farmId).map { LabourWorkerTotalView(it.workerName, it.minutes, it.entryCount, it.latestEpochDay) },
    )
}
