package com.farmos.app

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
