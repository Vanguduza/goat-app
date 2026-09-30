package com.farmos.app

import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.LabourCommands
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.ops.RecordLabour
import com.farmos.domain.ops.RecordWorkerLabour
import com.farmos.feature.ops.LabourWorkerOption
import java.time.LocalDate
import java.util.UUID

/**
 * Labour capture (resolution R1): once the farm has registered workers, work is recorded against one of
 * them (`labour.record.v2`); before that, a typed label is kept (`labour.record.v1`).
 */
class LabourCapture(private val database: FarmOsDatabase, private val farmId: String, private val ops: RoomOpsRepository) {
    suspend fun activeWorkers(): List<LabourWorkerOption> = database.workers().active(farmId).map { LabourWorkerOption(it.id, it.name) }

    suspend fun record(
        workers: List<LabourWorkerOption>,
        workerId: String?,
        typedName: String,
        taskCode: String,
        minutesText: String,
        dayText: String,
        context: LocalCommandContext,
    ) {
        val day = runCatching { LocalDate.parse(dayText.trim()).toEpochDay() }.getOrElse { error("Enter the date as YYYY-MM-DD") }
        val minutes = minutesText.trim().toIntOrNull() ?: 0
        val entryId = UUID.randomUUID().toString()
        if (workers.isEmpty()) {
            ops.recordLabour(RecordLabour(entryId, typedName, taskCode, minutes, day), context)
        } else {
            val worker = workers.firstOrNull { it.workerId == workerId } ?: error("Choose the worker who did the work")
            LabourCommands(database, farmId).record(RecordWorkerLabour(entryId, worker.workerId, worker.name, taskCode, minutes, day), context)
        }
    }
}
