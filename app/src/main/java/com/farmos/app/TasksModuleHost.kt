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
import com.farmos.domain.ops.EndTaskSeries
import com.farmos.feature.ops.EditTaskScreen
import com.farmos.feature.ops.TaskDetailScreen
import com.farmos.feature.ops.TaskEntryPage
import com.farmos.feature.ops.TasksBoardScreen
import java.time.LocalDate
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

    fun complete(taskId: String) = runWrite { completeTaskRow(rows.firstOrNull { it.id == taskId }, taskId, ops, planning, newContext()) }

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
}
