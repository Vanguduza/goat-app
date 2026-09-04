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
import com.farmos.domain.ops.CreateFarmTask
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
) {
    val scope = rememberCoroutineScope()
    var rows by remember(farmId) { mutableStateOf(emptyList<TaskUiRow>()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        val open = ops.openTasks()
        val completed = ops.completedTasks(100)
        rows = (open + completed).map { task ->
            TaskUiRow(
                id = task.id,
                title = task.title,
                moduleCode = task.moduleCode,
                taskCode = task.taskCode,
                dueEpochDay = task.dueOnEpochDay,
                status = task.status,
            )
        }
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
        onComplete = { taskId ->
            runWrite {
                ops.completeTask(CompleteFarmTask(taskId), newContext())
            }
        },
        onBack = onBack,
    )
}
