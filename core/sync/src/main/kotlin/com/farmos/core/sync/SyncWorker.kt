package com.farmos.core.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

interface SyncEngineOwner {
    val syncEngine: SyncEngine
    suspend fun pullAuthoritativeChanges(): AuthoritativePullOutcome
}

enum class AuthoritativePullOutcome {
    APPLIED_OR_CURRENT,
    SKIPPED,
    RETRY,
    FAILURE,
}

class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val owner = applicationContext as? SyncEngineOwner
            ?: return Result.failure()

        val push = owner.syncEngine.drain()
        val pull = owner.pullAuthoritativeChanges()

        return when {
            pull == AuthoritativePullOutcome.FAILURE -> Result.failure()
            push.retrying > 0 || pull == AuthoritativePullOutcome.RETRY -> Result.retry()
            else -> Result.success()
        }
    }
}
