package com.farmos.data.herd

import androidx.room.withTransaction
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.TaskEntity
import com.farmos.core.database.TaskSeriesEntity
import com.farmos.core.database.journalLocalOperation
import com.farmos.core.model.LocalCommandContext
import com.farmos.core.model.LocalCommandResult
import com.farmos.domain.ops.CompleteTaskOccurrence
import com.farmos.domain.ops.CreateTaskSeries
import com.farmos.domain.ops.EditTaskSeries
import com.farmos.domain.ops.EndTaskSeries
import com.farmos.domain.ops.SeriesEditScope
import com.farmos.domain.ops.TaskAssignee
import com.farmos.domain.ops.TaskOccurrence
import com.farmos.domain.ops.TaskRecurrenceSchedule
import com.farmos.domain.ops.TaskSeriesRules
import com.farmos.domain.ops.TaskSeriesSchedule
import com.farmos.domain.ops.UpdateFarmTask
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Repeating tasks (owner decision D-020). A series is master data; its occurrences are derived from it.
 * An occurrence is stored as a farm_tasks row, under the id every device derives for that series and day,
 * only once it is completed or edited on its own. Completed rows are never changed or removed. Journalled
 * for farm replication only; there is no server to receive them.
 */
class TaskSeriesCommands(
    private val database: FarmOsDatabase,
    private val farmId: String,
    /** Replays an operation received from another device: domain writes only, it is already journalled. */
    private val replaying: Boolean = false,
    private val json: Json = Json { encodeDefaults = true },
) {
    suspend fun create(command: CreateTaskSeries, context: LocalCommandContext): LocalCommandResult {
        TaskSeriesRules.create(command)?.let { error(it) }
        journal(context, SERIES_CREATE, command.seriesId, command) {
            requireAssignee(command.assignee)
            database.taskSeries().upsert(
                TaskSeriesEntity(
                    id = command.seriesId,
                    farmId = farmId,
                    moduleCode = command.moduleCode,
                    taskCode = command.taskCode,
                    title = command.title.trim(),
                    recurrenceKind = command.recurrenceKind,
                    recurrenceInterval = command.recurrenceInterval,
                    startEpochDay = command.startEpochDay,
                    endEpochDay = command.endEpochDay,
                    animalId = command.animalId,
                    assigneeAccountId = command.assignee.accountId,
                    assigneeWorkerId = command.assignee.workerId,
                    status = ACTIVE,
                    updatedAtEpochMillis = context.occurredAtEpochMillis,
                    updatedByActorId = context.actorId,
                ),
            )
        }
        return LocalCommandResult(context.mutationId, command.seriesId, true)
    }

    /** Completes one occurrence. Completing it again, on this or another device, changes nothing. */
    suspend fun complete(command: CompleteTaskOccurrence, context: LocalCommandContext): LocalCommandResult {
        val id = TaskRecurrenceSchedule.occurrenceId(command.seriesId, command.epochDay)
        journal(context, OCCURRENCE_COMPLETE, command.seriesId, command) {
            val series = series(command.seriesId)
            val current = database.tasks().get(farmId, id)
            if (current == null) require(TaskRecurrenceSchedule.isOccurrence(series.schedule(), command.epochDay)) { "That day is not an occurrence of this task" }
            when {
                current?.status == DONE -> Unit
                current != null -> database.tasks().updateStatus(farmId, id, DONE, context.occurredAtEpochMillis)
                else -> database.tasks().insert(series.occurrence(command.epochDay, DONE, context.occurredAtEpochMillis))
            }
        }
        return LocalCommandResult(context.mutationId, id, true)
    }

    suspend fun edit(command: EditTaskSeries, context: LocalCommandContext): LocalCommandResult {
        TaskSeriesRules.edit(command)?.let { error(it) }
        val scope = SeriesEditScope.valueOf(command.scope)
        journal(context, SERIES_EDIT, command.seriesId, command) {
            command.assignee?.let { requireAssignee(it) }
            val series = series(command.seriesId)
            val occurrenceId = TaskRecurrenceSchedule.occurrenceId(series.id, command.epochDay)
            val stored = database.tasks().get(farmId, occurrenceId)
            require(stored != null || TaskRecurrenceSchedule.isOccurrence(series.schedule(), command.epochDay)) { "That day is not an occurrence of this task" }
            require(stored?.status != DONE) { "A completed occurrence is kept as it was done and cannot be changed" }
            when (scope) {
                SeriesEditScope.THIS -> {
                    val base = stored ?: series.occurrence(command.epochDay, OPEN, context.occurredAtEpochMillis)
                    val assignee = command.assignee
                    database.tasks().upsertFromServer(
                        base.copy(
                            title = command.title?.trim() ?: base.title,
                            dueOnEpochDay = command.movedToEpochDay ?: base.dueOnEpochDay,
                            assigneeAccountId = if (assignee != null) assignee.accountId else base.assigneeAccountId,
                            assigneeWorkerId = if (assignee != null) assignee.workerId else base.assigneeWorkerId,
                            updatedAtEpochMillis = context.occurredAtEpochMillis,
                        ),
                    )
                }
                SeriesEditScope.THIS_AND_FUTURE -> {
                    val (before, after) = TaskRecurrenceSchedule.splitAt(series.schedule(), command.epochDay)
                    database.taskSeries().upsert(series.copy(endEpochDay = before.endEpochDay, updatedAtEpochMillis = context.occurredAtEpochMillis, updatedByActorId = context.actorId))
                    // Occurrences changed one by one from the split onwards give way to the new series; completed ones stay.
                    database.taskSeries().storedOccurrences(farmId, series.id)
                        .filter { it.status == OPEN && (it.occurrenceEpochDay ?: it.dueOnEpochDay) >= command.epochDay }
                        .forEach { database.tasks().deleteOpen(farmId, it.id) }
                    database.taskSeries().upsert(series.edited(command, context).copy(id = requireNotNull(command.newSeriesId), startEpochDay = after.startEpochDay, endEpochDay = after.endEpochDay))
                }
                SeriesEditScope.SERIES -> database.taskSeries().upsert(series.edited(command, context))
            }
        }
        return LocalCommandResult(context.mutationId, command.newSeriesId ?: command.seriesId, true)
    }

    /** Changes an open one-off task (R2); a task in a series is edited through its series, a completed task never. */
    suspend fun update(command: UpdateFarmTask, context: LocalCommandContext): LocalCommandResult {
        TaskSeriesRules.update(command)?.let { error(it) }
        journal(context, TASK_UPDATE, command.taskId, command, entityType = "task") {
            command.assignee?.let { requireAssignee(it) }
            val current = requireNotNull(database.tasks().get(farmId, command.taskId)) { "Task not found" }
            require(current.seriesId == null) { "A repeating task is changed through its series" }
            require(current.status != DONE) { "A completed task is kept as it was done and cannot be changed" }
            // A task completed on another device first stays as it was done.
            if (current.status != DONE) {
                val assignee = command.assignee
                database.tasks().upsertFromServer(
                    current.copy(
                        title = command.title?.trim() ?: current.title,
                        dueOnEpochDay = command.dueEpochDay ?: current.dueOnEpochDay,
                        assigneeAccountId = if (assignee != null) assignee.accountId else current.assigneeAccountId,
                        assigneeWorkerId = if (assignee != null) assignee.workerId else current.assigneeWorkerId,
                        updatedAtEpochMillis = context.occurredAtEpochMillis,
                    ),
                )
            }
        }
        return LocalCommandResult(context.mutationId, command.taskId, true)
    }

    /** Ends a series after [EndTaskSeries.lastEpochDay]; a day before its start stops it before any occurrence. */
    suspend fun end(command: EndTaskSeries, context: LocalCommandContext): LocalCommandResult {
        journal(context, SERIES_END, command.seriesId, command) {
            val series = series(command.seriesId)
            val ended = if (command.lastEpochDay < series.startEpochDay) {
                series.copy(status = ENDED)
            } else {
                series.copy(endEpochDay = minOf(command.lastEpochDay, series.endEpochDay ?: Long.MAX_VALUE))
            }
            database.taskSeries().upsert(ended.copy(updatedAtEpochMillis = context.occurredAtEpochMillis, updatedByActorId = context.actorId))
        }
        return LocalCommandResult(context.mutationId, command.seriesId, true)
    }

    private suspend fun series(seriesId: String): TaskSeriesEntity =
        requireNotNull(database.taskSeries().get(farmId, seriesId)) { "Task series not found" }

    private suspend fun requireAssignee(assignee: TaskAssignee) {
        // Replay trusts the origin device: the account may not have reached this device yet.
        if (replaying) return
        assignee.accountId?.let { require(database.taskSeries().accountOnFarm(farmId, it)) { "That account is not on this farm" } }
        assignee.workerId?.let { require(database.taskSeries().activeWorkerOnFarm(farmId, it)) { "That worker is not active on this farm" } }
    }

    private fun TaskSeriesEntity.schedule() =
        TaskSeriesSchedule(TaskSeriesRules.recurrence(recurrenceKind, recurrenceInterval), startEpochDay, endEpochDay)

    private fun TaskSeriesEntity.occurrence(epochDay: Long, status: String, updatedAt: Long) = TaskEntity(
        id = TaskRecurrenceSchedule.occurrenceId(id, epochDay),
        farmId = farmId,
        moduleCode = moduleCode,
        taskCode = taskCode,
        title = title,
        dueOnEpochDay = epochDay,
        status = status,
        animalId = animalId,
        cageId = null,
        waveId = null,
        updatedAtEpochMillis = updatedAt,
        seriesId = id,
        occurrenceEpochDay = epochDay,
        assigneeAccountId = assigneeAccountId,
        assigneeWorkerId = assigneeWorkerId,
    )

    private fun TaskSeriesEntity.edited(command: EditTaskSeries, context: LocalCommandContext): TaskSeriesEntity {
        val kind = command.recurrenceKind ?: recurrenceKind
        val interval = command.recurrenceInterval ?: if (command.recurrenceKind != null) 1 else recurrenceInterval
        val assignee = command.assignee
        // Validates the resulting rule before anything is written.
        TaskSeriesSchedule(TaskSeriesRules.recurrence(kind, interval), startEpochDay, endEpochDay)
        return copy(
            title = command.title?.trim() ?: title,
            recurrenceKind = kind,
            recurrenceInterval = interval,
            assigneeAccountId = if (assignee != null) assignee.accountId else assigneeAccountId,
            assigneeWorkerId = if (assignee != null) assignee.workerId else assigneeWorkerId,
            updatedAtEpochMillis = context.occurredAtEpochMillis,
            updatedByActorId = context.actorId,
        )
    }

    private suspend inline fun <reified C> journal(
        context: LocalCommandContext,
        commandName: String,
        entityId: String,
        command: C,
        entityType: String = "task_series",
        noinline localWrite: suspend () -> Unit,
    ) {
        require(context.farmId == farmId) { "Farm context mismatch" }
        database.withTransaction {
            val payloadJson = json.encodeToString(command)
            val permission = OpsCommandPermissions.requiredFor(commandName, payloadJson)
            if (!replaying) database.requireLocalCommandAuthority(context, permission)
            val original = database.replication().operation(farmId, context.mutationId)
            if (original != null) {
                requireOriginalCommand(original, context, commandName, entityType, entityId, {
                    json.decodeFromString<C>(it) == command
                })
                if (database.commandAlreadyApplied(original, replaying)) return@withTransaction
            } else {
                require(!replaying) { "A received change must be journalled before it is applied" }
            }
            localWrite()
            if (replaying) return@withTransaction
            database.journalLocalOperation(
                operationId = context.mutationId,
                farmId = farmId,
                entityType = entityType,
                entityId = entityId,
                actorId = context.actorId,
                deviceId = context.deviceId,
                businessTimeEpochMillis = context.occurredAtEpochMillis,
                createdAtEpochMillis = System.currentTimeMillis(),
                baseVersion = null,
                operationType = commandName,
                payloadJson = payloadJson,
                schemaVersion = 1,
            )
        }
    }

    companion object {
        const val SERIES_CREATE = "task.series_create.v1"
        const val OCCURRENCE_COMPLETE = "task.occurrence_complete.v1"
        const val SERIES_EDIT = "task.series_edit.v1"
        const val SERIES_END = "task.series_end.v1"
        const val TASK_UPDATE = "task.update.v1"
        private const val ACTIVE = "active"
        private const val ENDED = "ended"
        private const val OPEN = "open"
        private const val DONE = "done"
    }
}

