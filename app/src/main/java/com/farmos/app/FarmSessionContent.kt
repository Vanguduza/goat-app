package com.farmos.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.farmos.core.model.LocalCommandContext
import com.farmos.core.network.FarmMembership
import com.farmos.core.sync.SyncWorker
import java.util.UUID

@Composable
fun FarmSessionContent(
    app: FarmOsApplication,
    membership: FarmMembership,
    farmName: String?,
    onRequireReauth: (String?) -> Unit,
    onRequireFarmReselection: (String, List<FarmMembership>) -> Unit,
    onSignOut: () -> Unit,
) {
    var destination by remember { mutableStateOf<FarmDestination>(FarmDestination.Home) }
    val repository = remember(membership.farmId) { app.goatRepository(membership.farmId) }
    val ops = remember(membership.farmId) { app.opsRepository(membership.farmId) }
    fun context(): LocalCommandContext {
        val userId = requireNotNull(app.sessionStore.current()?.user?.id) { "Sign in is required" }
        return LocalCommandContext(
            mutationId = UUID.randomUUID().toString(),
            farmId = membership.farmId,
            actorId = userId,
            deviceId = app.deviceId,
            occurredAtEpochMillis = System.currentTimeMillis(),
        )
    }
    fun enqueueSync() {
        WorkManager.getInstance(app).enqueue(
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .addTag(FarmOsApplication.SYNC_WORK_TAG)
                .build(),
        )
    }
    val backHome = { destination = FarmDestination.Home }
    when (val dest = destination) {
        FarmDestination.Home -> FarmHomeHost(
            farmName = farmName,
            membershipRole = membership.role,
            farmId = membership.farmId,
            ops = ops,
            loadGoatCount = { repository.listGoats(500).size },
            loadPendingSync = { app.database.outbox().countUnacknowledgedForFarm(membership.farmId) },
            onOpen = { destination = it },
            onSignOut = onSignOut,
        )
        is FarmDestination.Goat -> GoatModuleHost(
            app = app,
            membership = membership,
            farmName = farmName,
            newContext = ::context,
            enqueueSync = ::enqueueSync,
            onRequireReauth = onRequireReauth,
            onRequireFarmReselection = onRequireFarmReselection,
            onSignOut = onSignOut,
            onBack = backHome,
            entryPage = dest.entry,
        )
        is FarmDestination.Health -> HealthModuleHost(
            farmId = membership.farmId,
            ops = ops,
            newContext = ::context,
            enqueueSync = ::enqueueSync,
            onBack = backHome,
            entryPage = dest.entry,
        )
        is FarmDestination.Task -> TasksModuleHost(
            farmId = membership.farmId,
            ops = ops,
            newContext = ::context,
            enqueueSync = ::enqueueSync,
            onBack = backHome,
            focusTaskId = dest.taskId,
        )
        is FarmDestination.Tasks -> TasksModuleHost(
            farmId = membership.farmId,
            ops = ops,
            newContext = ::context,
            enqueueSync = ::enqueueSync,
            onBack = backHome,
            entryPage = dest.entry,
        )
        is FarmDestination.Module -> when (dest.module) {
            FarmModule.TASKS -> TasksModuleHost(
                farmId = membership.farmId,
                ops = ops,
                newContext = ::context,
                enqueueSync = ::enqueueSync,
                onBack = backHome,
            )
            FarmModule.MONEY -> MoneyModuleHost(
                farmId = membership.farmId,
                ops = ops,
                newContext = ::context,
                enqueueSync = ::enqueueSync,
                onBack = backHome,
            )
            FarmModule.POULTRY -> PoultryModuleHost(
                farmId = membership.farmId,
                ops = ops,
                newContext = ::context,
                enqueueSync = ::enqueueSync,
                onBack = backHome,
            )
            FarmModule.RABBIT -> RabbitModuleHost(
                farmId = membership.farmId,
                ops = ops,
                rabbitHerd = com.farmos.data.herd.RoomHerdRepository(app.database, membership.farmId, "rabbit"),
                newContext = ::context,
                enqueueSync = ::enqueueSync,
                onBack = backHome,
            )
            FarmModule.INVENTORY -> InventoryModuleHost(
                farmId = membership.farmId,
                ops = ops,
                newContext = ::context,
                enqueueSync = ::enqueueSync,
                onBack = backHome,
            )
            FarmModule.HOME, FarmModule.GOAT, FarmModule.HEALTH -> FarmHomeHost(
                farmName = farmName,
                membershipRole = membership.role,
                farmId = membership.farmId,
                ops = ops,
                loadGoatCount = { repository.listGoats(500).size },
                loadPendingSync = { app.database.outbox().countUnacknowledgedForFarm(membership.farmId) },
                onOpen = { destination = it },
                onSignOut = onSignOut,
            )
            else -> OperatingModuleHost(
                module = dest.module,
                farmId = membership.farmId,
                database = app.database,
                ops = ops,
                newContext = ::context,
                enqueueSync = ::enqueueSync,
                onBack = backHome,
            )
        }
    }
}
