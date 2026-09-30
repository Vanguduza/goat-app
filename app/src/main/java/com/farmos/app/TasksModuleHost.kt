package com.farmos.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.ops.CompleteFarmTask
import com.farmos.domain.ops.CompleteTaskOccurrence
import com.farmos.domain.ops.CreateFarmTask
import com.farmos.domain.ops.EndTaskSeries
import com.farmos.feature.ops.EditTaskScreen
import com.farmos.feature.ops.TaskAssigneeOption
import com.farmos.feature.ops.TaskSeriesUiRow
import com.farmos.feature.ops.TaskDetailScreen
import com.farmos.feature.ops.TaskEntryPage
import com.farmos.feature.ops.TaskUiRow
import com.farmos.feature.ops.TasksBoardScreen
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.launch

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
) {
    val scope = rememberCoroutineScope()
    var rows by remember(farmId) { mutableStateOf(emptyList<TaskUiRow>()) }
    var completedCount by remember(farmId) { mutableStateOf<Int?>(null) }
    var selectedId by remember(farmId, focusTaskId) { mutableStateOf(focusTaskId) }
    var editingId by remember(farmId) { mutableStateOf<String?>(null) }
    var series by remember(farmId) { mutableStateOf(emptyList<TaskSeriesUiRow>()) }
    var assignees by remember(farmId) { mutableStateOf(emptyList<TaskAssigneeOption>()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val deepEntry = focusTaskId != null

    suspend fun refresh() {
        val today = LocalDate.now().toEpochDay()
        assignees = planning?.loadAssignees?.invoke().orEmpty()
        val names = assignees.associate { it.accountId to it.label }
        val repeats = planning?.repeatLabels().orEmpty()
        // Stored open occurrences of a repeating task come from the series query, not twice.
        val open = ops.openTasks().filter { it.seriesId == null }
        val completed = ops.completedTasks(100)
        val occurrences = planning?.openOccurrences(today)?.map { occurrence ->
            TaskUiRow(
                id = occurrence.id,
                title = occurrence.title,
                moduleCode = occurrence.moduleCode,
                taskCode = occurrence.taskCode,
                dueEpochDay = occurrence.dueEpochDay,
                status = "open",
                seriesId = occurrence.seriesId,
                occurrenceEpochDay = occurrence.occurrenceEpochDay,
                assigneeAccountId = occurrence.assignee.accountId,
                assigneeLabel = occurrence.assignee.accountId?.let { names[it] ?: it },
                repeatLabel = repeats[occurrence.seriesId],
            )
        }.orEmpty()
        rows = (open + completed).map { task ->
            TaskUiRow(
                id = task.id,
                title = task.title,
                moduleCode = task.moduleCode,
                taskCode = task.taskCode,
                dueEpochDay = task.dueOnEpochDay,
                status = task.status,
                seriesId = task.seriesId,
                occurrenceEpochDay = task.occurrenceEpochDay,
                assigneeAccountId = task.assigneeAccountId,
                assigneeLabel = task.assigneeAccountId?.let { names[it] ?: it },
                repeatLabel = task.seriesId?.let { repeats[it] },
            )
        } + occurrences
        series = planning?.seriesRows(today, names).orEmpty()
        completedCount = loadCompletedCount()
    }

    fun runWrite(block: suspend () -> Unit) {
        scope.launch {
            busy = true
            error = null
            runCatching {
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
        runCatching { refresh() }
            .onFailure { error = it.message }
    }

    fun complete(taskId: String) {
        val row = rows.firstOrNull { it.id == taskId }
        val seriesId = row?.seriesId
        if (row != null && seriesId != null && planning != null) {
            runWrite { planning.commands.complete(CompleteTaskOccurrence(seriesId, row.occurrenceEpochDay ?: row.dueEpochDay), newContext()) }
        } else {
            runWrite { ops.completeTask(CompleteFarmTask(taskId), newContext()) }
        }
    }

    val editing = editingId?.let { id -> rows.firstOrNull { it.id == id && it.seriesId != null && it.status == "open" } }
    if (editing != null && planning != null && planning.canPlanWork) {
        EditTaskScreen(
            task = editing,
            assignees = assignees,
            busy = busy,
            error = error,
            onSave = { draft ->
                runWrite {
                    planning.edit(draft, newContext())
                    editingId = null
                }
            },
            onBack = { editingId = null },
        )
        return
    }

    if (selectedId != null) {
        TaskDetailScreen(
            task = rows.firstOrNull { it.id == selectedId },
            busy = busy,
            error = error,
            onComplete = ::complete,
            onBack = { if (deepEntry) onBack() else selectedId = null },
            canPlanWork = planning?.canPlanWork == true,
            onEdit = { editingId = selectedId },
        )
        return
    }

    TasksBoardScreen(
        rows = rows,
        busy = busy,
        error = error,
        onCreate = { title, module, code, due ->
            runWrite {
                ops.createTask(
                    CreateFarmTask(
                        taskId = UUID.randomUUID().toString(),
                        moduleCode = module.trim(),
                        taskCode = code.trim(),
                        title = title.trim(),
                        dueEpochDay = LocalDate.parse(due).toEpochDay(),
                    ),
                    newContext(),
                )
            }
        },
        onComplete = ::complete,
        onOpenDetail = { selectedId = it },
        completedCount = completedCount,
        onBack = onBack,
        entryPage = entryPage,
        canPlanWork = planning?.canPlanWork == true,
        currentAccountId = planning?.currentAccountId,
        assignees = assignees,
        series = series,
        onCreateSeries = { draft -> planning?.let { runWrite { it.create(draft, newContext()) } } },
        onEndSeries = { seriesId -> planning?.let { runWrite { it.commands.end(EndTaskSeries(seriesId, LocalDate.now().toEpochDay()), newContext()) } } },
        repeatHorizonDays = planning?.let { TaskPlanning.HORIZON_DAYS },
    )
}
