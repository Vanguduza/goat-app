package com.farmos.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.design.FosDimens
import com.farmos.core.model.LocalCommandContext
import com.farmos.feature.ops.EditTaskScreen
import com.farmos.feature.ops.TaskEditDraft
import com.farmos.feature.ops.TaskEntryPage
import com.farmos.feature.ops.TaskSeriesDraft
import com.farmos.feature.ops.TaskUpdateDraft
import com.farmos.feature.ops.TasksBoardScreen

internal data class TaskModuleActions(
    val create: (String, String, String, String) -> Unit,
    val complete: (String) -> Unit,
    val createSeries: (TaskSeriesDraft) -> Unit,
    val endSeries: (String) -> Unit,
    val editSeries: (TaskEditDraft, () -> Unit) -> Unit,
    val updateTask: (TaskUpdateDraft, () -> Unit) -> Unit,
)

/** Owns task route state; every save delegates to the host's governed command adapters. */
@Composable
internal fun TaskModuleNavigator(
    farmId: String,
    board: TaskBoardData,
    completedCount: Int?,
    busy: Boolean,
    error: String?,
    focusTaskId: String?,
    entryPage: TaskEntryPage,
    planning: TaskPlanning?,
    database: FarmOsDatabase?,
    newContext: () -> LocalCommandContext,
    actions: TaskModuleActions,
    onBack: () -> Unit,
) {
    val rows = board.rows
    var selectedId by remember(farmId, focusTaskId) { mutableStateOf(focusTaskId) }
    var editingId by remember(farmId) { mutableStateOf<String?>(null) }
    var originPage by remember(farmId) { mutableStateOf<String?>(null) }
    val editing = editingId?.let { id -> rows.firstOrNull { it.id == id && it.status == "open" } }
    if (editing != null && planning?.canPlanWork == true) {
        EditTaskScreen(
            task = editing, assignees = board.assignees, busy = busy, error = error,
            onSave = { actions.editSeries(it) { editingId = null } },
            onSaveOneOff = { actions.updateTask(it) { editingId = null } },
            onBack = { editingId = null },
        )
        return
    }
    val taskId = selectedId
    if (taskId != null) {
        TaskDetailWorkspace(
            taskId = taskId, task = rows.firstOrNull { it.id == taskId }, assignees = board.assignees,
            busy = busy, error = error, database = database, farmId = farmId, canAssign = planning?.canPlanWork == true,
            onAssign = { change ->
                rows.firstOrNull { it.id == taskId && it.seriesId == null && it.status == "open" }?.let {
                    actions.updateTask(TaskUpdateDraft(it.id, null, null, change)) {}
                }
            },
            onComplete = actions.complete, onEdit = { editingId = taskId }, newContext = newContext,
            onBack = { if (focusTaskId != null) onBack() else selectedId = null },
        )
        return
    }
    when (originPage) {
        "protocol" -> {
            ProtocolGeneratedTasksPage(rows.filter { it.taskCode == "PACK_SLOT" }, { selectedId = it }, { originPage = null })
            return
        }
        "lifecycle" -> {
            LifecycleGeneratedTasksPage(rows.filter { it.taskCode in LIFECYCLE_TASK_CODES }, { selectedId = it }, { originPage = null })
            return
        }
    }
    TasksBoardScreen(
        rows = rows, busy = busy, error = error, onCreate = actions.create, onComplete = actions.complete,
        onOpenDetail = { selectedId = it }, completedCount = completedCount, onBack = onBack, entryPage = entryPage,
        canPlanWork = planning?.canPlanWork == true, currentAccountId = planning?.currentAccountId,
        assignees = board.assignees, series = board.series, onCreateSeries = actions.createSeries,
        onEndSeries = actions.endSeries, repeatHorizonDays = planning?.let { TaskPlanning.HORIZON_DAYS },
        extraActions = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FosDimens.Grid)) {
                androidx.compose.material3.TextButton(onClick = { originPage = "protocol" }, modifier = Modifier.weight(1f)) {
                    androidx.compose.material3.Text("From protocols")
                }
                androidx.compose.material3.TextButton(onClick = { originPage = "lifecycle" }, modifier = Modifier.weight(1f)) {
                    androidx.compose.material3.Text("From lifecycle")
                }
            }
        },
    )
}
