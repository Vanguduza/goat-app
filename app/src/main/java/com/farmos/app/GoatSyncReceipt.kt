package com.farmos.app

import com.farmos.domain.replication.SyncOutcome
import com.farmos.domain.replication.SyncSessionStatus

/** Manual sync receipt. Never claims Synced unless a server outcome is known. */
internal fun goatManualSyncReceipt(
    acknowledged: Int,
    appliedEvents: Int,
    conflicts: Int,
    rejected: Int,
    retrying: Int,
): String =
    when {
        conflicts > 0 -> "Conflict needs review"
        rejected > 0 -> "Server rejected a pending record"
        retrying > 0 -> "Saved locally · server retry pending"
        acknowledged > 0 && appliedEvents > 0 ->
            "Server accepted $acknowledged local change(s). $appliedEvents server change(s) applied"
        acknowledged > 0 -> "Server accepted $acknowledged local change(s)"
        appliedEvents > 0 -> "$appliedEvents server change(s) applied"
        else -> "No pending local changes. Server returned no new events."
    }

/**
 * Manual sync receipt without a server: what the farm-network sync did. Never claims a change reached
 * another device unless a session with that device completed. [waiting] is this device's own changes no
 * farm device holds yet, counted after the attempt.
 */
internal fun goatLocalSyncReceipt(outcomes: List<SyncOutcome>?, waiting: Long): String {
    val stillWaiting = if (waiting > 0) " · $waiting change(s) waiting to sync" else ""
    if (outcomes == null) return "Saved locally · farm network sync is not running on this device$stillWaiting"
    if (outcomes.isEmpty()) return "Saved locally · no other farm device found on this network$stillWaiting"
    val completed = outcomes.filter { it.status == SyncSessionStatus.COMPLETED }
    val failed = outcomes.size - completed.size
    val received = completed.sumOf { it.pulledOperations }
    val sent = completed.sumOf { it.pushedOperations }
    val exchanged = "sent $sent, received $received change(s)"
    return when {
        completed.isEmpty() -> "Saved locally · sync with $failed farm device(s) did not complete$stillWaiting"
        failed > 0 -> "Synchronised with ${completed.size} farm device(s), $exchanged · $failed device(s) did not complete$stillWaiting"
        else -> "Synchronised with ${completed.size} farm device(s), $exchanged$stillWaiting"
    }
}
