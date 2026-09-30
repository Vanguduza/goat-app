package com.farmos.domain.replication

/**
 * Truthful replication states. "Saved locally" never implies "synchronised" or "backed up"; each is a
 * separate fact about a separate place.
 */
enum class ReplicationState {
    SAVED_LOCALLY,
    LAN_SYNC_PENDING,
    SYNCED_WITH_PEER,
    DRIVE_SYNC_PENDING,
    SYNCED_TO_DRIVE,
    DRIVE_UNAVAILABLE,
    PEER_UNAVAILABLE,
    CONFLICT,
    UNKNOWN,
}

/**
 * Replication states of one locally saved operation, given the last known vectors of farm peers and of
 * the Drive journal. A null vector means that place has never been observed, which is reported as
 * [ReplicationState.UNKNOWN] rather than as synchronised or pending.
 */
fun replicationStates(
    operation: OperationEnvelope,
    peerVectors: List<SyncVector>?,
    driveVector: SyncVector?,
    driveConfigured: Boolean,
    driveReachable: Boolean,
    peersReachable: Boolean,
    inConflict: Boolean,
): Set<ReplicationState> = buildSet {
    add(ReplicationState.SAVED_LOCALLY)
    if (inConflict) add(ReplicationState.CONFLICT)
    when {
        peerVectors == null -> add(ReplicationState.UNKNOWN)
        peerVectors.any { it.watermark(operation.deviceId) >= operation.deviceSequence } -> add(ReplicationState.SYNCED_WITH_PEER)
        else -> {
            add(ReplicationState.LAN_SYNC_PENDING)
            if (!peersReachable) add(ReplicationState.PEER_UNAVAILABLE)
        }
    }
    if (driveConfigured) {
        when {
            driveVector == null -> add(ReplicationState.UNKNOWN)
            driveVector.watermark(operation.deviceId) >= operation.deviceSequence -> add(ReplicationState.SYNCED_TO_DRIVE)
            else -> {
                add(ReplicationState.DRIVE_SYNC_PENDING)
                if (!driveReachable) add(ReplicationState.DRIVE_UNAVAILABLE)
            }
        }
    }
}
