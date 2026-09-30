package com.farmos.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.farmos.domain.replication.OperationEnvelope

/**
 * Makes a received operation take effect in this device's domain tables. Each application runs in its
 * own transaction after the operation is journalled; a failure is recorded, never loses the operation,
 * and is retried when later operations arrive.
 */
fun interface OperationApplier {
    suspend fun apply(database: FarmOsDatabase, operation: OperationEnvelope)
}

/** How a received operation stands against this device's domain tables. */
enum class ApplicationState {
    APPLIED,

    /** The applier refused it, for example because an operation it depends on has not arrived yet. */
    FAILED,

    /** This app version has no applier for the operation type yet; it stays journalled until one exists. */
    AWAITING_APPLIER,
}

/** Application state of one received operation. Local operations are applied when they are written. */
@Entity(
    tableName = "replication_applications",
    indices = [Index(value = ["farmId", "state"])],
)
data class ReplicationApplicationEntity(
    @PrimaryKey val operationId: String,
    val farmId: String,
    val state: String,
    val reason: String?,
    val attempts: Int,
    val updatedAtEpochMillis: Long,
)

@Dao
interface ReplicationApplicationDao {
    @Upsert
    suspend fun upsert(application: ReplicationApplicationEntity)

    @Query("SELECT * FROM replication_applications WHERE farmId = :farmId AND operationId = :operationId LIMIT 1")
    suspend fun get(farmId: String, operationId: String): ReplicationApplicationEntity?

    /** Operations not yet applied, joined to the journal so they can be retried in business order. */
    @Query(
        """
        SELECT o.* FROM replication_operations o
        JOIN replication_applications a ON a.operationId = o.operationId
        WHERE a.farmId = :farmId AND a.state != 'APPLIED'
        ORDER BY o.businessTimeEpochMillis, o.deviceId, o.deviceSequence
        """,
    )
    suspend fun unapplied(farmId: String): List<ReplicationOperationEntity>

    @Query("SELECT COUNT(*) FROM replication_applications WHERE farmId = :farmId AND state = :state")
    suspend fun count(farmId: String, state: String): Long

    /** Received operations that have not taken effect, newest business time first, for review. */
    @Query(
        """
        SELECT o.operationId, o.operationType, o.deviceId, o.actorId, o.businessTimeEpochMillis, a.state, a.reason, a.attempts
        FROM replication_applications a JOIN replication_operations o ON o.operationId = a.operationId
        WHERE a.farmId = :farmId AND a.state != 'APPLIED'
        ORDER BY o.businessTimeEpochMillis DESC, o.operationId
        LIMIT :limit
        """,
    )
    suspend fun unappliedForReview(farmId: String, limit: Int): List<UnappliedOperation>
}

/** One received operation awaiting application, with why. */
data class UnappliedOperation(
    val operationId: String,
    val operationType: String,
    val deviceId: String,
    val actorId: String,
    val businessTimeEpochMillis: Long,
    val state: String,
    val reason: String?,
    val attempts: Int,
)

/**
 * How far a farm peer has confirmed holding this device's own operations, from the peer's own vector at
 * the end of a completed sync session. "Synchronised" means another farm device holds the change.
 */
@Entity(tableName = "replication_peer_marks", primaryKeys = ["farmId", "peerDeviceId"])
data class ReplicationPeerMarkEntity(
    val farmId: String,
    val peerDeviceId: String,
    val holdsOwnThrough: Long,
    val atEpochMillis: Long,
)

@Dao
interface ReplicationPeerMarkDao {
    @Query("SELECT * FROM replication_peer_marks WHERE farmId = :farmId AND peerDeviceId = :peerDeviceId LIMIT 1")
    suspend fun get(farmId: String, peerDeviceId: String): ReplicationPeerMarkEntity?

    @Upsert
    suspend fun upsert(mark: ReplicationPeerMarkEntity)

    @Query("SELECT MAX(holdsOwnThrough) FROM replication_peer_marks WHERE farmId = :farmId")
    suspend fun highestHeld(farmId: String): Long?
}

/** Records a peer's confirmation; a mark never moves backwards. */
suspend fun FarmOsDatabase.recordPeerHolds(farmId: String, peerDeviceId: String, holdsOwnThrough: Long, atEpochMillis: Long) {
    val existing = replicationPeerMarks().get(farmId, peerDeviceId)
    if (existing == null || existing.holdsOwnThrough < holdsOwnThrough) {
        replicationPeerMarks().upsert(ReplicationPeerMarkEntity(farmId, peerDeviceId, holdsOwnThrough, atEpochMillis))
    }
}

/** This device's own operations that no other farm device has confirmed holding yet. */
suspend fun FarmOsDatabase.unsharedLocalOperations(farmId: String, deviceId: String): Long {
    val issued = replication().device(farmId, deviceId)?.lastReportedOwnSequence ?: 0
    return (issued - (replicationPeerMarks().highestHeld(farmId) ?: 0)).coerceAtLeast(0)
}
