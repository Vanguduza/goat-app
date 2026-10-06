package com.farmos.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

/**
 * A farm customer (FOS-SALES-002/003): a buyer the farm sells to. Never deleted, only made inactive, so
 * recorded sales keep who they were for.
 */
@Entity(tableName = "farm_customers", indices = [Index(value = ["farmId", "active"])])
data class FarmCustomerEntity(
    @PrimaryKey val id: String,
    val farmId: String,
    val name: String,
    val phone: String?,
    val active: Boolean,
    val updatedAtEpochMillis: Long,
    val updatedByActorId: String,
)

@Dao
interface FarmCustomerDao {
    @Query("SELECT * FROM farm_customers WHERE farmId = :farmId ORDER BY active DESC, name, id")
    suspend fun all(farmId: String): List<FarmCustomerEntity>

    @Query("SELECT * FROM farm_customers WHERE farmId = :farmId AND id = :customerId LIMIT 1")
    suspend fun get(farmId: String, customerId: String): FarmCustomerEntity?

    /** Search-as-you-type over every active customer (D-004); paged, in a stable order. */
    @Query(
        """
        SELECT * FROM farm_customers
        WHERE farmId = :farmId AND active = 1
            AND (:pattern IS NULL OR name LIKE :pattern ESCAPE '\' OR phone LIKE :pattern ESCAPE '\')
        ORDER BY name, id
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun searchActive(farmId: String, pattern: String?, limit: Int, offset: Int): List<FarmCustomerEntity>

    @Upsert
    suspend fun upsert(customer: FarmCustomerEntity)
}
