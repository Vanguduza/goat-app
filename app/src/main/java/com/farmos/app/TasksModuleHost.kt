package com.farmos.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.ops.EndTaskSeries
import com.farmos.feature.ops.EditTaskScreen
import com.farmos.feature.ops.TaskAssigneeChange
import com.farmos.feature.ops.TaskAssigneeOption
import com.farmos.feature.ops.TaskDetailScreen
import com.farmos.feature.ops.TaskEntryPage
import com.farmos.feature.ops.TaskUiRow
import com.farmos.feature.ops.TaskUpdateDraft
import com.farmos.feature.ops.TasksBoardScreen
import com.farmos.feature.ops.assigneeChange
import com.farmos.feature.ops.assigneeKey
import java.time.LocalDate
import kotlinx.coroutines.launch
import com.farmos.core.design.runSuspendCatching

/** Tabs of the per-task workspace. FOS-GROUP-007 group moves are governed by the MoveAnimalGroup command in the Groups module. */
private enum class TaskDetailTab(val label: String) {
    DETAIL("Details"),
    ASSIGN("Assign"),
    ATTACHMENTS("Attachments"),
    EVIDENCE("Evidence"),
}

@Composable
fun TasksModuleHost(
    farmId: String,
    ops: RoomOpsRepository,
    newContext: () -> LocalCommandContext,
    enqueueSync: () -> Unit,
    onBack: () -> Unit,
    focusTaskId: String? = null,
    entryPage: TaskEntryPage = TaskEntryPage.BOARD,
    loadCompletedCount: suspend () -> Int? = { null },
    /** Repeating and assigned tasks (D-020); null keeps the board to one-off tasks. */
    planning: TaskPlanning? = null,
    /** Local database for the attachment read side; null leaves task attachments unwired. */
    database: FarmOsDatabase? = null,
) {
    val scope = rememberCoroutineScope()
    var board by remember(farmId) { mutableStateOf(TaskBoardData()) }
    val rows = board.rows
    var completedCount by remember(farmId) { mutableStateOf<Int?>(null) }
    var selectedId by remember(farmId, focusTaskId) { mutableStateOf(focusTaskId) }
    var editingId by remember(farmId) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val deepEntry = focusTaskId != null

    suspend fun refresh() {
        board = loadTaskBoard(ops, planning, LocalDate.now().toEpochDay())
        completedCount = loadCompletedCount()
    }

    fun runWrite(block: suspend () -> Unit) {
        scope.launch {
            busy = true
            error = null
            runSuspendCatching {
                block()
                refresh()
            }.onSuccess {
                enqueueSync()
            }.onFailure { failure ->
                error = failure.message
            }
            busy = false
        }
    }

    LaunchedEffect(farmId) {
        runSuspendCatching { refresh() }
            .onFailure { error = it.message }
    }

    fun complete(taskId: String) = runWrite { completeTaskRow(rows.firstOrNull { it.id == taskId }, taskId, ops, planning, newContext()) }

    fun assign(taskId: String, change: TaskAssigneeChange?) {
        val target = rows.firstOrNull { it.id == taskId }
        val plan = planning
        if (target != null && plan != null && target.seriesId == null && target.status == "open") {
            runWrite { plan.update(TaskUpdateDraft(target.id, null, null, change), newContext()) }
        }
    }

    val editing = editingId?.let { id -> rows.firstOrNull { it.id == id && it.status == "open" } }
    if (editing != null && planning != null && planning.canPlanWork) {
        EditTaskScreen(
            task = editing,
            assignees = board.assignees,
            busy = busy,
            error = error,
            onSave = { draft -> runWrite { planning.edit(draft, newContext()).also { editingId = null } } },
            onSaveOneOff = { draft -> runWrite { planning.update(draft, newContext()).also { editingId = null } } },
            onBack = { editingId = null },
        )
        return
    }

    if (selectedId != null) {
        val taskId = selectedId
        TaskDetailWorkspace(
            taskId = taskId,
            task = rows.firstOrNull { it.id == taskId },
            assignees = board.assignees,
            busy = busy,
            error = error,
            database = database,
            farmId = farmId,
            canAssign = planning?.canPlanWork == true,
            onAssign = { change -> assign(taskId, change) },
            onComplete = ::complete,
            onEdit = { editingId = taskId },
            newContext = newContext,
            onBack = { if (deepEntry) onBack() else selectedId = null },
        )
        return
    }

    var originPage by remember(farmId) { mutableStateOf<String?>(null) }
    when (originPage) {
        "protocol" -> {
            ProtocolGeneratedTasksPage(
                rows = rows.filter { it.taskCode == "PACK_SLOT" },
                onOpenDetail = { selectedId = it; originPage = null },
                onBack = { originPage = null },
            )
            return
        }
        "lifecycle" -> {
            LifecycleGeneratedTasksPage(
                rows = rows.filter { it.taskCode in LIFECYCLE_TASK_CODES },
                onOpenDetail = { selectedId = it; originPage = null },
                onBack = { originPage = null },
            )
            return
        }
    }

    TasksBoardScreen(
        rows = rows,
        busy = busy,
        error = error,
        onCreate = { title, module, code, due -> runWrite { createOneOffTask(ops, title, module, code, due, newContext()) } },
        onComplete = ::complete,
        onOpenDetail = { selectedId = it },
        completedCount = completedCount,
        onBack = onBack,
        entryPage = entryPage,
        canPlanWork = planning?.canPlanWork == true,
        currentAccountId = planning?.currentAccountId,
        assignees = board.assignees,
        series = board.series,
        onCreateSeries = { draft -> planning?.let { runWrite { it.create(draft, newContext()) } } },
        onEndSeries = { seriesId -> planning?.let { runWrite { it.commands.end(EndTaskSeries(seriesId, LocalDate.now().toEpochDay()), newContext()) } } },
        repeatHorizonDays = planning?.let { TaskPlanning.HORIZON_DAYS },
    )
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        androidx.compose.material3.TextButton(
            onClick = { originPage = "protocol" },
            modifier = Modifier.weight(1f),
        ) {
            androidx.compose.material3.Text("From protocols")
        }
        androidx.compose.material3.TextButton(
            onClick = { originPage = "lifecycle" },
            modifier = Modifier.weight(1f),
        ) {
            androidx.compose.material3.Text("From lifecycle")
        }
    }
}

