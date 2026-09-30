package com.farmos.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Append-only animal exits (owner decision D-022, resolution R6): a death, cull or sale, or a reversal of
 * one ([kind] `REVERSAL` with [reversesExitId]). Rows are never updated or deleted.
 */
@Entity(tableName = "animal_exits", indices = [Index(value = ["farmId", "animalId"])])
data class AnimalExitEntity(
    @PrimaryKey val id: String,
    val farmId: String,
    val animalId: String,
    /** DEATH, CULL, SALE or REVERSAL. */
    val kind: String,
    val occurredEpochDay: Long,
    /** A death cause category, for a death. */
    val deathCause: String?,
    /** Why: the cull reason, a note on a death, or why an exit was reversed. */
    val reason: String?,
    val buyer: String?,
    val priceMinor: Long?,
    val currency: String?,
    val reversesExitId: String?,
    val recordedByActorId: String,
    val recordedAtEpochMillis: Long,
)

@Dao
interface AnimalExitDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(exit: AnimalExitEntity): Long

    @Query("SELECT * FROM animal_exits WHERE farmId = :farmId AND animalId = :animalId ORDER BY recordedAtEpochMillis, id")
    suspend fun forAnimal(farmId: String, animalId: String): List<AnimalExitEntity>

    @Query("SELECT * FROM animal_exits WHERE farmId = :farmId AND id = :exitId LIMIT 1")
    suspend fun get(farmId: String, exitId: String): AnimalExitEntity?
}
