package com.farmos.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * FOS-RABBIT-032 — schema 36 → 37: creates the `rabbit_weights` table. Column types and order
 * match [RabbitWeightEntity] exactly (String → TEXT, Long → INTEGER, Double → REAL; nullable
 * columns have no NOT NULL). The index name follows the Room-generated convention
 * `index_rabbit_weights_farmId_animalId`.
 *
 * Integration (owned by the schema integrator; FarmOsDatabase.kt is not touched by this batch):
 * add `RabbitWeightEntity::class` to the entities list, bump `version` to 37, and append
 * `MIGRATION_36_37_RABBIT_WEIGHT` to `ALL_MIGRATIONS`.
 */
val MIGRATION_36_37_RABBIT_WEIGHT = object : Migration(36, 37) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `rabbit_weights` (`id` TEXT NOT NULL, `farmId` TEXT NOT NULL, `animalId` TEXT NOT NULL, " +
                "`weighedAtEpochMillis` INTEGER NOT NULL, `weightKg` REAL NOT NULL, `notes` TEXT, " +
                "`recordedByActorId` TEXT, `createdAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_rabbit_weights_farmId_animalId` ON `rabbit_weights` (`farmId`, `animalId`)")
    }
}
