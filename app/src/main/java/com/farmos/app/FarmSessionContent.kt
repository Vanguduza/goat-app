package com.farmos.app

import com.farmos.domain.access.Permission
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.farmos.core.database.unsharedLocalOperations
import com.farmos.core.model.LocalCommandContext
import com.farmos.core.network.FarmMembership
import com.farmos.core.sync.SyncWorker
import java.util.UUID

@Composable
fun FarmSessionContent(
    app: FarmOsApplication,
    membership: FarmMembership,
    farmName: String?,
    memberships: List<FarmMembership>,
    farmNames: Map<String, String>,
    onSwitchFarm: (FarmMembership) -> Unit,
    onRequireReauth: (String?) -> Unit,
    onRequireFarmReselection: (String, List<FarmMembership>) -> Unit,
    onSignOut: () -> Unit,
    /** The local GOAT account signed in on this device; server-era sessions leave it null. */
    actorId: String? = null,
) {
    var destination by remember(membership.farmId) { mutableStateOf<FarmDestination>(FarmDestination.Home) }
    val repository = remember(membership.farmId) { app.goatRepository(membership.farmId) }
    val ops = remember(membership.farmId) { app.opsRepository(membership.farmId) }
    fun context(): LocalCommandContext {
        val userId = actorId ?: requireNotNull(app.sessionStore.current()?.user?.id) { "Sign in is required" }
        return LocalCommandContext(
            mutationId = UUID.randomUUID().toString(),
            farmId = membership.farmId,
            actorId = userId,
            deviceId = app.deviceId,
            occurredAtEpochMillis = System.currentTimeMillis(),
        )
    }
    fun enqueueSync() {
        // Without a server there is nothing to upload: share the change with the farm's devices instead.
        if (!app.backendConfigured) {
            app.farmLan?.requestSync()
            return
        }
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag(FarmOsApplication.SYNC_WORK_TAG)
            .build()
        WorkManager.getInstance(app).enqueueUniqueWork(
            FarmOsApplication.ON_DEMAND_SYNC_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
    suspend fun loadSyncQueueCounts(): Map<SyncQueueView, Long>? =
        if (!serverSyncQueuesVisible(app.backendConfigured, membership.role)) {
            null
        } else {
            syncQueueCounts(app.database.outbox().countByStateForFarm(membership.farmId).associate { it.state to it.count })
        }
    /** Server-era outbox rows, or, without a server, local changes no other farm device holds yet. */
    suspend fun pendingSync(): Long =
        if (app.backendConfigured) {
            app.database.outbox().countUnacknowledgedForFarm(membership.farmId)
        } else {
            app.database.unsharedLocalOperations(membership.farmId, app.deviceId)
        }
    val backHome = { destination = FarmDestination.Home }
    // Repeating and assigned tasks (D-020); Assigned to me needs a signed-in local account.
    val taskPlanning = remember(membership.farmId, actorId) { TaskPlanning(app.database, membership.farmId, canPlanFarmWork(membership.role), actorId) }
    val runtimeRoute = destination.runtimeRouteContract()
    Box(Modifier.testTag(runtimeRoute.testTag)) {
        when (val dest = destination) {
        FarmDestination.Home -> FarmHomeHost(
            farmName = farmName,
            membershipRole = membership.role,
            farmId = membership.farmId,
            ops = ops,
            loadGoatCount = { app.database.animals().countBySpecies(membership.farmId, "goat") },
            loadCompletedTaskCount = { app.database.tasks().countCompletedForFarm(membership.farmId) },
            loadActiveWithdrawalCount = { app.database.lifecycle().activeWithdrawalCount(membership.farmId, it) },
            loadPendingSync = { pendingSync() },
            loadSyncQueueCounts = ::loadSyncQueueCounts,
            onOpen = { destination = it },
            onSignOut = onSignOut,
            memberships = memberships,
            farmNames = farmNames,
            onSwitchFarm = onSwitchFarm,
        )
        FarmDestination.Search -> GlobalSearchHost(
            app = app,
            farmId = membership.farmId,
            onOpen = { destination = it },
            onBack = backHome,
            onRequireReauth = onRequireReauth,
        )
        FarmDestination.Settings -> SettingsHost(
            directory = remember(app) { LocalFarmDirectory(app.database, app.deviceId) },
            database = app.database,
            farmId = membership.farmId,
            actorId = actorId,
            deviceId = app.deviceId,
            onBack = backHome,
            lan = app.farmLan,
            drive = app.farmDrive,
        )
        is FarmDestination.SyncQueue -> SyncQueueHost(
            view = dest.view,
            permitted = syncQueuesPermitted(membership.role),
            loadRows = { view -> app.database.outbox().listForFarmInState(membership.farmId, view.state.name, SYNC_QUEUE_LIMIT) },
            onSelectView = { destination = FarmDestination.SyncQueue(it) },
            onBack = backHome,
            loadTotal = { view -> loadSyncQueueCounts()?.get(view) },
            traceOnServer = app.mutationTraceClient?.let { client ->
                val trace: suspend (String) -> ServerMutationTrace = { client.trace(membership.farmId, it).toServerMutationTrace() }
                trace
            },
        )
        is FarmDestination.HomePanel -> error("Home panels are nested owners and must be intercepted by RoleAwareFarmHomeScreen")
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
            loadReadModel = { loadHealthReadModel(app.database, membership.farmId) },
            searchAnimals = remember(membership.farmId) { animalSelectorSearch(app.database, membership.farmId, speciesCode = null) },
        )
        is FarmDestination.Task -> TasksModuleHost(
            farmId = membership.farmId,
            ops = ops,
            newContext = ::context,
            enqueueSync = ::enqueueSync,
            onBack = backHome,
            focusTaskId = dest.taskId,
            planning = taskPlanning,
        )
        is FarmDestination.Tasks -> TasksModuleHost(
            farmId = membership.farmId,
            ops = ops,
            newContext = ::context,
            enqueueSync = ::enqueueSync,
            onBack = backHome,
            entryPage = dest.entry,
            loadCompletedCount = { app.database.tasks().countCompletedForFarm(membership.farmId) },
            planning = taskPlanning,
        )
        is FarmDestination.Module -> when (dest.module) {
            FarmModule.TASKS -> TasksModuleHost(
                farmId = membership.farmId,
                ops = ops,
                newContext = ::context,
                enqueueSync = ::enqueueSync,
                onBack = backHome,
                loadCompletedCount = { app.database.tasks().countCompletedForFarm(membership.farmId) },
                planning = taskPlanning,
            )
            FarmModule.MONEY -> MoneyModuleHost(
                farmId = membership.farmId,
                ops = ops,
                newContext = ::context,
                enqueueSync = ::enqueueSync,
                onBack = backHome,
                loadCurrency = { app.database.farmCurrency(membership.farmId) },
            )
            FarmModule.POULTRY -> PoultryModuleHost(
                farmId = membership.farmId,
                ops = ops,
                newContext = ::context,
                enqueueSync = ::enqueueSync,
                onBack = backHome,
                loadRecords = { loadPoultryRecords(app.database, membership.farmId) },
                loadFlock = { loadPoultryFlockRecords(app.database, membership.farmId, it) },
            )
            FarmModule.GROUPS -> GroupsModuleHost(
                farmId = membership.farmId,
                ops = ops,
                newContext = ::context,
                enqueueSync = ::enqueueSync,
                onBack = backHome,
                loadGroups = { loadGroupViews(app.database, membership.farmId) },
                loadGroup = { loadGroupRecords(app.database, membership.farmId, it) },
            )
            FarmModule.PASTURE -> PastureModuleHost(
                farmId = membership.farmId,
                ops = ops,
                newContext = ::context,
                enqueueSync = ::enqueueSync,
                onBack = backHome,
                loadRecords = { loadPastureRecords(app.database, membership.farmId) },
            )
            FarmModule.LABOUR -> LabourModuleHost(
                farmId = membership.farmId,
                ops = ops,
                newContext = ::context,
                enqueueSync = ::enqueueSync,
                onBack = backHome,
                loadRecords = { loadLabourRecords(app.database, membership.farmId) },
                workers = { back -> WorkerRegisterHost(app.database, membership.farmId, membership.role, ::context, ::enqueueSync, back) },
                capture = remember(membership.farmId, ops) { LabourCapture(app.database, membership.farmId, ops) },
            )
            FarmModule.ASSETS -> AssetsModuleHost(
                farmId = membership.farmId,
                ops = ops,
                newContext = ::context,
                enqueueSync = ::enqueueSync,
                onBack = backHome,
                loadRecords = { loadAssetRecords(app.database, membership.farmId) },
            )
            FarmModule.RABBIT -> RabbitModuleHost(
                farmId = membership.farmId,
                ops = ops,
                rabbitHerd = com.farmos.data.herd.RoomHerdRepository(app.database, membership.farmId, "rabbit"),
                newContext = ::context,
                enqueueSync = ::enqueueSync,
                onBack = backHome,
                loadRecords = { loadRabbitRecords(app.database, membership.farmId) },
                searchRabbits = remember(membership.farmId) { animalSelectorSearch(app.database, membership.farmId, "rabbit") },
                pedigree = remember(membership.farmId) { rabbitPedigreePorts(app.database, membership.farmId, ops, ::context, ::enqueueSync) },
                exitFor = { rabbit, onRecorded ->
                    SpeciesExitHost(app.database, membership.farmId, SpeciesAnimalRow(rabbit.animalId, rabbit.label, rabbit.active), null, ::context, onRecorded, onBack = {})
                },
                attachmentsFor = { rabbit -> AnimalAttachmentsHost(app.database, membership.farmId, rabbit.animalId, canAttach = rabbit.active, ::context) },
            )
            FarmModule.INVENTORY -> InventoryModuleHost(
                farmId = membership.farmId,
                ops = ops,
                newContext = ::context,
                enqueueSync = ::enqueueSync,
                onBack = backHome,
                loadReadModel = { loadInventoryReadModel(app.database, membership.farmId) },
                stockCount = { back -> StockCountHost(app.database, membership.farmId, membership.role, ::context, ::enqueueSync, back) },
            )
            FarmModule.HOME, FarmModule.GOAT, FarmModule.HEALTH -> FarmHomeHost(
                farmName = farmName,
                membershipRole = membership.role,
                farmId = membership.farmId,
                ops = ops,
                loadGoatCount = { app.database.animals().countBySpecies(membership.farmId, "goat") },
                loadCompletedTaskCount = { app.database.tasks().countCompletedForFarm(membership.farmId) },
                loadActiveWithdrawalCount = { app.database.lifecycle().activeWithdrawalCount(membership.farmId, it) },
                loadPendingSync = { pendingSync() },
            loadSyncQueueCounts = ::loadSyncQueueCounts,
                onOpen = { destination = it },
                onSignOut = onSignOut,
            )
            FarmModule.FEED -> FeedModuleHost(
                farmId = membership.farmId,
                ops = ops,
                newContext = ::context,
                enqueueSync = ::enqueueSync,
                onBack = backHome,
                loadRecords = { loadFeedRecords(app.database, membership.farmId) },
            )
            FarmModule.WATER -> WaterModuleHost(
                farmId = membership.farmId,
                ops = ops,
                newContext = ::context,
                enqueueSync = ::enqueueSync,
                onBack = backHome,
                loadRecords = { loadWaterRecords(app.database, membership.farmId) },
            )
            FarmModule.SALES -> SalesModuleHost(
                farmId = membership.farmId, ops = ops, newContext = ::context, enqueueSync = ::enqueueSync, onBack = backHome,
                loadRecords = { loadSalesRecords(app.database, membership.farmId) },
                loadCurrency = { app.database.farmCurrency(membership.farmId) },
                customers = { back -> CustomerRegisterHost(app.database, membership.farmId, membership.role, ::context, ::enqueueSync, back) },
                customerSearch = remember(membership.farmId) { customerSelectorSearch(app.database, membership.farmId) },
                customerCommands = remember(membership.farmId) { com.farmos.data.herd.CustomerCommands(app.database, membership.farmId) },
                animalSale = { back -> AnimalSaleHost(app.database, membership.farmId, ::context, ::enqueueSync, back) },
            )
            FarmModule.PROCUREMENT -> ProcurementModuleHost(
                farmId = membership.farmId, ops = ops, newContext = ::context, enqueueSync = ::enqueueSync, onBack = backHome,
                loadRecords = { loadProcurementRecords(app.database, membership.farmId) },
                loadCurrency = { app.database.farmCurrency(membership.farmId) },
            )
            FarmModule.WAITLIST -> RabbitCommerceModuleHost(
                farmId = membership.farmId, ops = ops, newContext = ::context, enqueueSync = ::enqueueSync, onBack = backHome,
                loadRecords = { loadRabbitCommerceRecords(app.database, membership.farmId) },
                loadCurrency = { app.database.farmCurrency(membership.farmId) },
            )
            FarmModule.REPORTS -> ReportsModuleHost(
                database = app.database,
                farmId = membership.farmId,
                canExport = rolePermits(membership.role, Permission.EXPORT_FARM_DATA),
                onBack = backHome,
            )
            FarmModule.GENETICS -> GeneticsModuleHost(
                database = app.database,
                farmId = membership.farmId,
                ops = ops,
                newContext = ::context,
                enqueueSync = ::enqueueSync,
                onBack = backHome,
            )
            FarmModule.CAPACITY -> CapacityModuleHost(
                database = app.database,
                farmId = membership.farmId,
                onBack = backHome,
            )
            FarmModule.ANALYTICS -> AnalyticsModuleHost(
                database = app.database,
                farmId = membership.farmId,
                onBack = backHome,
            )
            FarmModule.SIMULATION -> SimulationModuleHost(
                database = app.database,
                farmId = membership.farmId,
                filesDir = app.filesDir,
                onBack = backHome,
            )
            FarmModule.AI -> AiBoundaryHost(
                database = app.database,
                farmId = membership.farmId,
                filesDir = app.filesDir,
                onOpenModule = { destination = FarmDestination.Module(it) },
                onBack = backHome,
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
}
