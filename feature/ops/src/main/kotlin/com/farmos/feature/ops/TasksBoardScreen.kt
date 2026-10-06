package com.farmos.feature.ops

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farmos.core.design.AnimalFarmCanvas
import com.farmos.core.design.AnimalFarmModuleHeader
import com.farmos.core.design.AnimalFarmTheme
import com.farmos.core.design.FarmIllustratedSectionSurface
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FosDimens
import java.time.LocalDate

/** UI projection only; authoritative task state remains in Room/Supabase. */
data class TaskUiRow(
    val id: String,
    val title: String,
    val moduleCode: String,
    val taskCode: String,
    val dueEpochDay: Long,
    val status: String,
    /** The repeating task this occurrence belongs to (D-020), or null for a one-off task. */
    val seriesId: String? = null,
    /** The series day this occurrence stands for; its due day may have been moved. */
    val occurrenceEpochDay: Long? = null,
    val assigneeAccountId: String? = null,
    /** A worker record assignee (resolution R1). */
    val assigneeWorkerId: String? = null,
    val assigneeLabel: String? = null,
    /** How the task repeats, in words; null for a task that does not repeat. */
    val repeatLabel: String? = null,
)

private enum class TaskTab { TODAY, SCHEDULED, OVERDUE, COMPLETED, ALL, CALENDAR, MINE, REPEATING }

/**
 * FOS-TASK-001 / 002 / 003 / 004 — shared Farm OS task reference family. [rows] hold every open
 * task and may hold only the latest completed ones; [completedCount] is the exhaustive count.
 */
