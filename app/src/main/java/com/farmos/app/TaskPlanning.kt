package com.farmos.app

import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.TaskSeriesCommands
import com.farmos.data.herd.TaskSeriesQueries
import com.farmos.domain.access.LocalRole
import com.farmos.domain.access.Permission
import com.farmos.domain.access.RolePermissions
import com.farmos.domain.ops.CreateTaskSeries
import com.farmos.domain.ops.EditTaskSeries
import com.farmos.domain.ops.SeriesEditScope
import com.farmos.domain.ops.TaskAssignee
import com.farmos.domain.ops.TaskOccurrence
import com.farmos.domain.ops.TaskRecurrenceSchedule
import com.farmos.domain.ops.TaskSeriesRules
import com.farmos.domain.ops.TaskSeriesSchedule
import com.farmos.domain.ops.UpdateFarmTask
import com.farmos.feature.ops.TaskAssigneeOption
import com.farmos.feature.ops.TaskEditScope
import com.farmos.feature.ops.TaskEditDraft
import com.farmos.feature.ops.TaskRepeat
import com.farmos.feature.ops.TaskSeriesDraft
import com.farmos.feature.ops.TaskSeriesUiRow
import com.farmos.feature.ops.TaskUpdateDraft
import com.farmos.feature.ops.taskRepeatLabel
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Planning repeating and assigned tasks (owner decision D-020): supervisors and management plan the work
 * (`MANAGE_WORKERS`); anyone who records farm work completes it.
 */
internal fun canPlanFarmWork(role: String): Boolean = rolePermits(role, Permission.MANAGE_WORKERS)

/** Whether a membership role holds [permission] under the local role matrix; unknown roles hold nothing. */
internal fun rolePermits(role: String, permission: Permission): Boolean {
    val local = LocalRole.entries.firstOrNull { it.name.equals(role, ignoreCase = true) }
        ?: when (role.lowercase()) {
            "farm_manager" -> LocalRole.MANAGER
            else -> return false
        }
    return RolePermissions.allows(local, permission)
}

/** The task board's adapter to the repeating-task commands and reads for one farm. */
class TaskPlanning(
    private val database: FarmOsDatabase,
    private val farmId: String,
    val canPlanWork: Boolean,
    /** The signed-in local account, for Assigned to me; null without a local account. */
    val currentAccountId: String?,
) {
    val commands = TaskSeriesCommands(database, farmId)
    private val queries = TaskSeriesQueries(database, farmId)

    /** Local accounts on this farm a task can be assigned to. */
    val loadAssignees: suspend () -> List<TaskAssigneeOption> = {
        withContext(Dispatchers.IO) { database.localAccess().accounts(farmId) }
            .filter { it.status == "ACTIVE" }
            .map { TaskAssigneeOption(it.accountId, it.displayName.ifBlank { it.username }) }
    }

    /** Every open occurrence from the earliest active series start to [HORIZON_DAYS] ahead. */
    suspend fun openOccurrences(today: Long): List<TaskOccurrence> {
        val from = database.taskSeries().active(farmId).minOfOrNull { it.startEpochDay } ?: return emptyList()
        return queries.openOccurrences(minOf(from, today), today + HORIZON_DAYS)
    }

    /** How each active series repeats, in words; null for an assigned task that does not repeat. */
    suspend fun repeatLabels(): Map<String, String?> =
        database.taskSeries().active(farmId).associate { it.id to taskRepeatLabel(TaskRepeat.valueOf(it.recurrenceKind), it.recurrenceInterval) }

    /** Active series that repeat, for the Repeating list. */
    suspend fun seriesRows(today: Long, names: Map<String, String>): List<TaskSeriesUiRow> =
        database.taskSeries().active(farmId).mapNotNull { series ->
            val label = taskRepeatLabel(TaskRepeat.valueOf(series.recurrenceKind), series.recurrenceInterval) ?: return@mapNotNull null
            val schedule = TaskSeriesSchedule(TaskSeriesRules.recurrence(series.recurrenceKind, series.recurrenceInterval), series.startEpochDay, series.endEpochDay)
            TaskSeriesUiRow(
                seriesId = series.id,
                title = series.title,
                repeatLabel = label,
                startEpochDay = series.startEpochDay,
                endEpochDay = series.endEpochDay,
                nextEpochDay = TaskRecurrenceSchedule.next(schedule, today - 1),
                assigneeLabel = series.assigneeAccountId?.let { names[it] ?: it },
            )
        }

    suspend fun create(draft: TaskSeriesDraft, context: LocalCommandContext) {
        check(canPlanWork) { "Only supervisors and farm management plan repeating or assigned work" }
        commands.create(
            CreateTaskSeries(
                seriesId = UUID.randomUUID().toString(),
                moduleCode = draft.moduleCode,
                taskCode = draft.taskCode,
                title = draft.title,
                recurrenceKind = draft.repeat.name,
                recurrenceInterval = draft.interval,
                startEpochDay = draft.startDate.toEpochDay(),
                endEpochDay = draft.endDate?.toEpochDay(),
                assignee = TaskAssignee(accountId = draft.assigneeAccountId),
            ),
            context,
        )
    }

    suspend fun edit(draft: TaskEditDraft, context: LocalCommandContext) {
        check(canPlanWork) { "Only supervisors and farm management change repeating or assigned work" }
        val series = requireNotNull(database.taskSeries().get(farmId, draft.seriesId)) { "Task series not found" }
        // Changing this and future from the first occurrence is a change to the whole series.
        val scope = if (draft.scope == TaskEditScope.THIS_AND_FUTURE && draft.occurrenceEpochDay == series.startEpochDay) TaskEditScope.SERIES else draft.scope
        commands.edit(
            EditTaskSeries(
                seriesId = draft.seriesId,
                epochDay = draft.occurrenceEpochDay,
                scope = SeriesEditScope.valueOf(scope.name).name,
                newSeriesId = UUID.randomUUID().toString().takeIf { scope == TaskEditScope.THIS_AND_FUTURE },
                title = draft.title,
                assignee = draft.assignee?.let { TaskAssignee(accountId = it.accountId) },
                movedToEpochDay = draft.movedTo?.toEpochDay(),
                recurrenceKind = draft.repeat?.name,
                recurrenceInterval = draft.interval,
            ),
            context,
        )
    }

    /** Changes an open one-off task (D-020 resolution R2). */
    suspend fun update(draft: TaskUpdateDraft, context: LocalCommandContext) {
        check(canPlanWork) { "Only supervisors and farm management change planned work" }
        commands.update(
            UpdateFarmTask(draft.taskId, draft.title, draft.due?.toEpochDay(), draft.assignee?.let { TaskAssignee(accountId = it.accountId) }),
            context,
        )
    }

    companion object {
        /** Repeating occurrences are listed this many days ahead; the Repeating tab shows each rule in full. */
        const val HORIZON_DAYS = 30L
    }
}
