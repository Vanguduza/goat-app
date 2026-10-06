package com.farmos.feature.ops

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farmos.core.design.AnimalFarmEmptyState
import com.farmos.core.design.AnimalFarmTheme
import com.farmos.core.design.FarmIllustratedSectionSurface
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalSection
import java.time.LocalDate

/** A local account a task can be assigned to. */
data class TaskAssigneeOption(val accountId: String?, val label: String, val workerId: String? = null) {
    /** Selection key: an account id, or `worker:` and a worker id. */
    val key: String get() = assigneeKey(accountId, workerId) ?: ""
}

/** The selection key of an assignee: an account id, or `worker:` and a worker id; null for nobody. */
fun assigneeKey(accountId: String?, workerId: String?): String? = workerId?.let { "worker:$it" } ?: accountId

/** The assignee change a selection key stands for; null key removes the assignee. */
fun assigneeChange(key: String?): TaskAssigneeChange =
    if (key?.startsWith("worker:") == true) TaskAssigneeChange(null, key.removePrefix("worker:")) else TaskAssigneeChange(key)

/** How a new or edited task repeats (D-020). Mirrors the recurrence kinds of the task contracts. */
enum class TaskRepeat(val label: String, val takesInterval: Boolean = false) {
    NONE("Does not repeat"),
    DAILY("Daily"),
    WEEKDAYS("Weekdays"),
    WEEKLY("Weekly"),
    EVERY_N_DAYS("Every N days", takesInterval = true),
    EVERY_N_WEEKS("Every N weeks", takesInterval = true),
    MONTHLY("Monthly"),
}

/** A repeating or assigned task to create. [endDate] and [assigneeAccountId] are optional. */
data class TaskSeriesDraft(
    val title: String,
    val moduleCode: String,
    val taskCode: String,
    val startDate: LocalDate,
    val repeat: TaskRepeat,
    val interval: Int,
    val endDate: LocalDate?,
    val assigneeAccountId: String?,
    /** A worker record assignee (resolution R1); never together with [assigneeAccountId]. */
    val assigneeWorkerId: String? = null,
)

/** Which occurrences an edit changes. */
enum class TaskEditScope(val label: String) {
    THIS("This occurrence"),
    THIS_AND_FUTURE("This and future occurrences"),
    SERIES("Every occurrence"),
}

/** An edit of one occurrence of a repeating task. Null fields keep their value. */
data class TaskEditDraft(
    val seriesId: String,
    val occurrenceEpochDay: Long,
    val scope: TaskEditScope,
    val title: String?,
    /** Present when the assignee changes; [TaskAssigneeChange.accountId] null removes the assignee. */
    val assignee: TaskAssigneeChange?,
    val movedTo: LocalDate?,
    val repeat: TaskRepeat?,
    val interval: Int?,
)

data class TaskAssigneeChange(val accountId: String?, val workerId: String? = null)

/** An edit of an open one-off task (D-020 resolution R2). Null fields keep their value. */
data class TaskUpdateDraft(val taskId: String, val title: String?, val due: LocalDate?, val assignee: TaskAssigneeChange?)

/** One active repeating task, for the Recurrence list. */
data class TaskSeriesUiRow(
    val seriesId: String,
    val title: String,
    val repeatLabel: String,
    val startEpochDay: Long,
    val endEpochDay: Long?,
    val nextEpochDay: Long?,
    val assigneeLabel: String?,
)

/** How a task repeats, in words; null when it does not repeat. */
fun taskRepeatLabel(repeat: TaskRepeat, interval: Int): String? = when (repeat) {
    TaskRepeat.NONE -> null
    TaskRepeat.EVERY_N_DAYS -> "Every $interval days"
    TaskRepeat.EVERY_N_WEEKS -> "Every $interval weeks"
    else -> repeat.label
}

private fun intervalValid(repeat: TaskRepeat, text: String) = !repeat.takesInterval || text.toIntOrNull()?.let { it in 2..365 } == true

