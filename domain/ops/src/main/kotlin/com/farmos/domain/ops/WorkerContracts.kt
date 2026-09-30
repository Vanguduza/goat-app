package com.farmos.domain.ops

import kotlinx.serialization.Serializable

/** Adds a worker to the farm's register (D-016, resolution R1). Replicated as `worker.create.v1`. */
@Serializable
data class CreateFarmWorker(val workerId: String, val name: String)

/**
 * Renames a worker or makes them active or inactive; a null field keeps its value. The later change by
 * business time wins on every device. Replicated as `worker.update.v1`.
 */
@Serializable
data class UpdateFarmWorker(val workerId: String, val name: String? = null, val active: Boolean? = null)

object WorkerRules {
    const val MAX_NAME = 80

    fun name(name: String): String? = when {
        name.isBlank() -> "Enter the worker's name"
        name.trim().length > MAX_NAME -> "A worker's name is at most $MAX_NAME characters"
        else -> null
    }

    fun update(command: UpdateFarmWorker): String? = when {
        command.name == null && command.active == null -> "Nothing to change"
        command.name != null -> name(command.name)
        else -> null
    }
}

/**
 * Labour worked by a registered worker (resolution R1). [workerName] is the name when recorded, kept with
 * the entry; totals follow [workerId], so a later rename does not split them. Replicated as `labour.record.v2`.
 */
@Serializable
data class RecordWorkerLabour(
    val entryId: String,
    val workerId: String,
    val workerName: String,
    val taskCode: String,
    val minutes: Int,
    val occurredEpochDay: Long,
    val note: String? = null,
)
