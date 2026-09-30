package com.farmos.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

/**
 * A repeating farm task (owner decision D-020): its rule, first and optional last day, and what each
 * occurrence carries. Occurrences are derived from the series; an occurrence is stored as a farm_tasks
 * row (with this series id) only once it is completed or edited on its own, so completed history is a
 * stored snapshot that editing the series never rewrites.
 */
@Entity(tableName = "task_series", indices = [Index(value = ["farmId", "status"])])
data class TaskSeriesEntity(
    @PrimaryKey val id: String,
    val farmId: String,
    val moduleCode: String,
    val taskCode: String,
    val title: String,
    /** A [com.farmos.domain.ops.RecurrenceKind] name. */
    val recurrenceKind: String,
    val recurrenceInterval: Int,
    val startEpochDay: Long,
    val endEpochDay: Long?,
    val animalId: String?,
    /** A local account on this farm, or null. */
    val assigneeAccountId: String?,
    /** A worker record on this farm, or null. */
    val assigneeWorkerId: String?,
    /** `active`, or `ended` once the series is stopped. */
    val status: String,
    val updatedAtEpochMillis: Long,
    val updatedByActorId: String,
)

@Dao
interface TaskSeriesDao {
    @Query("SELECT * FROM task_series WHERE farmId = :farmId AND id = :seriesId LIMIT 1")
    suspend fun get(farmId: String, seriesId: String): TaskSeriesEntity?

    @Query("SELECT * FROM task_series WHERE farmId = :farmId AND status = 'active' ORDER BY startEpochDay, title, id")
    suspend fun active(farmId: String): List<TaskSeriesEntity>

    @Upsert
    suspend fun upsert(series: TaskSeriesEntity)

    /** Stored occurrences of one series (completed or individually edited), by day. */
    @Query("SELECT * FROM farm_tasks WHERE farmId = :farmId AND seriesId = :seriesId ORDER BY dueOnEpochDay, id")
    suspend fun storedOccurrences(farmId: String, seriesId: String): List<TaskEntity>
}
