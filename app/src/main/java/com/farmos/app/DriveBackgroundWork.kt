package com.farmos.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.Operation
import androidx.work.await
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** WorkManager retains network-constrained work across normal process death and device restart. */
internal object DriveBackgroundWork {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun restore(app: FarmOsApplication) {
        if (app.backendConfigured) return
        scope.launch {
            try {
                app.database.localAccess().farms().forEach { farm ->
                    try {
                        scheduleApproved(app, farm.farmId)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        schedulingFailed(app, farm.farmId)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A later app start or committed action retries scheduling; local records are untouched.
            }
        }
    }

    fun request(context: Context, farmId: String) {
        val app = context.applicationContext as? FarmOsApplication ?: return
        if (app.backendConfigured || !DriveBackgroundWorker.SAFE_FARM_ID.matches(farmId)) return
        scope.launch {
            try {
                scheduleApproved(app, farmId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                schedulingFailed(app, farmId)
            }
        }
    }

    private suspend fun scheduleApproved(app: FarmOsApplication, farmId: String): Unit = DriveFarmCoordinator.shared.withFarm(farmId) {
        val configStore = DriveConfigStore(app)
        val config = configStore.get(farmId)
        if (config == null) {
            cancel(app, farmId)
            return@withFarm
        }
        try {
            DriveGatewayAuthority(app.database, app.deviceId).requireCurrent(farmId, config, configStore)
        } catch (denied: DriveGatewayApprovalException) {
            val state = DriveDeliveryStateStore(app).load(farmId, config).copy(
                deliveryStatus = DriveDeliveryStatus.APPROVAL_REQUIRED, lastError = denied.message, counts = null,
            )
            DriveDeliveryStateStore(app).save(farmId, config, state)
            DriveFarmCoordinator.shared.state(farmId) { state }.value = state
            cancel(app, farmId)
            return@withFarm
        }
        if (DriveDeliveryStateStore(app).load(farmId, config).deliveryStatus in
            setOf(DriveDeliveryStatus.NEEDS_CONSENT, DriveDeliveryStatus.APPROVAL_REQUIRED)
        ) {
            cancel(app, farmId)
            return@withFarm
        }
        if (!driveNetworkAvailable(app)) {
            val state = DriveDeliveryStateStore(app).load(farmId, config).copy(
                deliveryStatus = DriveDeliveryStatus.OFFLINE,
                lastError = "Offline. Farm records remain saved on this device.", counts = null,
            )
            DriveDeliveryStateStore(app).save(farmId, config, state)
            DriveFarmCoordinator.shared.state(farmId) { state }.value = state
        }
        val manager = WorkManager.getInstance(app)
        enqueueRequests(
            periodic = { manager.enqueueUniquePeriodicWork(periodicName(farmId), ExistingPeriodicWorkPolicy.KEEP, periodicRequest(farmId)) },
            immediate = { manager.enqueueUniqueWork(immediateName(farmId), ExistingWorkPolicy.KEEP, immediateRequest(farmId)) },
        )
        Unit
    }

    /** Scheduling is accepted only when WorkManager commits both asynchronous enqueue operations. */
    internal suspend fun enqueueRequests(periodic: () -> Operation, immediate: () -> Operation) {
        periodic().await()
        immediate().await()
    }

    /** A late result from an old attempt must not cancel a newly approved connection. */
    suspend fun stopIfBlocked(context: Context, farmId: String) = DriveFarmCoordinator.shared.withFarm(farmId) {
        val config = DriveConfigStore(context).get(farmId)
        val status = DriveDeliveryStateStore(context).load(farmId, config).deliveryStatus
        if (config == null || status == DriveDeliveryStatus.APPROVAL_REQUIRED || status == DriveDeliveryStatus.NEEDS_CONSENT) {
            cancel(context, farmId)
        }
    }

    private suspend fun schedulingFailed(context: Context, farmId: String) = DriveFarmCoordinator.shared.withFarm(farmId) {
        runCatching {
            val config = DriveConfigStore(context).get(farmId) ?: return@runCatching
            val store = DriveDeliveryStateStore(context)
            val previous = store.load(farmId, config)
            if (previous.deliveryStatus in setOf(DriveDeliveryStatus.NEEDS_CONSENT, DriveDeliveryStatus.APPROVAL_REQUIRED)) return@runCatching
            val state = previous.copy(
                deliveryStatus = DriveDeliveryStatus.FAILED,
                lastError = "Background Drive delivery could not be scheduled. Open Storage and backup to retry.",
                counts = null,
            )
            store.save(farmId, config, state)
            DriveFarmCoordinator.shared.state(farmId) { state }.value = state
        }
    }

    fun cancel(context: Context, farmId: String) {
        val manager = WorkManager.getInstance(context)
        manager.cancelUniqueWork(periodicName(farmId))
        manager.cancelUniqueWork(immediateName(farmId))
    }

    internal fun periodicRequest(farmId: String): PeriodicWorkRequest =
        PeriodicWorkRequestBuilder<DriveBackgroundWorker>(15, TimeUnit.MINUTES)
            .setConstraints(networkConstraint())
            .setInputData(workDataOf(DriveBackgroundWorker.FARM_ID to checkedFarm(farmId)))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag("goat-drive")
            .build()

    internal fun immediateRequest(farmId: String): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<DriveBackgroundWorker>()
            .setConstraints(networkConstraint())
            .setInputData(workDataOf(DriveBackgroundWorker.FARM_ID to checkedFarm(farmId)))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag("goat-drive")
            .build()

    private fun networkConstraint() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    private fun checkedFarm(farmId: String): String = farmId.also { require(DriveBackgroundWorker.SAFE_FARM_ID.matches(it)) }
    private fun periodicName(farmId: String) = "goat-drive-periodic-$farmId"
    private fun immediateName(farmId: String) = "goat-drive-now-$farmId"
}

internal fun driveNetworkAvailable(context: Context): Boolean {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