/** Repeat, end date and assignee fields shared by Create Task; only planners see them. */
@Composable
internal fun TaskPlanningFields(
    busy: Boolean,
    assignees: List<TaskAssigneeOption>,
    repeat: TaskRepeat,
    onRepeat: (TaskRepeat) -> Unit,
    interval: String,
    onInterval: (String) -> Unit,
    endDate: String,
    onEndDate: (String) -> Unit,
    assigneeId: String?,
    onAssignee: (String?) -> Unit,
) {
    Text("Repeat", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    ChoiceList(TaskRepeat.entries.map { it to it.label }, repeat, busy, "task-repeat", onRepeat)
    if (repeat.takesInterval) {
        OutlinedTextField(
            interval, onInterval,
            label = { Text(if (repeat == TaskRepeat.EVERY_N_DAYS) "Every how many days (2–365)" else "Every how many weeks (2–365)") },
            isError = !intervalValid(repeat, interval),
            modifier = Modifier.fillMaxWidth().testTag("task-repeat-interval"), enabled = !busy, singleLine = true,
        )
    }
    if (repeat != TaskRepeat.NONE) {
        OutlinedTextField(
            endDate, onEndDate,
            label = { Text("Last day (optional)") }, placeholder = { Text("YYYY-MM-DD") },
            modifier = Modifier.fillMaxWidth().testTag("task-repeat-end"), enabled = !busy, singleLine = true,
        )
    }
    Text("Assign to", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    ChoiceList(listOf<Pair<String?, String>>(null to "Nobody") + assignees.map { it.key to it.label }, assigneeId, busy, "task-assignee", onAssignee)
}

/** Whether the planning fields describe a valid series; [endDate] may be blank. */
internal fun planningValid(repeat: TaskRepeat, interval: String, start: String, endDate: String): Boolean {
    val startDay = runCatching { LocalDate.parse(start) }.getOrNull() ?: return false
    if (!intervalValid(repeat, interval)) return false
    if (repeat == TaskRepeat.NONE || endDate.isBlank()) return true
    val end = runCatching { LocalDate.parse(endDate) }.getOrNull() ?: return false
    return !end.isBefore(startDay)
}

@Composable
private fun <T> ChoiceList(options: List<Pair<T, String>>, selected: T, busy: Boolean, tag: String, onSelect: (T) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            TextButton(
                onClick = { onSelect(value) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().semantics { this.selected = isSelected }.testTag("$tag:${value ?: "none"}"),
            ) { Text(if (isSelected) "$label · selected" else label, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal) }
        }
    }
}

/**
 * FOS-TASK-005 — edit one occurrence of a repeating task (D-020): this occurrence, this and future, or
 * every occurrence. Completed occurrences are never offered here; they stay as they were done.
 */
@Composable
fun EditTaskScreen(
    task: TaskUiRow,
    assignees: List<TaskAssigneeOption>,
    busy: Boolean,
    error: String?,
    onSave: (TaskEditDraft) -> Unit,
    onBack: () -> Unit,
    onSaveOneOff: (TaskUpdateDraft) -> Unit = {},
) {
    val seriesId = task.seriesId
    if (seriesId == null) {
        EditOneOffTask(task, assignees, busy, error, onSaveOneOff, onBack)
        return
    }
    val occurrenceDay = task.occurrenceEpochDay ?: task.dueEpochDay
    var scope by remember { mutableStateOf(TaskEditScope.THIS) }
    var title by remember { mutableStateOf(task.title) }
    var assigneeId by remember { mutableStateOf(assigneeKey(task.assigneeAccountId, task.assigneeWorkerId)) }
    var moveTo by remember { mutableStateOf(LocalDate.ofEpochDay(task.dueEpochDay).toString()) }
    var repeat by remember { mutableStateOf<TaskRepeat?>(null) }
    var interval by remember { mutableStateOf("2") }
    FarmOperationalPage("FOS-TASK-005", "Edit task", "${task.title} · ${LocalDate.ofEpochDay(occurrenceDay)}", onBack = onBack, backLabel = "Task") {
        FarmOperationalSection("Change") {
            ChoiceList(TaskEditScope.entries.map { it to it.label }, scope, busy, "task-edit-scope") { scope = it }
        }
        FarmOperationalSection("Task") {
            OutlinedTextField(title, { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth().testTag("task-edit-title"), enabled = !busy, singleLine = true)
            if (scope == TaskEditScope.THIS) {
                OutlinedTextField(moveTo, { moveTo = it }, label = { Text("Due date") }, placeholder = { Text("YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth().testTag("task-edit-due"), enabled = !busy, singleLine = true)
            } else {
                Text("Repeat", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                ChoiceList(listOf<Pair<TaskRepeat?, String>>(null to "Keep how it repeats") + TaskRepeat.entries.filter { it != TaskRepeat.NONE }.map { it to it.label }, repeat, busy, "task-edit-repeat") { repeat = it }
                if (repeat?.takesInterval == true) {
                    OutlinedTextField(interval, { interval = it }, label = { Text("Interval (2–365)") }, modifier = Modifier.fillMaxWidth().testTag("task-edit-interval"), enabled = !busy, singleLine = true)
                }
            }
            Text("Assign to", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            ChoiceList(listOf<Pair<String?, String>>(null to "Nobody") + assignees.map { it.key to it.label }, assigneeId, busy, "task-edit-assignee") { assigneeId = it }
        }
        val movedTo = runCatching { LocalDate.parse(moveTo) }.getOrNull()
        val chosenRepeat = repeat
        val valid = title.isNotBlank() &&
            (scope != TaskEditScope.THIS || movedTo != null) &&
            (chosenRepeat == null || intervalValid(chosenRepeat, interval))
        Button(
            onClick = {
                onSave(
                    TaskEditDraft(
                        seriesId = seriesId,
                        occurrenceEpochDay = occurrenceDay,
                        scope = scope,
                        title = title.trim().takeIf { it != task.title },
                        assignee = assigneeChange(assigneeId).takeIf { assigneeId != assigneeKey(task.assigneeAccountId, task.assigneeWorkerId) },
                        movedTo = movedTo?.takeIf { scope == TaskEditScope.THIS && it.toEpochDay() != task.dueEpochDay },
                        repeat = chosenRepeat.takeIf { scope != TaskEditScope.THIS },
                        interval = chosenRepeat?.takeIf { it.takesInterval && scope != TaskEditScope.THIS }?.let { interval.toInt() },
                    ),
                )
            },
            enabled = !busy && valid,
            modifier = Modifier.fillMaxWidth().testTag("task-edit-save"),
        ) { Text("Save changes") }
        error?.let { Text(it, color = AnimalFarmTheme.colors.critical) }
    }
}

/** FOS-TASK-005, one-off variant: an open task outside a series changes its title, due day or assignee. */
@Composable
private fun EditOneOffTask(
    task: TaskUiRow,
    assignees: List<TaskAssigneeOption>,
    busy: Boolean,
    error: String?,
    onSave: (TaskUpdateDraft) -> Unit,
    onBack: () -> Unit,
) {
    var title by remember { mutableStateOf(task.title) }
    var due by remember { mutableStateOf(LocalDate.ofEpochDay(task.dueEpochDay).toString()) }
    var assigneeId by remember { mutableStateOf(assigneeKey(task.assigneeAccountId, task.assigneeWorkerId)) }
    FarmOperationalPage("FOS-TASK-005", "Edit task", task.title, onBack = onBack, backLabel = "Task") {
        FarmOperationalSection("Task") {
            OutlinedTextField(title, { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth().testTag("task-edit-title"), enabled = !busy, singleLine = true)
            OutlinedTextField(due, { due = it }, label = { Text("Due date") }, placeholder = { Text("YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth().testTag("task-edit-due"), enabled = !busy, singleLine = true)
            Text("Assign to", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            ChoiceList(listOf<Pair<String?, String>>(null to "Nobody") + assignees.map { it.key to it.label }, assigneeId, busy, "task-edit-assignee") { assigneeId = it }
        }
        val dueDate = runCatching { LocalDate.parse(due) }.getOrNull()
        val changed = title.trim() != task.title || dueDate?.toEpochDay() != task.dueEpochDay || assigneeId != assigneeKey(task.assigneeAccountId, task.assigneeWorkerId)
        Button(
            onClick = {
                onSave(
                    TaskUpdateDraft(
                        taskId = task.id,
                        title = title.trim().takeIf { it != task.title },
                        due = dueDate?.takeIf { it.toEpochDay() != task.dueEpochDay },
                        assignee = assigneeChange(assigneeId).takeIf { assigneeId != assigneeKey(task.assigneeAccountId, task.assigneeWorkerId) },
                    ),
                )
            },
            enabled = !busy && title.isNotBlank() && dueDate != null && changed,
            modifier = Modifier.fillMaxWidth().testTag("task-edit-save"),
        ) { Text("Save changes") }
        error?.let { Text(it, color = AnimalFarmTheme.colors.critical) }
    }
}

/** FOS-TASK-011 — every active repeating task, how it repeats and when it next falls due; planners can end one. */
@Composable
internal fun TaskRecurrenceList(series: List<TaskSeriesUiRow>, canPlanWork: Boolean, busy: Boolean, onEnd: (String) -> Unit) {
    if (series.isEmpty()) {
        AnimalFarmEmptyState("No repeating tasks on this farm.")
        return
    }
    FarmIllustratedSectionSurface {
        series.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider()
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag("task-series:${row.seriesId}"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(row.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    listOfNotNull(
                        row.repeatLabel,
                        "from ${LocalDate.ofEpochDay(row.startEpochDay)}",
                        row.endEpochDay?.let { "until ${LocalDate.ofEpochDay(it)}" },
                    ).joinToString(" · "),
                )
                Text(
                    listOfNotNull(
                        row.nextEpochDay?.let { "Next ${LocalDate.ofEpochDay(it)}" } ?: "No further occurrence",
                        row.assigneeLabel?.let { "Assigned to $it" },
                    ).joinToString(" · "),
                    color = AnimalFarmTheme.colors.mutedInk,
                )
                if (canPlanWork && row.nextEpochDay != null) {
                    TextButton(onClick = { onEnd(row.seriesId) }, enabled = !busy, modifier = Modifier.testTag("task-series-end:${row.seriesId}")) { Text("End after today") }
                }
            }
        }
    }
}
