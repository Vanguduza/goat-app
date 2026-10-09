package com.farmos.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.design.runSuspendCatching
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.ops.EndTaskSeries
import com.farmos.feature.ops.TaskEntryPage
import java.time.LocalDate

/** Loads the farm task board and owns command dispatch. Navigation and task evidence have separate surfaces. */
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
    planning: TaskPlanning? = null,
    database: FarmOsDatabase? = null,
) {
    val scope = rememberCoroutineScope()
    var board by remember(farmId) { mutableStateOf(TaskBoardData()) }
    var completedCount by remember(farmId) { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    suspend fun refresh() {
        board = loadTaskBoard(ops, planning, LocalDate.now().toEpochDay())
        completedCount = loadCompletedCount()
    }
    fun runWrite(block: suspend () -> Unit) = launchCommittedModuleWrite(
        scope = scope,
        write = block,
        isBusy = { busy },
        setBusy = { busy = it },
        setError = { error = it },
        enqueueSync = enqueueSync,
        refresh = ::refresh,
    )
    LaunchedEffect(farmId) { runSuspendCatching { refresh() }.onFailure { error = it.message } }
    val actions = TaskModuleActions(
        create = { title, module, code, due -> runWrite { createOneOffTask(ops, title, module, code, due, newContext()) } },
        complete = { id -> runWrite { completeTaskRow(board.rows.firstOrNull { it.id == id }, id, ops, planning, newContext()) } },
        createSeries = { draft -> planning?.let { runWrite { it.create(draft, newContext()) } } },
        endSeries = { id -> planning?.let { runWrite { it.commands.end(EndTaskSeries(id, LocalDate.now().toEpochDay()), newContext()) } } },
        editSeries = { draft, done -> planning?.let { runWrite { it.edit(draft, newContext()); done() } } },
        updateTask = { draft, done -> planning?.let { runWrite { it.update(draft, newContext()); done() } } },
    )
    TaskModuleNavigator(
        farmId, board, completedCount, busy, error, focusTaskId, entryPage, planning,
        database, newContext, actions, onBack,
    )
}
