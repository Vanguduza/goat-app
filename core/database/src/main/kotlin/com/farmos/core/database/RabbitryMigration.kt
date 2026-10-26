package com.farmos.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Additive upgrade. Existing farms retain every record; no historical occupants/services are invented. */
object RabbitryMigration37To38 : Migration(37,38) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_rabbit_cages_farmId_id` ON `rabbit_cages` (`farmId`, `id`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_rabbit_nest_boxes_farmId_id` ON `rabbit_nest_boxes` (`farmId`, `id`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_rabbit_breeding_waves_farmId_id` ON `rabbit_breeding_waves` (`farmId`, `id`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `rabbit_housing` (`farmId` TEXT NOT NULL, `animalId` TEXT NOT NULL, `cageId` TEXT, `role` TEXT, `changedAtEpochMillis` INTEGER NOT NULL, `version` INTEGER NOT NULL, PRIMARY KEY(`farmId`, `animalId`), FOREIGN KEY(`farmId`, `animalId`) REFERENCES `animals`(`farmId`, `id`) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(`farmId`, `cageId`) REFERENCES `rabbit_cages`(`farmId`, `id`) ON UPDATE NO ACTION ON DELETE NO ACTION)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_rabbit_housing_farmId_cageId` ON `rabbit_housing` (`farmId`, `cageId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `rabbit_buck_services` (`farmId` TEXT NOT NULL, `id` TEXT NOT NULL, `cageId` TEXT NOT NULL, `buckId` TEXT NOT NULL, `waveId` TEXT, `startedAtEpochMillis` INTEGER NOT NULL, `endedAtEpochMillis` INTEGER, `notes` TEXT NOT NULL, `version` INTEGER NOT NULL, PRIMARY KEY(`farmId`, `id`), FOREIGN KEY(`farmId`, `cageId`) REFERENCES `rabbit_cages`(`farmId`, `id`) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(`farmId`, `buckId`) REFERENCES `animals`(`farmId`, `id`) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(`farmId`, `waveId`) REFERENCES `rabbit_breeding_waves`(`farmId`, `id`) ON UPDATE NO ACTION ON DELETE NO ACTION)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_rabbit_buck_services_farmId_cageId` ON `rabbit_buck_services` (`farmId`, `cageId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_rabbit_buck_services_farmId_buckId` ON `rabbit_buck_services` (`farmId`, `buckId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_rabbit_buck_services_farmId_waveId` ON `rabbit_buck_services` (`farmId`, `waveId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `rabbit_pair_matings` (`farmId` TEXT NOT NULL, `id` TEXT NOT NULL, `cageId` TEXT NOT NULL, `doeId` TEXT NOT NULL, `buckId` TEXT NOT NULL, `waveId` TEXT, `matedAtEpochMillis` INTEGER NOT NULL, `notes` TEXT NOT NULL, PRIMARY KEY(`farmId`, `id`), FOREIGN KEY(`farmId`, `cageId`) REFERENCES `rabbit_cages`(`farmId`, `id`) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(`farmId`, `doeId`) REFERENCES `animals`(`farmId`, `id`) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(`farmId`, `buckId`) REFERENCES `animals`(`farmId`, `id`) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(`farmId`, `waveId`) REFERENCES `rabbit_breeding_waves`(`farmId`, `id`) ON UPDATE NO ACTION ON DELETE NO ACTION)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_rabbit_pair_matings_farmId_cageId` ON `rabbit_pair_matings` (`farmId`, `cageId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_rabbit_pair_matings_farmId_doeId` ON `rabbit_pair_matings` (`farmId`, `doeId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_rabbit_pair_matings_farmId_buckId` ON `rabbit_pair_matings` (`farmId`, `buckId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_rabbit_pair_matings_farmId_waveId` ON `rabbit_pair_matings` (`farmId`, `waveId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `rabbit_nest_assignments` (`farmId` TEXT NOT NULL, `boxId` TEXT NOT NULL, `doeId` TEXT, `waveId` TEXT, `changedAtEpochMillis` INTEGER NOT NULL, `version` INTEGER NOT NULL, PRIMARY KEY(`farmId`, `boxId`), FOREIGN KEY(`farmId`, `boxId`) REFERENCES `rabbit_nest_boxes`(`farmId`, `id`) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(`farmId`, `doeId`) REFERENCES `animals`(`farmId`, `id`) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(`farmId`, `waveId`) REFERENCES `rabbit_breeding_waves`(`farmId`, `id`) ON UPDATE NO ACTION ON DELETE NO ACTION)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_rabbit_nest_assignments_farmId_doeId` ON `rabbit_nest_assignments` (`farmId`, `doeId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_rabbit_nest_assignments_farmId_waveId` ON `rabbit_nest_assignments` (`farmId`, `waveId`)")
    }
}
