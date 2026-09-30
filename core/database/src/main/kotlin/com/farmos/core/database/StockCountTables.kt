package com.farmos.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

/**
 * A stock count (owner decision D-021): workers record counted quantities, management reviews the
 * variances and posts them as stock adjustments or rejects the count. Counts are never deleted.
 */
@Entity(tableName = "stock_counts", indices = [Index(value = ["farmId", "status"])])
data class StockCountEntity(
    @PrimaryKey val id: String,
    val farmId: String,
    /** A [com.farmos.domain.ops.StockCountStatus] name. */
    val status: String,
    val startedByActorId: String,
    val startedAtEpochMillis: Long,
    val submittedByActorId: String?,
    val submittedAtEpochMillis: Long?,
    /** Who posted or rejected the count, and when. */
    val decidedByActorId: String?,
    val decidedAtEpochMillis: Long?,
    /** Why management rejected the count, when it did. */
    val rejectionReason: String?,
)

/** One item's count: on hand when counted (carried from the counting device) and the quantity found. */
@Entity(tableName = "stock_count_lines", indices = [Index(value = ["farmId", "countId"])])
data class StockCountLineEntity(
    @PrimaryKey val id: String,
    val farmId: String,
    val countId: String,
    val itemId: String,
    val onHandAtCountMilli: Long,
    val countedMilli: Long,
    val countedByActorId: String,
    val countedAtEpochMillis: Long,
)

@Dao
interface StockCountDao {
    @Query("SELECT * FROM stock_counts WHERE farmId = :farmId AND id = :countId LIMIT 1")
    suspend fun get(farmId: String, countId: String): StockCountEntity?

    @Query("SELECT * FROM stock_counts WHERE farmId = :farmId ORDER BY startedAtEpochMillis DESC, id")
    suspend fun all(farmId: String): List<StockCountEntity>

    @Upsert
    suspend fun upsert(count: StockCountEntity)

    @Query("SELECT * FROM stock_count_lines WHERE farmId = :farmId AND countId = :countId ORDER BY itemId")
    suspend fun lines(farmId: String, countId: String): List<StockCountLineEntity>

    @Query("SELECT * FROM stock_count_lines WHERE farmId = :farmId AND id = :lineId LIMIT 1")
    suspend fun line(farmId: String, lineId: String): StockCountLineEntity?

    @Upsert
    suspend fun upsertLine(line: StockCountLineEntity)
}
