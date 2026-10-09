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
 * 4. Cover `MigrationGroupMove36To37` in the merged `MIGRATION_36_37` in `ALL_MIGRATIONS`
 *    (never register it as a second Migration(36, 37) — Room keeps only one object per (start, end) key).
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
 * FOS-GROUP-007 — schema 36 to 37: creates the per-animal group membership link table.
 * The CREATE TABLE column order mirrors the entity field order, matching Room's generated schema.
 *
 * This object owns ONLY the SQL body. It is invoked by the merged MIGRATION_36_37 in FarmOsDatabase.kt
 * and must NOT be registered in ALL_MIGRATIONS itself: Room keys migrations by (startVersion, endVersion)
 * and silently keeps only the last object registered for a duplicate key, so a second Migration(36, 37)
 * would drop one table from the chain.
 */
object MigrationGroupMove36To37 : Migration(36, 37) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `animal_group_memberships` (`farmId` TEXT NOT NULL, `animalId` TEXT NOT NULL, " +
                "`groupId` TEXT NOT NULL, `movedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`farmId`, `animalId`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_animal_group_memberships_farmId_groupId` ON `animal_group_memberships` (`farmId`, `groupId`)")
    }
}