@Composable
fun TasksBoardScreen(
    rows: List<TaskUiRow>,
    busy: Boolean,
    error: String?,
    onCreate: (title: String, module: String, code: String, due: String) -> Unit,
    onComplete: (taskId: String) -> Unit,
    onBack: () -> Unit,
    onOpenDetail: (taskId: String) -> Unit = {},
    entryPage: TaskEntryPage = TaskEntryPage.BOARD,
    completedCount: Int? = null,
    /** Supervisors and management plan repeating and assigned work (D-020); everyone completes it. */
    canPlanWork: Boolean = false,
    currentAccountId: String? = null,
    assignees: List<TaskAssigneeOption> = emptyList(),
    series: List<TaskSeriesUiRow> = emptyList(),
    onCreateSeries: (TaskSeriesDraft) -> Unit = {},
    onEndSeries: (seriesId: String) -> Unit = {},
    /** How far ahead repeating occurrences are listed; null when the board has no repeating tasks. */
    repeatHorizonDays: Long? = null,
) {
    if (entryPage == TaskEntryPage.CREATE) {
        CreateTaskScreen(busy = busy, error = error, onCreate = onCreate, onBack = onBack, canPlanWork = canPlanWork, assignees = assignees, onCreateSeries = onCreateSeries)
        return
    }
    var tab by remember { mutableStateOf(TaskTab.TODAY) }
    var showCreate by remember { mutableStateOf(false) }
    val today = LocalDate.now().toEpochDay()
    val visible = when (tab) {
        TaskTab.TODAY -> rows.filter { it.status == "open" && it.dueEpochDay == today }
        TaskTab.SCHEDULED -> rows.filter { it.status == "open" && it.dueEpochDay > today }
        TaskTab.OVERDUE -> rows.filter { it.status == "open" && it.dueEpochDay < today }
        TaskTab.COMPLETED -> rows.filter { it.status == "done" }
        TaskTab.ALL -> rows
        TaskTab.CALENDAR -> rows.filter { it.status == "open" }.sortedWith(compareBy<TaskUiRow> { it.dueEpochDay }.thenBy { it.title }.thenBy { it.id })
        TaskTab.MINE -> rows.filter { it.status == "open" && currentAccountId != null && it.assigneeAccountId == currentAccountId }
        TaskTab.REPEATING -> emptyList()
    }
    val completedShown = rows.count { it.status == "done" }

    AnimalFarmCanvas {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(FosDimens.IntraCardGap),
        ) {
            AnimalFarmModuleHeader(
                title = "Tasks",
                subtitle = "${rows.count { it.status == "open" }} open task(s)",
            )

            TaskTab.entries.chunked(3).forEach { tabs ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    tabs.forEach { option ->
                        TextButton(onClick = { tab = option }, modifier = Modifier.weight(1f)) {
                            Text(taskTabLabel(option, selected = tab == option))
                        }
                    }
                    repeat(3 - tabs.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            Column(
                Modifier.fillMaxWidth().testTag("farm-screen:${taskTabScreenId(tab)}"),
                verticalArrangement = Arrangement.spacedBy(FosDimens.IntraCardGap),
            ) {
                if (repeatHorizonDays != null && series.isNotEmpty() && tab in setOf(TaskTab.SCHEDULED, TaskTab.ALL, TaskTab.CALENDAR)) {
                    Text(
                        "Repeating tasks are listed $repeatHorizonDays days ahead; see Repeating for each rule.",
                        color = AnimalFarmTheme.colors.mutedInk,
                        modifier = Modifier.testTag("task-repeat-horizon"),
                    )
                }
                if ((tab == TaskTab.COMPLETED || tab == TaskTab.ALL) && completedCount != null && completedShown < completedCount) {
                    Text(
                        "Completed tasks · latest $completedShown of $completedCount",
                        color = AnimalFarmTheme.colors.mutedInk,
                        modifier = Modifier.testTag("task-completed-bound"),
                    )
                }
                if (tab == TaskTab.REPEATING) {
                    TaskRecurrenceList(series, canPlanWork, busy, onEndSeries)
                } else if (visible.isEmpty()) {
                    FarmIllustratedSectionSurface {
                        Text(emptyTaskMessage(tab), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(emptyTaskHint(tab), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else if (tab == TaskTab.CALENDAR) {
                    visible.groupBy { it.dueEpochDay }.forEach { (day, tasks) ->
                        Text(
                            calendarDayLabel(day, today, tasks.size),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.testTag("task-calendar-day:$day"),
                        )
                        tasks.forEach { task ->
                            TaskCard(task = task, today = today, busy = busy, onComplete = onComplete, onOpen = { onOpenDetail(task.id) })
                        }
                    }
                } else {
                    visible.forEach { task ->
                        TaskCard(
                            task = task,
                            today = today,
                            busy = busy,
                            onComplete = onComplete,
                            onOpen = { onOpenDetail(task.id) },
                        )
                    }
                }
            }

            FarmIllustratedSectionSurface {
                Text("Quick action", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Create a farm task. Species-specific capture stays inside its native module.")
                Button(onClick = { showCreate = !showCreate }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (showCreate) "Close task form" else "Add task")
                }
            }

            if (showCreate) {
                CreateTaskCard(busy = busy, onCreate = onCreate, canPlanWork = canPlanWork, assignees = assignees, onCreateSeries = onCreateSeries) { showCreate = false }
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = onBack, enabled = !busy) { Text("Farm home") }
        }
    }
}

@Composable
private fun TaskCard(
    task: TaskUiRow,
    today: Long,
    busy: Boolean,
    onComplete: (String) -> Unit,
    onOpen: () -> Unit,
) {
    val overdue = task.status == "open" && task.dueEpochDay < today
    FarmIllustratedSectionSurface(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(task.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    listOfNotNull(
                        "${task.moduleCode} · ${task.taskCode}",
                        task.repeatLabel,
                        task.assigneeLabel?.let { "Assigned to $it" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    when {
                        task.status == "done" -> "Completed"
                        overdue -> "Overdue · ${LocalDate.ofEpochDay(task.dueEpochDay)}"
                        task.dueEpochDay == today -> "Due today"
                        else -> "Due ${LocalDate.ofEpochDay(task.dueEpochDay)}"
                    },
                    color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            if (task.status == "open") {
                TextButton(onClick = { onComplete(task.id) }, enabled = !busy) { Text("Done") }
            }
        }
    }
}

@Composable
fun CreateTaskScreen(
    busy: Boolean,
    error: String?,
    onCreate: (title: String, module: String, code: String, due: String) -> Unit,
    onBack: () -> Unit,
    canPlanWork: Boolean = false,
    assignees: List<TaskAssigneeOption> = emptyList(),
    onCreateSeries: (TaskSeriesDraft) -> Unit = {},
) {
    FarmOperationalPage(
        screenId = "FOS-TASK-004",
        title = "Add task",
        subtitle = "Create a farm task. Species-specific capture stays inside its native module.",
        onBack = onBack,
    ) {
        CreateTaskCard(busy = busy, onCreate = onCreate, canPlanWork = canPlanWork, assignees = assignees, onCreateSeries = onCreateSeries) {}
        error?.let { Text(it, color = AnimalFarmTheme.colors.critical) }
    }
}

@Composable
private fun CreateTaskCard(
    busy: Boolean,
    onCreate: (title: String, module: String, code: String, due: String) -> Unit,
    canPlanWork: Boolean,
    assignees: List<TaskAssigneeOption>,
    onCreateSeries: (TaskSeriesDraft) -> Unit,
    onCreated: () -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var module by remember { mutableStateOf("goat") }
    var code by remember { mutableStateOf("CHECK") }
    var due by remember { mutableStateOf(LocalDate.now().toString()) }
    var repeat by remember { mutableStateOf(TaskRepeat.NONE) }
    var interval by remember { mutableStateOf("2") }
    var endDate by remember { mutableStateOf("") }
    var assigneeId by remember { mutableStateOf<String?>(null) }
    FarmIllustratedSectionSurface {
        Text("Add task", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        OutlinedTextField(title, { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        OutlinedTextField(module, { module = it }, label = { Text("Module") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        OutlinedTextField(code, { code = it }, label = { Text("Task code") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        OutlinedTextField(due, { due = it }, label = { Text(if (repeat == TaskRepeat.NONE) "Due date" else "First day") }, placeholder = { Text("YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        if (canPlanWork) {
            TaskPlanningFields(busy, assignees, repeat, { repeat = it }, interval, { interval = it }, endDate, { endDate = it }, assigneeId, { assigneeId = it })
        }
        Button(
            onClick = {
                if (repeat == TaskRepeat.NONE && assigneeId == null) {
                    onCreate(title, module, code, due)
                } else {
                    onCreateSeries(
                        TaskSeriesDraft(
                            title = title.trim(),
                            moduleCode = module.trim(),
                            taskCode = code.trim(),
                            startDate = LocalDate.parse(due),
                            repeat = repeat,
                            interval = if (repeat.takesInterval) interval.toInt() else 1,
                            endDate = endDate.takeIf { repeat != TaskRepeat.NONE && it.isNotBlank() }?.let { LocalDate.parse(it) },
                            assigneeAccountId = assigneeChange(assigneeId).accountId,
                            assigneeWorkerId = assigneeChange(assigneeId).workerId,
                        ),
                    )
                }
                onCreated()
            },
            enabled = !busy && title.isNotBlank() && module.isNotBlank() && code.isNotBlank() && planningValid(repeat, interval, due, endDate),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Create task") }
    }
}

private fun taskTabScreenId(tab: TaskTab): String = when (tab) {
    TaskTab.TODAY -> "FOS-TASK-001"
    TaskTab.SCHEDULED -> "FOS-TASK-007"
    TaskTab.OVERDUE -> "FOS-TASK-008"
    TaskTab.COMPLETED -> "FOS-TASK-009"
    TaskTab.ALL -> "FOS-TASK-002"
    TaskTab.CALENDAR -> "FOS-TASK-010"
    TaskTab.MINE -> "FOS-TASK-006"
    TaskTab.REPEATING -> "FOS-TASK-011"
}

/** Calendar day heading: the ISO date, whether it is today or past, and its open task count. */
private fun calendarDayLabel(day: Long, today: Long, count: Int): String {
    val marker = when {
        day == today -> " · Today"
        day < today -> " · Overdue"
        else -> ""
    }
    return "${LocalDate.ofEpochDay(day)}$marker · $count open " + if (count == 1) "task" else "tasks"
}

private fun taskTabLabel(tab: TaskTab, selected: Boolean): String {
    val label = when (tab) {
        TaskTab.TODAY -> "Today"
        TaskTab.SCHEDULED -> "Scheduled"
        TaskTab.OVERDUE -> "Overdue"
        TaskTab.COMPLETED -> "Completed"
        TaskTab.ALL -> "All"
        TaskTab.CALENDAR -> "Calendar"
        TaskTab.MINE -> "Assigned to me"
        TaskTab.REPEATING -> "Repeating"
    }
    return if (selected) "$label · selected" else label
}

private fun emptyTaskMessage(tab: TaskTab): String = when (tab) {
    TaskTab.TODAY -> "No tasks due today"
    TaskTab.SCHEDULED -> "No scheduled tasks"
    TaskTab.OVERDUE -> "No overdue tasks"
    TaskTab.COMPLETED -> "No completed tasks yet"
    TaskTab.ALL -> "No tasks on this device"
    TaskTab.CALENDAR -> "No open tasks to schedule"
    TaskTab.MINE -> "Nothing assigned to you"
    TaskTab.REPEATING -> "No repeating tasks"
}

private fun emptyTaskHint(tab: TaskTab): String = when (tab) {
    TaskTab.TODAY -> "Your open work due today will appear here."
    TaskTab.SCHEDULED -> "Future scheduled farm work will appear here."
    TaskTab.OVERDUE -> "Open work past its due date will appear here."
    TaskTab.COMPLETED -> "Finished work remains visible for farm history."
    TaskTab.ALL -> "Open and completed farm work will appear here."
    TaskTab.CALENDAR -> "Open farm work appears here under its due date."
    TaskTab.MINE -> "Open work assigned to your account will appear here."
    TaskTab.REPEATING -> "Repeating farm work will appear here."
}

/**
 * FOS-TASK-003 — task detail. Anyone may complete an open task; a planner may also edit it (FOS-TASK-005):
 * a repeating task by occurrence scope, a one-off task directly. A completed task is never edited.
 */
@Composable
fun TaskDetailScreen(
    task: TaskUiRow?,
    busy: Boolean,
    error: String?,
    onComplete: (String) -> Unit,
    onBack: () -> Unit,
    canPlanWork: Boolean = false,
    onEdit: () -> Unit = {},
) {
    val today = LocalDate.now().toEpochDay()
    FarmOperationalPage(
        screenId = "FOS-TASK-003",
        title = task?.title ?: "Task",
        subtitle = task?.let { "${it.moduleCode} · ${it.taskCode}" } ?: "That task is not on this device.",
        onBack = onBack,
    ) {
        if (task == null) {
            Text("The selected task is not in the local records.")
        } else {
            val overdue = task.status == "open" && task.dueEpochDay < today
            Text(
                when {
                    task.status == "done" -> "Completed"
                    overdue -> "Overdue · ${LocalDate.ofEpochDay(task.dueEpochDay)}"
                    task.dueEpochDay == today -> "Due today"
                    else -> "Due ${LocalDate.ofEpochDay(task.dueEpochDay)}"
                },
                color = if (overdue) AnimalFarmTheme.colors.critical else AnimalFarmTheme.colors.mutedInk,
            )
            task.repeatLabel?.let { Text("Repeats: $it", modifier = Modifier.testTag("task-detail-repeating")) }
            task.assigneeLabel?.let { Text("Assigned to $it") }
            if (task.status == "open") {
                Button(
                    onClick = { onComplete(task.id) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Mark done") }
                if (canPlanWork) {
                    TextButton(onClick = onEdit, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("task-detail-edit")) { Text("Edit") }
                }
            }
        }
        error?.let { Text(it, color = AnimalFarmTheme.colors.critical) }
    }
}
