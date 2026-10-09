package com.farmos.app

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.File
import kotlinx.coroutines.CancellationException

/** A durable local-first job. Its only persisted input is the farm ID; authorization stays on-device. */
class DriveBackgroundWorker internal constructor(
    appContext: Context,
    params: WorkerParameters,
    private val deliver: suspend (String) -> DriveDeliveryStatus,
    private val disable: suspend (String) -> Unit,
) : CoroutineWorker(appContext, params) {
    constructor(appContext: Context, params: WorkerParameters) : this(
        appContext,
        params,
        deliver = { farmId ->
            val app = appContext.applicationContext as? FarmOsApplication
                ?: error("The farm application is unavailable")
            if (app.backendConfigured) DriveDeliveryStatus.NOT_CONFIGURED else {
                FarmDriveRuntime(
                    app, app.database, app.keyVault, farmId, app.deviceId,
                    FileAttachmentStore(File(app.filesDir, "attachments")),
                ).use { it.synchroniseOnce() }
            }
        },
        disable = { farmId -> DriveBackgroundWork.stopIfBlocked(appContext, farmId) },
    )

    override suspend fun doWork(): Result {
        val farmId = inputData.getString(FARM_ID)?.takeIf { SAFE_FARM_ID.matches(it) }
            ?: return Result.failure()
        return try {
            resultFor(farmId, deliver(farmId))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private suspend fun resultFor(farmId: String, status: DriveDeliveryStatus): Result =
        when (status) {
            DriveDeliveryStatus.OFFLINE, DriveDeliveryStatus.RETRY_WAIT, DriveDeliveryStatus.FAILED,
            DriveDeliveryStatus.RUNNING, DriveDeliveryStatus.KEY_ROTATION_PENDING -> Result.retry()
            DriveDeliveryStatus.NOT_CONFIGURED, DriveDeliveryStatus.APPROVAL_REQUIRED,
            DriveDeliveryStatus.NEEDS_CONSENT -> {
                disable(farmId)
                // Completing the job is not a backup claim; the durable gateway status explains why it stopped.
                Result.success(workDataOf(DELIVERY_STATUS to status.name))
            }
            else -> Result.success(workDataOf(DELIVERY_STATUS to status.name))
        }

    companion object {
        internal const val FARM_ID = "farm_id"
        internal const val DELIVERY_STATUS = "delivery_status"
        internal val SAFE_FARM_ID = Regex("[A-Za-z0-9-]{1,64}")
    }
}