/** Reads open task occurrences (D-020): derived from each active series, with occurrences changed alone taking their place. */
class TaskSeriesQueries(private val database: FarmOsDatabase, private val farmId: String) {
    /**
     * Every open occurrence whose due day falls in [fromEpochDay]..[toEpochDay], from the full local
     * database. With [assignedToAccountId], only occurrences assigned to that account.
     */
    suspend fun openOccurrences(fromEpochDay: Long, toEpochDay: Long, assignedToAccountId: String? = null): List<TaskOccurrence> =
        database.taskSeries().active(farmId).flatMap { series ->
            val stored = database.taskSeries().storedOccurrences(farmId, series.id).associateBy { it.occurrenceEpochDay ?: it.dueOnEpochDay }
            val schedule = TaskSeriesSchedule(TaskSeriesRules.recurrence(series.recurrenceKind, series.recurrenceInterval), series.startEpochDay, series.endEpochDay)
            val derived = TaskRecurrenceSchedule.occurrences(schedule, fromEpochDay, toEpochDay)
                .filter { it !in stored }
                .map { day -> series.toOccurrence(day) }
            val editedAlone = stored.values
                .filter { it.status == "open" && it.dueOnEpochDay in fromEpochDay..toEpochDay }
                .map { it.toOccurrence(series.id) }
            derived + editedAlone
        }
            .filter { assignedToAccountId == null || it.assignee.accountId == assignedToAccountId }
            .sortedWith(compareBy({ it.dueEpochDay }, { it.title }, { it.id }))

    private fun TaskSeriesEntity.toOccurrence(day: Long) = TaskOccurrence(
        id = TaskRecurrenceSchedule.occurrenceId(id, day),
        seriesId = id,
        occurrenceEpochDay = day,
        dueEpochDay = day,
        moduleCode = moduleCode,
        taskCode = taskCode,
        title = title,
        animalId = animalId,
        assignee = TaskAssignee(assigneeAccountId, assigneeWorkerId),
        editedAlone = false,
    )

    private fun TaskEntity.toOccurrence(seriesId: String) = TaskOccurrence(
        id = id,
        seriesId = seriesId,
        occurrenceEpochDay = occurrenceEpochDay ?: dueOnEpochDay,
        dueEpochDay = dueOnEpochDay,
        moduleCode = moduleCode,
        taskCode = taskCode,
        title = title,
        animalId = animalId,
        assignee = TaskAssignee(assigneeAccountId, assigneeWorkerId),
        editedAlone = true,
    )
}
