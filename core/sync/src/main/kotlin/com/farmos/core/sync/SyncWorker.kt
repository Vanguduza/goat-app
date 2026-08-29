package com.farmos.core.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

interface SyncEngineOwner {
    val syncEngine: SyncEngine
}

class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val owner = applicationContext as? SyncEngineOwner
            ?: return Result.failure()
        val run = owner.syncEngine.drain()
        return when {
            run.conflicts > 0 || run.rejected > 0 -> Result.success()
            run.retrying > 0 -> Result.retry()
            else -> Result.success()
        }
    }
}
