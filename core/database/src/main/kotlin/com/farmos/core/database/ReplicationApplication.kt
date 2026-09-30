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
}
