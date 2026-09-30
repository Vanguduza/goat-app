package com.farmos.domain.ops

import kotlinx.serialization.Serializable

/** Who a task is assigned to (D-020): a local account, a worker record, or nobody. Never both. */
@Serializable
data class TaskAssignee(val accountId: String? = null, val workerId: String? = null) {
    val isAssigned: Boolean get() = accountId != null || workerId != null
}

/** Creates a task series; a one-off task is a series with [RecurrenceKind.NONE]. Replicated as `task.series_create.v1`. */
@Serializable
data class CreateTaskSeries(
    val seriesId: String,
    val moduleCode: String,
    val taskCode: String,
    val title: String,
    val recurrenceKind: String,
    val recurrenceInterval: Int = 1,
    val startEpochDay: Long,
    val endEpochDay: Long? = null,
    val animalId: String? = null,
    val assignee: TaskAssignee = TaskAssignee(),
)

/** Completes one occurrence; stored as a snapshot under its stable id. Replicated as `task.occurrence_complete.v1`. */
@Serializable
data class CompleteTaskOccurrence(val seriesId: String, val epochDay: Long)

/**
 * Edits a series at one occurrence (D-020). With [SeriesEditScope.THIS] the occurrence alone changes and may
 * move to [movedToEpochDay]; with [SeriesEditScope.THIS_AND_FUTURE] the series ends the day before and
 * [newSeriesId] continues from the occurrence with the edits; with [SeriesEditScope.SERIES] the series itself
 * changes. A null field keeps its value. Completed occurrences are never rewritten. Replicated as
 * `task.series_edit.v1`.
 */
@Serializable
data class EditTaskSeries(
    val seriesId: String,
    val epochDay: Long,
    val scope: String,
    val newSeriesId: String? = null,
    val title: String? = null,
    val assignee: TaskAssignee? = null,
    val movedToEpochDay: Long? = null,
    val recurrenceKind: String? = null,
    val recurrenceInterval: Int? = null,
)

/** Ends a series after [lastEpochDay]; earlier occurrences, completed or not, are kept. Replicated as `task.series_end.v1`. */
@Serializable
data class EndTaskSeries(val seriesId: String, val lastEpochDay: Long)

object TaskSeriesRules {
    fun recurrence(kind: String, interval: Int): TaskRecurrence {
        val parsed = RecurrenceKind.entries.firstOrNull { it.name == kind } ?: throw IllegalArgumentException("Unknown repeat: $kind")
        return TaskRecurrence(parsed, interval)
    }

    fun assignee(assignee: TaskAssignee): String? = when {
        assignee.accountId != null && assignee.workerId != null -> "Assign a task to an account or a worker, not both"
        assignee.accountId?.isBlank() == true || assignee.workerId?.isBlank() == true -> "Choose who the task is assigned to"
        else -> null
    }

    fun create(command: CreateTaskSeries): String? {
        if (command.title.isBlank() || command.taskCode.isBlank() || command.moduleCode.isBlank()) return "Task title and code are required"
        runCatching { TaskSeriesSchedule(recurrence(command.recurrenceKind, command.recurrenceInterval), command.startEpochDay, command.endEpochDay) }
            .onFailure { return it.message ?: "Repeat is not valid" }
        return assignee(command.assignee)
    }

    fun edit(command: EditTaskSeries): String? {
        val scope = SeriesEditScope.entries.firstOrNull { it.name == command.scope } ?: return "Choose what to change: this, this and future, or the series"
        if (command.title?.isBlank() == true) return "Task title is required"
        command.assignee?.let { assignee(it) }?.let { return it }
        if (command.movedToEpochDay != null && scope != SeriesEditScope.THIS) return "Only a single occurrence can be moved"
        val changesRule = command.recurrenceKind != null || command.recurrenceInterval != null
        if (changesRule && scope == SeriesEditScope.THIS) return "A single occurrence cannot change how the task repeats"
        if (scope == SeriesEditScope.THIS_AND_FUTURE && command.newSeriesId.isNullOrBlank()) return "Changing this and future occurrences needs a new series id"
        return null
    }
}

/** One open occurrence of a task series, derived from the series or stored after an edit of that occurrence alone. */
data class TaskOccurrence(
    val id: String,
    val seriesId: String,
    val occurrenceEpochDay: Long,
    val dueEpochDay: Long,
    val moduleCode: String,
    val taskCode: String,
    val title: String,
    val animalId: String?,
    val assignee: TaskAssignee,
    /** True when this occurrence was changed on its own and is stored. */
    val editedAlone: Boolean,
)
