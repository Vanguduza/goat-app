package com.farmos.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * A photo or document attached to a farm record (D-015). The bytes are kept outside the database, named by
 * [contentSha256]; this row is the replicated metadata and may reach a device before the bytes do.
 */
@Entity(tableName = "attachments", indices = [Index(value = ["farmId", "ownerType", "ownerId"])])
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val farmId: String,
    val ownerType: String,
    val ownerId: String,
    val contentSha256: String,
    val byteSize: Long,
    val mediaType: String,
    val displayName: String,
    val attachedAtEpochMillis: Long,
    val attachedByActorId: String,
)

@Dao
interface AttachmentDao {
    /** Replay of the same attachment operation is a no-op. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(row: AttachmentEntity)

    @Query("SELECT * FROM attachments WHERE farmId = :farmId AND id = :attachmentId LIMIT 1")
    suspend fun get(farmId: String, attachmentId: String): AttachmentEntity?

    /** Every attachment on one record, newest first; no row cap. */
    @Query("SELECT * FROM attachments WHERE farmId = :farmId AND ownerType = :ownerType AND ownerId = :ownerId ORDER BY attachedAtEpochMillis DESC, id")
    suspend fun forOwner(farmId: String, ownerType: String, ownerId: String): List<AttachmentEntity>
}
