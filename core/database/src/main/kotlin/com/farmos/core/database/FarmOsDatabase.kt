package com.farmos.core.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(
    tableName = "animals",
    indices = [
        Index(value = ["farmId", "id"], unique = true),
        Index(value = ["farmId", "speciesCode", "tag"], unique = true),
        Index(value = ["farmId", "speciesCode", "status"]),
    ],
)
data class AnimalEntity(
    @PrimaryKey val id: String,
    val farmId: String,
    val tag: String,
    val name: String?,
    val speciesCode: String,
    val sex: String,
    val status: String,
    val dateOfBirthEpochDay: Long?,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "measurements",
    foreignKeys = [
        ForeignKey(
            entity = AnimalEntity::class,
            parentColumns = ["farmId", "id"],
            childColumns = ["farmId", "animalId"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [Index(value = ["farmId", "animalId", "measuredAtEpochMillis"])],
)
data class MeasurementEntity(
    @PrimaryKey val id: String,
    val farmId: String,
    val animalId: String,
    val type: String,
    val valueLong: Long,
    val unit: String,
    val measuredAtEpochMillis: Long,
)

@Entity(
    tableName = "sync_outbox",
    indices = [
        Index(value = ["farmId", "state", "nextAttemptAtEpochMillis"]),
        Index(value = ["farmId", "aggregateId"]),
    ],
)
data class OutboxEntity(
    @PrimaryKey val mutationId: String,
    val farmId: String,
    val actorId: String,
    val deviceId: String,
    val commandName: String,
    val commandSchemaVersion: Int,
    val aggregateType: String,
    val aggregateId: String,
    @ColumnInfo(defaultValue = "0") val aggregateOrdinal: Long,
    val expectedStreamVersion: Long?,
    val payloadJson: String,
    val occurredAtEpochMillis: Long,
    val createdAtEpochMillis: Long,
    val state: String,
    val attemptCount: Int,
    val nextAttemptAtEpochMillis: Long?,
    val lastErrorCode: String?,
    val serverEventId: String?,
    val serverStreamVersion: Long?,
)

@Entity(
    tableName = "aggregate_versions",
    primaryKeys = ["farmId", "aggregateType", "aggregateId"],
)
data class AggregateVersionEntity(
    val farmId: String,
    val aggregateType: String,
    val aggregateId: String,
    val streamVersion: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(tableName = "sync_cursors")
data class SyncCursorEntity(
    @PrimaryKey val farmId: String,
    val changeCursor: Long,
    val updatedAtEpochMillis: Long,
)

@Dao
interface AnimalDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(animal: AnimalEntity)

    @Upsert
    suspend fun upsertFromServer(animal: AnimalEntity)

    @Query("SELECT * FROM animals WHERE farmId = :farmId AND id = :animalId LIMIT 1")
    suspend fun get(farmId: String, animalId: String): AnimalEntity?

    @Query("""
        SELECT * FROM animals
        WHERE farmId = :farmId
          AND speciesCode = :speciesCode
          AND status != 'closed'
          AND (
              :query = ''
              OR tag LIKE '%' || :query || '%' COLLATE NOCASE
              OR COALESCE(name, '') LIKE '%' || :query || '%' COLLATE NOCASE
          )
        ORDER BY CASE WHEN tag = :query THEN 0 ELSE 1 END, tag
        LIMIT :limit
    """)
    suspend fun searchBySpecies(
        farmId: String,
        speciesCode: String,
        query: String,
        limit: Int,
    ): List<AnimalEntity>
}

@Dao
interface MeasurementDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(measurement: MeasurementEntity)

    @Upsert
    suspend fun upsertFromServer(measurement: MeasurementEntity)

    @Query("SELECT * FROM measurements WHERE farmId = :farmId AND animalId = :animalId AND type = :type ORDER BY measuredAtEpochMillis DESC LIMIT 1")
    suspend fun latest(farmId: String, animalId: String, type: String): MeasurementEntity?
}

@Dao
interface OutboxDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: OutboxEntity)

    @Query("""
        SELECT candidate.*
        FROM sync_outbox AS candidate
        WHERE candidate.state IN ('PENDING','RETRY_WAIT','IN_FLIGHT')
          AND (candidate.nextAttemptAtEpochMillis IS NULL OR candidate.nextAttemptAtEpochMillis <= :now)
          AND NOT EXISTS (
              SELECT 1
              FROM sync_outbox AS blocker
              WHERE blocker.farmId = candidate.farmId
                AND blocker.aggregateType = candidate.aggregateType
                AND blocker.aggregateId = candidate.aggregateId
                AND blocker.aggregateOrdinal < candidate.aggregateOrdinal
                AND blocker.state != 'ACKNOWLEDGED'
          )
        ORDER BY candidate.aggregateOrdinal, candidate.mutationId
        LIMIT :limit
    """)
    suspend fun pending(now: Long, limit: Int): List<OutboxEntity>

    @Query("""
        SELECT COALESCE(MAX(aggregateOrdinal), 0) + 1
        FROM sync_outbox
        WHERE farmId = :farmId
          AND aggregateType = :aggregateType
          AND aggregateId = :aggregateId
    """)
    suspend fun nextAggregateOrdinal(
        farmId: String,
        aggregateType: String,
        aggregateId: String,
    ): Long

    @Query("""
        SELECT COUNT(*)
        FROM sync_outbox
        WHERE farmId = :farmId
          AND aggregateType = :aggregateType
          AND aggregateId = :aggregateId
          AND state != 'ACKNOWLEDGED'
    """)
    suspend fun countUnacknowledgedForAggregate(
        farmId: String,
        aggregateType: String,
        aggregateId: String,
    ): Long

    @Query("UPDATE sync_outbox SET state = :state, attemptCount = :attemptCount, nextAttemptAtEpochMillis = :nextAttemptAt, lastErrorCode = :errorCode, serverEventId = :serverEventId, serverStreamVersion = :serverStreamVersion WHERE mutationId = :mutationId")
    suspend fun updateState(
        mutationId: String,
        state: String,
        attemptCount: Int,
        nextAttemptAt: Long?,
        errorCode: String?,
        serverEventId: String?,
        serverStreamVersion: Long?,
    )

    @Query("SELECT EXISTS(SELECT 1 FROM sync_outbox WHERE farmId = :farmId AND aggregateId = :aggregateId AND state != 'ACKNOWLEDGED')")
    suspend fun hasPending(farmId: String, aggregateId: String): Boolean
}

