package com.farmos.core.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.farmos.core.network.AuthorizationLoss

interface SyncEngineOwner {
    val syncEngine: SyncEngine
    suspend fun pullAuthoritativeChanges(): AuthoritativePullOutcome
    fun onTerminalAuthorizationLoss(reason: AuthorizationLoss)
}

enum class AuthoritativePullOutcome {
    APPLIED_OR_CURRENT,
    SKIPPED,
    RETRY,
    FAILURE,
    AUTHORIZATION_LOST,
}

class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val owner = applicationContext as? SyncEngineOwner
            ?: return Result.failure()

        val push = owner.syncEngine.drain(workName = tags.firstOrNull() ?: "farm-os-sync")
        push.authorizationLoss?.let { loss ->
            owner.onTerminalAuthorizationLoss(loss)
            return Result.failure()
        }

        val pull = owner.pullAuthoritativeChanges()
        if (pull == AuthoritativePullOutcome.AUTHORIZATION_LOST) {
            return Result.failure()
        }

        return when {
            pull == AuthoritativePullOutcome.FAILURE -> Result.failure()
            push.retrying > 0 || pull == AuthoritativePullOutcome.RETRY -> Result.retry()
            else -> Result.success()
        }
    }
}
