package com.farmos.core.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.farmos.core.network.AuthorizationLoss

interface SyncEngineOwner {
    val syncEngine: SyncEngine

    /**
     * Whether a server is configured to receive the outbox. Without one there is nothing to send or pull,
     * and background sync must leave the outbox untouched rather than retry against a server that does not exist.
     */
    val serverSyncConfigured: Boolean get() = true
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

enum class SyncWorkOutcome { SUCCESS, RETRY, FAILURE }

/** One background sync pass: send the outbox, then pull. Does nothing without a configured server. */
suspend fun runBackgroundSync(owner: SyncEngineOwner, workName: String): SyncWorkOutcome {
    if (!owner.serverSyncConfigured) return SyncWorkOutcome.SUCCESS

    val push = owner.syncEngine.drain(workName = workName)
    push.authorizationLoss?.let { loss ->
        owner.onTerminalAuthorizationLoss(loss)
        return SyncWorkOutcome.FAILURE
    }

    val pull = owner.pullAuthoritativeChanges()
    if (pull == AuthoritativePullOutcome.AUTHORIZATION_LOST) {
        return SyncWorkOutcome.FAILURE
    }

    return when {
        pull == AuthoritativePullOutcome.FAILURE -> SyncWorkOutcome.FAILURE
        push.retrying > 0 || pull == AuthoritativePullOutcome.RETRY -> SyncWorkOutcome.RETRY
        else -> SyncWorkOutcome.SUCCESS
    }
}

class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val owner = applicationContext as? SyncEngineOwner
            ?: return Result.failure()
        return when (runBackgroundSync(owner, tags.firstOrNull() ?: "farm-os-sync")) {
            SyncWorkOutcome.SUCCESS -> Result.success()
            SyncWorkOutcome.RETRY -> Result.retry()
            SyncWorkOutcome.FAILURE -> Result.failure()
        }
    }
}
