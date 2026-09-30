package com.farmos.core.database

import androidx.room.Dao
import androidx.room.Query

/** One animal in the herd register, with its latest recorded weight in grams if any. */
data class HerdRegisterRow(
    val id: String,
    val speciesCode: String,
    val tag: String,
    val name: String?,
    val sex: String,
    val status: String,
    val dateOfBirthEpochDay: Long?,
    val poultryKindCode: String?,
    val latestWeightGrams: Long?,
)

/**
 * Exhaustive reads for metrics and exports (owner decision D-026, resolution R10). These queries have no
 * row cap: a metric or an export over a capped list would misstate the farm.
 */
@Dao
interface FarmReportDao {
    @Query(
        """
        SELECT a.id, a.speciesCode, a.tag, a.name, a.sex, a.status, a.dateOfBirthEpochDay, a.poultryKindCode,
            (SELECT m.valueLong FROM measurements m
                WHERE m.farmId = a.farmId AND m.animalId = a.id AND m.type = 'weight'
                ORDER BY m.measuredAtEpochMillis DESC, m.id DESC LIMIT 1) AS latestWeightGrams
        FROM animals a
        WHERE a.farmId = :farmId
        ORDER BY a.speciesCode, a.tag, a.id
        """,
    )
    suspend fun herdRegister(farmId: String): List<HerdRegisterRow>
}
