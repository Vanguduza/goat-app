package com.farmos.app

import com.farmos.core.database.TaskEntity
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.ops.CompleteFarmTask
import com.farmos.domain.ops.CompleteTaskOccurrence
import com.farmos.domain.ops.CreateFarmTask
import com.farmos.domain.ops.TaskOccurrence
import com.farmos.feature.ops.TaskAssigneeOption
import com.farmos.feature.ops.TaskSeriesUiRow
import com.farmos.feature.ops.TaskUiRow
import com.farmos.feature.ops.assigneeKey
import java.time.LocalDate
import java.util.UUID

/** Everything the task board shows, read from the local database. */
internal data class TaskBoardData(
    val rows: List<TaskUiRow> = emptyList(),
    val series: List<TaskSeriesUiRow> = emptyList(),
    val assignees: List<TaskAssigneeOption> = emptyList(),
)

/**
 * One-off tasks, the latest completed tasks and, with [planning], every open occurrence of a repeating
 * or assigned task (D-020). Stored open occurrences come from the series query, never twice.
 */
internal suspend fun loadTaskBoard(ops: RoomOpsRepository, planning: TaskPlanning?, today: Long): TaskBoardData {
    val assignees = planning?.loadAssignees?.invoke().orEmpty()
    val names = assignees.associate { it.key to it.label } + planning?.workerLabels().orEmpty()
    val repeats = planning?.repeatLabels().orEmpty()
    fun label(accountId: String?, workerId: String? = null) = assigneeKey(accountId, workerId)?.let { names[it] ?: workerId ?: it }
    val stored = (ops.openTasks().filter { it.seriesId == null } + ops.completedTasks(100)).map { task -> task.toRow(label(task.assigneeAccountId, task.assigneeWorkerId), task.seriesId?.let { repeats[it] }) }
    val occurrences = planning?.openOccurrences(today).orEmpty().map { it.toRow(label(it.assignee.accountId, it.assignee.workerId), repeats[it.seriesId]) }
    return TaskBoardData(stored + occurrences, planning?.seriesRows(today, names).orEmpty(), assignees)
}

/** Creates a one-off task with no repeat and no assignee through the existing task command. */
internal suspend fun createOneOffTask(ops: RoomOpsRepository, title: String, module: String, code: String, due: String, context: LocalCommandContext) {
    ops.createTask(CreateFarmTask(UUID.randomUUID().toString(), module.trim(), code.trim(), title.trim(), LocalDate.parse(due).toEpochDay()), context)
}

/** Completes a board row: an occurrence of a series through its series, a one-off task directly. */
internal suspend fun completeTaskRow(row: TaskUiRow?, taskId: String, ops: RoomOpsRepository, planning: TaskPlanning?, context: LocalCommandContext) {
    val seriesId = row?.seriesId
    if (row != null && seriesId != null && planning != null) {
        planning.commands.complete(CompleteTaskOccurrence(seriesId, row.occurrenceEpochDay ?: row.dueEpochDay), context)
    } else {
        ops.completeTask(CompleteFarmTask(taskId), context)
    }
}

private fun TaskEntity.toRow(assigneeLabel: String?, repeatLabel: String?) = TaskUiRow(
    id = id,
    title = title,
    moduleCode = moduleCode,
    taskCode = taskCode,
    dueEpochDay = dueOnEpochDay,
    status = status,
    seriesId = seriesId,
    occurrenceEpochDay = occurrenceEpochDay,
    assigneeAccountId = assigneeAccountId,
    assigneeWorkerId = assigneeWorkerId,
    assigneeLabel = assigneeLabel,
    repeatLabel = repeatLabel,
)

private fun TaskOccurrence.toRow(assigneeLabel: String?, repeatLabel: String?) = TaskUiRow(
    id = id,
    title = title,
    moduleCode = moduleCode,
    taskCode = taskCode,
    dueEpochDay = dueEpochDay,
    status = "open",
    seriesId = seriesId,
    occurrenceEpochDay = occurrenceEpochDay,
    assigneeAccountId = assignee.accountId,
    assigneeWorkerId = assignee.workerId,
    assigneeLabel = assigneeLabel,
    repeatLabel = repeatLabel,
)
