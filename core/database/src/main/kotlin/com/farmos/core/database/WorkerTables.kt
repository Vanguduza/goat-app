package com.farmos.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

/**
 * A person who works on the farm, with or without a login (owner decision D-016, resolution R1). A local
 * account may link to a worker; a worker is never deleted, only made inactive, so work stays attributed.
 */
@Entity(tableName = "farm_workers", indices = [Index(value = ["farmId", "active"])])
data class FarmWorkerEntity(
    @PrimaryKey val id: String,
    val farmId: String,
    val name: String,
    val active: Boolean,
    val updatedAtEpochMillis: Long,
    val updatedByActorId: String,
)

@Dao
interface FarmWorkerDao {
    @Query("SELECT * FROM farm_workers WHERE farmId = :farmId ORDER BY active DESC, name, id")
    suspend fun all(farmId: String): List<FarmWorkerEntity>

    @Query("SELECT * FROM farm_workers WHERE farmId = :farmId AND active = 1 ORDER BY name, id")
    suspend fun active(farmId: String): List<FarmWorkerEntity>

    @Query("SELECT * FROM farm_workers WHERE farmId = :farmId AND id = :workerId LIMIT 1")
    suspend fun get(farmId: String, workerId: String): FarmWorkerEntity?

    @Upsert
    suspend fun upsert(worker: FarmWorkerEntity)
}
