package com.farmos.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.WorkerRegisterCommands
import com.farmos.domain.access.Permission
import com.farmos.domain.ops.CreateFarmWorker
import com.farmos.domain.ops.UpdateFarmWorker
import com.farmos.feature.ops.WorkerRegisterScreens
import com.farmos.feature.ops.WorkerView
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.farmos.core.design.runSuspendCatching

/** Every worker on the farm, active first, with the local account linked to each, if any. */
internal suspend fun loadWorkers(database: FarmOsDatabase, farmId: String): List<WorkerView> {
    val linked = withContext(Dispatchers.IO) { database.localAccess().accounts(farmId) }
        .filter { it.workerId != null }
        .associate { it.workerId!! to it.username }
    return database.workers().all(farmId).map { WorkerView(it.id, it.name, it.active, linked[it.id]) }
}

/** The worker register (D-016, resolution R1): supervisors and management (MANAGE_WORKERS) maintain it. */
@Composable
fun WorkerRegisterHost(
    database: FarmOsDatabase,
    farmId: String,
    role: String,
    newContext: () -> LocalCommandContext,
    enqueueSync: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val commands = remember(farmId) { WorkerRegisterCommands(database, farmId) }
    val canManage = rolePermits(role, Permission.MANAGE_WORKERS)
    var workers by remember(farmId) { mutableStateOf(emptyList<WorkerView>()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun runWrite(block: suspend () -> Unit) {
        scope.launch {
            busy = true
            error = null
            runSuspendCatching {
                check(canManage) { "Only supervisors and farm management manage workers" }
                block()
                workers = loadWorkers(database, farmId)
            }.onSuccess { enqueueSync() }.onFailure { error = it.message }
            busy = false
        }
    }

    LaunchedEffect(farmId) { runSuspendCatching { workers = loadWorkers(database, farmId) }.onFailure { error = it.message } }

    WorkerRegisterScreens(
        workers = workers,
        canManage = canManage,
        busy = busy,
        error = error,
        onAdd = { name -> runWrite { commands.create(CreateFarmWorker(UUID.randomUUID().toString(), name), newContext()) } },
        onRename = { id, name -> runWrite { commands.update(UpdateFarmWorker(id, name = name), newContext()) } },
        onSetActive = { id, active -> runWrite { commands.update(UpdateFarmWorker(id, active = active), newContext()) } },
        onBack = onBack,
    )
}