@Dao
interface AggregateVersionDao {
    @Query("SELECT streamVersion FROM aggregate_versions WHERE farmId = :farmId AND aggregateType = :aggregateType AND aggregateId = :aggregateId LIMIT 1")
    suspend fun getVersion(farmId: String, aggregateType: String, aggregateId: String): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfMissing(entity: AggregateVersionEntity): Long

    @Query("""
        UPDATE aggregate_versions
        SET streamVersion = :streamVersion,
            updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE farmId = :farmId
          AND aggregateType = :aggregateType
          AND aggregateId = :aggregateId
          AND streamVersion < :streamVersion
    """)
    suspend fun advanceExisting(
        farmId: String,
        aggregateType: String,
        aggregateId: String,
        streamVersion: Long,
        updatedAtEpochMillis: Long,
    )

    @Transaction
    suspend fun advance(
        farmId: String,
        aggregateType: String,
        aggregateId: String,
        streamVersion: Long,
        updatedAtEpochMillis: Long,
    ) {
        val inserted = insertIfMissing(
            AggregateVersionEntity(
                farmId = farmId,
                aggregateType = aggregateType,
                aggregateId = aggregateId,
                streamVersion = streamVersion,
                updatedAtEpochMillis = updatedAtEpochMillis,
            ),
        )
        if (inserted == -1L) {
            advanceExisting(
                farmId = farmId,
                aggregateType = aggregateType,
                aggregateId = aggregateId,
                streamVersion = streamVersion,
                updatedAtEpochMillis = updatedAtEpochMillis,
            )
        }
    }
}

@Dao
interface SyncCursorDao {
    @Query("SELECT changeCursor FROM sync_cursors WHERE farmId = :farmId LIMIT 1")
    suspend fun get(farmId: String): Long?

    @Upsert
    suspend fun upsert(cursor: SyncCursorEntity)
}

@Database(
    entities = [
        AnimalEntity::class,
        MeasurementEntity::class,
        OutboxEntity::class,
        AggregateVersionEntity::class,
        SyncCursorEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class FarmOsDatabase : RoomDatabase() {
    abstract fun animals(): AnimalDao
    abstract fun measurements(): MeasurementDao
    abstract fun outbox(): OutboxDao
    abstract fun aggregateVersions(): AggregateVersionDao
    abstract fun syncCursors(): SyncCursorDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE sync_outbox ADD COLUMN aggregateOrdinal INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL("UPDATE sync_outbox SET aggregateOrdinal = rowid WHERE aggregateOrdinal = 0")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS aggregate_versions (
                        farmId TEXT NOT NULL,
                        aggregateType TEXT NOT NULL,
                        aggregateId TEXT NOT NULL,
                        streamVersion INTEGER NOT NULL,
                        updatedAtEpochMillis INTEGER NOT NULL,
                        PRIMARY KEY(farmId, aggregateType, aggregateId)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO aggregate_versions(
                        farmId,
                        aggregateType,
                        aggregateId,
                        streamVersion,
                        updatedAtEpochMillis
                    )
                    SELECT
                        farmId,
                        aggregateType,
                        aggregateId,
                        MAX(serverStreamVersion),
                        MAX(createdAtEpochMillis)
                    FROM sync_outbox
                    WHERE state = 'ACKNOWLEDGED'
                      AND serverStreamVersion IS NOT NULL
                    GROUP BY farmId, aggregateType, aggregateId
                    """.trimIndent(),
                )
            }
        }
    }
}
