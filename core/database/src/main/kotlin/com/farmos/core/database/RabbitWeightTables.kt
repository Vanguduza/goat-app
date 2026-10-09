package com.farmos.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * FOS-RABBIT-032 — one governed rabbit weighing. Species-specific; the goat `measurements`
 * table is never reused. The [animalId] references an `animals` row with speciesCode "rabbit"
 * (FK enforced by the RecordRabbitWeight handler's existence check, not by SQLite). Journal
 * columns follow the rabbit lifecycle-table discipline: [farmId] scopes every query,
 * [recordedByActorId] names the actor from the command context, [createdAtEpochMillis] is the
 * command's business time.
 */
@Entity(
    tableName = "rabbit_weights",
    indices = [Index(value = ["farmId", "animalId"])],
)
data class RabbitWeightEntity(
    @PrimaryKey val id: String,
    val farmId: String,
    val animalId: String,
    val weighedAtEpochMillis: Long,
    val weightKg: Double,
    val notes: String?,
    val recordedByActorId: String?,
    val createdAtEpochMillis: Long,
)
