package com.farmos.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * FOS-GROUP-007 — per-animal group membership link table.
 *
 * Groups previously tracked only census head counts; there was no record of which animal belongs to
 * which group. This table is the single authoritative record of an animal's current group: at most one
 * row per animal (an animal belongs to exactly one group at a time). Move history lives in the
 * replication journal as `group.animal_move.v1` operations, not here.
 *
 * INTEGRATION (FarmOsDatabase.kt, owned by the integrator — do not edit from this lane):
 * 1. Add `AnimalGroupMembershipEntity::class` to the `@Database entities` list.
 * 2. Bump `version = 36` to `version = 37`.
 * 3. Add `abstract fun groupMemberships(): AnimalGroupMembershipDao`.
 * 4. Add `MigrationGroupMove36To37` to `ALL_MIGRATIONS`.
 */
@Entity(
    tableName = "animal_group_memberships",
    primaryKeys = ["farmId", "animalId"],
    indices = [Index(value = ["farmId", "groupId"])],
)
data class AnimalGroupMembershipEntity(
    val farmId: String,
    val animalId: String,
    val groupId: String,
    val movedAtEpochMillis: Long,
)

@Dao
interface AnimalGroupMembershipDao {
    @Query("SELECT * FROM animal_group_memberships WHERE farmId = :farmId AND animalId = :animalId LIMIT 1")
    suspend fun get(farmId: String, animalId: String): AnimalGroupMembershipEntity?

    @Query("SELECT * FROM animal_group_memberships WHERE farmId = :farmId AND animalId IN (:animalIds) ORDER BY animalId")
    suspend fun getMany(farmId: String, animalIds: List<String>): List<AnimalGroupMembershipEntity>

    /** Animals whose recorded membership is [groupId], ordered deterministically by tag then id. */
    @Query(
        "SELECT a.* FROM animals a " +
            "JOIN animal_group_memberships m ON m.farmId = a.farmId AND m.animalId = a.id " +
            "WHERE m.farmId = :farmId AND m.groupId = :groupId ORDER BY a.tag, a.id",
    )
    suspend fun animalsInGroup(farmId: String, groupId: String): List<AnimalEntity>

    /** Animals of [speciesCode] with no recorded group membership (first-assignment move candidates). */
    @Query(
        "SELECT a.* FROM animals a " +
            "LEFT JOIN animal_group_memberships m ON m.farmId = a.farmId AND m.animalId = a.id " +
            "WHERE a.farmId = :farmId AND a.speciesCode = :speciesCode AND m.animalId IS NULL " +
            "ORDER BY a.tag, a.id",
    )
    suspend fun unassignedAnimals(farmId: String, speciesCode: String): List<AnimalEntity>

    @Upsert
    suspend fun upsert(membership: AnimalGroupMembershipEntity)
}

/**
 * FOS-GROUP-007 + FOS-RABBIT-032 — schema 36 to 37: creates the per-animal group membership
 * link table AND the `rabbit_weights` table in a single migration.
 *
 * P0 fix (independent review 2026-10-08): Room's MigrationContainer keys migrations by
 * (startVersion, endVersion), so two `Migration(36, 37)` objects cannot coexist — the second
 * silently overrides the first and its tables are never created. Both CREATE TABLEs (and both
 * indexes) therefore live in this one migration.
 *
 * The CREATE TABLE column orders mirror the entity field orders, matching Room's generated schema.
 */
object MigrationGroupMove36To37 : Migration(36, 37) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `animal_group_memberships` (`farmId` TEXT NOT NULL, `animalId` TEXT NOT NULL, " +
                "`groupId` TEXT NOT NULL, `movedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`farmId`, `animalId`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_animal_group_memberships_farmId_groupId` ON `animal_group_memberships` (`farmId`, `groupId`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `rabbit_weights` (`id` TEXT NOT NULL, `farmId` TEXT NOT NULL, `animalId` TEXT NOT NULL, " +
                "`weighedAtEpochMillis` INTEGER NOT NULL, `weightKg` REAL NOT NULL, `notes` TEXT, " +
                "`recordedByActorId` TEXT, `createdAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_rabbit_weights_farmId_animalId` ON `rabbit_weights` (`farmId`, `animalId`)")
    }
}