/** Task codes written by lifecycle event handlers (poultry hatch cycle, rabbit red flags). */
private val LIFECYCLE_TASK_CODES = setOf("CANDLING", "LOCKDOWN", "EXPECTED_HATCH", "BIOSECURITY", "FLOCK_VAX", "GI_STASIS")

/**
 * FOS-TASK-014 — Task Generated by Protocol: tasks created when a vet-accepted protocol pack is
 * applied. The task id carries the pack-apply id, so each row links back to its protocol slot.
 */
@Composable
private fun ProtocolGeneratedTasksPage(
    rows: List<TaskUiRow>,
    onOpenDetail: (String) -> Unit,
    onBack: () -> Unit,
) {
    FarmOperationalPage(
        screenId = "FOS-TASK-014",
        title = "Tasks from protocols",
        subtitle = "Created by applying vet-accepted protocol packs",
        onBack = onBack,
    ) {
        if (rows.isEmpty()) {
            androidx.compose.material3.Text("No protocol tasks on this device. Apply a protocol pack to generate its task slots.")
        } else {
            FarmOperationalSection("Protocol tasks") {
                rows.forEach { row ->
                    androidx.compose.material3.TextButton(
                        onClick = { onOpenDetail(row.id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        androidx.compose.material3.Text("${row.title} · due ${LocalDate.ofEpochDay(row.dueEpochDay)} · ${row.status}")
                    }
                }
            }
        }
    }
}

/**
 * FOS-TASK-015 — Task Generated by Lifecycle: tasks created by lifecycle events such as the
 * poultry hatch cycle (candling, lockdown, expected hatch) or a rabbit GI-stasis red flag.
 */
@Composable
private fun LifecycleGeneratedTasksPage(
    rows: List<TaskUiRow>,
    onOpenDetail: (String) -> Unit,
    onBack: () -> Unit,
) {
    FarmOperationalPage(
        screenId = "FOS-TASK-015",
        title = "Tasks from lifecycle",
        subtitle = "Created by lifecycle events on this farm",
        onBack = onBack,
    ) {
        if (rows.isEmpty()) {
            androidx.compose.material3.Text("No lifecycle tasks on this device. Hatch cycles and red-flag events generate their tasks here.")
        } else {
            FarmOperationalSection("Lifecycle tasks") {
                rows.forEach { row ->
                    androidx.compose.material3.TextButton(
                        onClick = { onOpenDetail(row.id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        androidx.compose.material3.Text("${row.title} · ${row.taskCode} · due ${LocalDate.ofEpochDay(row.dueEpochDay)} · ${row.status}")
                    }
                }
            }
        }
    }
}

/**
 * One task with tabs for its detail, worker assignment (FOS-LABOUR-004), attachments
 * (FOS-TASK-012) and completion evidence (FOS-TASK-013).
 */
@Composable
private fun TaskDetailWorkspace(
    taskId: String,
    task: TaskUiRow?,
    assignees: List<TaskAssigneeOption>,
    busy: Boolean,
    error: String?,
    database: FarmOsDatabase?,
    farmId: String,
    canAssign: Boolean,
    onAssign: (TaskAssigneeChange?) -> Unit,
    onComplete: (String) -> Unit,
    onEdit: () -> Unit,
    newContext: () -> LocalCommandContext,
    onBack: () -> Unit,
) {
    var tab by remember(taskId) { mutableStateOf(TaskDetailTab.DETAIL) }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TaskDetailTab.entries.forEach { entry ->
                androidx.compose.material3.TextButton(
                    onClick = { tab = entry },
                    enabled = tab != entry,
                ) {
                    androidx.compose.material3.Text(entry.label)
                }
            }
        }
        when (tab) {
            TaskDetailTab.DETAIL -> TaskDetailScreen(
                task = task,
                busy = busy,
                error = error,
                onComplete = onComplete,
                onBack = onBack,
                canPlanWork = canAssign,
                onEdit = onEdit,
            )
            TaskDetailTab.ASSIGN -> TaskAssignPage(
                task = task,
                assignees = assignees,
                canAssign = canAssign,
                busy = busy,
                error = error,
                onAssign = onAssign,
                onBack = onBack,
            )
            TaskDetailTab.ATTACHMENTS -> TaskAttachmentsPage(
                database = database,
                farmId = farmId,
                task = task,
                newContext = newContext,
                onBack = onBack,
            )
            TaskDetailTab.EVIDENCE -> TaskEvidencePage(
                task = task,
                database = database,
                farmId = farmId,
                newContext = newContext,
                onBack = onBack,
            )
        }
    }
}

/**
 * FOS-LABOUR-004 — Worker Assignment: the account or worker an open one-off task is assigned to.
 * Changes go through UpdateFarmTask (task.update.v1); only supervisors and farm management plan work.
 */
@Composable
private fun TaskAssignPage(
    task: TaskUiRow?,
    assignees: List<TaskAssigneeOption>,
    canAssign: Boolean,
    busy: Boolean,
    error: String?,
    onAssign: (TaskAssigneeChange?) -> Unit,
    onBack: () -> Unit,
) {
    val currentKey = assigneeKey(task?.assigneeAccountId, task?.assigneeWorkerId)
    var selectedKey by remember(task?.id) { mutableStateOf(currentKey) }
    FarmOperationalPage(
        screenId = "FOS-LABOUR-004",
        title = "Worker assignment",
        subtitle = task?.title ?: "Task",
        onBack = onBack,
    ) {
        if (task == null) {
            androidx.compose.material3.Text("The task is not on this device.")
            return@FarmOperationalPage
        }
        FarmOperationalSection("Current assignment") {
            androidx.compose.material3.Text(task.assigneeLabel ?: "Unassigned")
            androidx.compose.material3.Text("Status: ${task.status} · due ${LocalDate.ofEpochDay(task.dueEpochDay)}")
        }
        when {
            task.seriesId != null ->
                androidx.compose.material3.Text("A repeating task is assigned through its series.")
            task.status != "open" ->
                androidx.compose.material3.Text("A completed task keeps the assignment it was done with.")
            !canAssign ->
                androidx.compose.material3.Text("Only supervisors and farm management assign work.")
            else -> FarmOperationalSection(
                title = "Assign to",
                description = "Choosing a name and saving writes the assignment to this device and the farm.",
            ) {
                androidx.compose.material3.TextButton(
                    onClick = { selectedKey = null },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    androidx.compose.material3.Text(if (selectedKey == null) "Nobody (current)" else "Nobody")
                }
                assignees.forEach { option ->
                    androidx.compose.material3.TextButton(
                        onClick = { selectedKey = option.key },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        androidx.compose.material3.Text(option.label + if (option.key == selectedKey) " (current)" else "")
                    }
                }
                androidx.compose.material3.Button(
                    onClick = { onAssign(assigneeChange(selectedKey)) },
                    enabled = !busy && selectedKey != currentKey,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    androidx.compose.material3.Text("Save assignment")
                }
                error?.let { androidx.compose.material3.Text(it) }
            }
        }
    }
}

/**
 * FOS-TASK-012 — Task Attachment: the files kept against one task. New photos and PDFs are added
 * through the governed AttachTaskAttachment command; a completed task keeps the files it was finished with.
 */
@Composable
private fun TaskAttachmentsPage(
    database: FarmOsDatabase?,
    farmId: String,
    task: TaskUiRow?,
    newContext: () -> LocalCommandContext,
    onBack: () -> Unit,
) {
    FarmOperationalPage(
        screenId = "FOS-TASK-012",
        title = "Task attachments",
        subtitle = task?.title ?: "Task",
        onBack = onBack,
    ) {
        if (task == null) {
            androidx.compose.material3.Text("The task is not on this device.")
        } else if (database == null) {
            androidx.compose.material3.Text("Attachments are unavailable: this task view is not wired to the database.")
        } else {
            TaskAttachmentsHost(
                database = database,
                farmId = farmId,
                taskId = task.id,
                canAttach = task.status == "open",
                newContext = newContext,
            )
            if (task.status != "open") {
                androidx.compose.material3.Text("A completed task keeps the files it was finished with.")
            }
        }
    }
}

/**
 * FOS-TASK-013 — Task Completion Evidence: whether the task is done, when it was due, and the
 * files kept as evidence against it.
 */
@Composable
private fun TaskEvidencePage(
    task: TaskUiRow?,
    database: FarmOsDatabase?,
    farmId: String,
    newContext: () -> LocalCommandContext,
    onBack: () -> Unit,
) {
    val today = LocalDate.now().toEpochDay()
    FarmOperationalPage(
        screenId = "FOS-TASK-013",
        title = "Completion evidence",
        subtitle = task?.title ?: "Task",
        onBack = onBack,
    ) {
        if (task == null) {
            androidx.compose.material3.Text("The task is not on this device.")
            return@FarmOperationalPage
        }
        FarmOperationalSection("Completion state") {
            val state = when {
                task.status == "done" -> "Completed"
                task.dueEpochDay < today -> "Open · overdue since ${LocalDate.ofEpochDay(task.dueEpochDay)}"
                else -> "Open · due ${LocalDate.ofEpochDay(task.dueEpochDay)}"
            }
            androidx.compose.material3.Text(state)
            androidx.compose.material3.Text("${task.moduleCode} · ${task.taskCode}")
            task.assigneeLabel?.let { androidx.compose.material3.Text("Assigned to $it") }
        }
        FarmOperationalSection("Evidence files") {
            if (database == null) {
                androidx.compose.material3.Text("Attachments are unavailable: this task view is not wired to the database.")
            } else {
                // Evidence lists the same files; new files are added from the Attachments tab.
                TaskAttachmentsHost(database = database, farmId = farmId, taskId = task.id, canAttach = false, newContext = newContext)
            }
        }
    }
}
