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
    subjectAnimalId: String? = null,
) {
    val scope = rememberCoroutineScope()
    var board by remember(farmId,subjectAnimalId) { mutableStateOf(TaskBoardData()) }
    var savedRevision by remember(farmId) { mutableStateOf(0) }
    var completedCount by remember(farmId) { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    suspend fun refresh() {
        subjectAnimalId?.let { requireNotNull(database?.animals()?.get(farmId,it)) { "Task subject is not on this farm" } }
        val loaded = loadTaskBoard(ops, planning, LocalDate.now().toEpochDay()) { ids ->
            database?.let { db -> ids.toList().chunked(200).flatMap { db.animals().getMany(farmId,it) }.associate {
                it.id to listOfNotNull(it.tag,it.name?.takeIf(String::isNotBlank)).joinToString(" · ")
            } }.orEmpty()
        }
        val subjectSeries=if(subjectAnimalId==null) emptySet() else database?.taskSeries()?.active(farmId).orEmpty()
            .filter { it.animalId==subjectAnimalId }.map { it.id }.toSet()
        board = if(subjectAnimalId==null) loaded else loaded.copy(rows=loaded.rows.filter { it.animalId==subjectAnimalId },
            series=loaded.series.filter { it.seriesId in subjectSeries })
        completedCount = if(subjectAnimalId==null) loadCompletedCount() else null
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
    LaunchedEffect(farmId,subjectAnimalId) { runSuspendCatching { refresh() }.onFailure { error = it.message } }
    val actions = TaskModuleActions(
        create = { title, module, code, due -> runWrite { createOneOffTask(ops, title, module, code, due, newContext()); savedRevision++ } },
        complete = { id -> runWrite { completeTaskRow(board.rows.firstOrNull { it.id == id }, id, ops, planning, newContext()) } },
        createSeries = { draft -> planning?.let { runWrite { it.create(draft, newContext()); savedRevision++ } } },
        endSeries = { id -> planning?.let { runWrite { it.commands.end(EndTaskSeries(id, LocalDate.now().toEpochDay()), newContext()) } } },
        editSeries = { draft, done -> planning?.let { runWrite { it.edit(draft, newContext()); done() } } },
        updateTask = { draft, done -> planning?.let { runWrite { it.update(draft, newContext()); done() } } },
        createDetailed = { draft -> runWrite {
            val animal = draft.animalId?.let { id -> requireNotNull(database?.animals()?.get(farmId,id)) { "The selected animal is not on this farm" } }
            val module = if(draft.moduleCode=="farm" && animal!=null) animal.speciesCode else draft.moduleCode
            createOneOffTask(ops,draft.title,module,draft.taskCode,draft.dueDate.toString(),newContext(),draft.animalId)
            savedRevision++
        } },
    )
    TaskModuleNavigator(
        farmId, board, completedCount, busy, error, focusTaskId, entryPage, planning,
        database, newContext, actions, onBack, savedRevision,
    )
}
