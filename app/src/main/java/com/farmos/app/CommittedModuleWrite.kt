package com.farmos.app

import com.farmos.core.design.runSuspendCatching
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A successful Room command stays saved even if a later refresh or sync request fails.
 * Cancellation is propagated; the receipt and sync request are issued before refreshing.
 */
internal suspend fun completeModuleWrite(
    write: suspend () -> Unit,
    onCommitted: () -> Unit,
    enqueueSync: () -> Unit,
    refresh: suspend () -> Unit,
): String? {
    write()
    onCommitted()
    val syncFailed = runSuspendCatching { enqueueSync() }.isFailure
    val refreshFailed = runSuspendCatching { refresh() }.isFailure
    return when {
        syncFailed && refreshFailed ->
            "Saved on this device. Records could not refresh and sharing could not be scheduled. Reopen this screen to reload records."
        refreshFailed ->
            "Saved on this device. Records could not refresh. Reopen this screen to reload records."
        syncFailed ->
            "Saved on this device. Sharing could not be scheduled. Use Sync now to try sharing again."
        else -> null
    }
}

/** Capture hosts reserve their submit slot synchronously and always release it after cancellation. */
internal fun launchCommittedModuleWrite(
    scope: CoroutineScope,
    write: suspend () -> Unit,
    isBusy: () -> Boolean,
    setBusy: (Boolean) -> Unit,
    setError: (String?) -> Unit,
    enqueueSync: () -> Unit,
    refresh: suspend () -> Unit,
    onStarted: () -> Unit = {},
    onCommitted: () -> Unit = {},
) {
    if (isBusy()) return
    setBusy(true)
    setError(null)
    onStarted()
    scope.launch {
        try {
            runSuspendCatching {
                completeModuleWrite(write, onCommitted, enqueueSync, refresh)
            }.onSuccess(setError)
                .onFailure { setError(it.message ?: "The change could not be saved on this device") }
        } finally {
            setBusy(false)
        }
    }
}
