package com.farmos.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

/**
 * Farm-scoped configuration set by management. A missing row means the documented defaults apply;
 * every change is also journalled as a field update so it replicates to the farm's other devices.
 */
@Entity(tableName = "farm_settings")
data class FarmSettingsEntity(
    @PrimaryKey val farmId: String,
    val currencyCode: String,
    val updatedAtEpochMillis: Long,
    val updatedByActorId: String,
)

@Dao
interface FarmSettingsDao {
    @Query("SELECT * FROM farm_settings WHERE farmId = :farmId LIMIT 1")
    suspend fun get(farmId: String): FarmSettingsEntity?

    @Upsert
    suspend fun upsert(settings: FarmSettingsEntity)
}

/** A farm's own gestation period for one species (owner decision D-019); species without a row use the defaults. */
@Entity(tableName = "farm_gestation", primaryKeys = ["farmId", "species"])
data class FarmGestationEntity(
    val farmId: String,
    val species: String,
    val earliestDays: Int,
    val typicalDays: Int,
    val latestDays: Int,
    val updatedAtEpochMillis: Long,
    val updatedByActorId: String,
)

@Dao
interface FarmGestationDao {
    @Query("SELECT * FROM farm_gestation WHERE farmId = :farmId ORDER BY species")
    suspend fun all(farmId: String): List<FarmGestationEntity>

    @Query("SELECT * FROM farm_gestation WHERE farmId = :farmId AND species = :species LIMIT 1")
    suspend fun get(farmId: String, species: String): FarmGestationEntity?

    @Upsert
    suspend fun upsert(setting: FarmGestationEntity)
}
