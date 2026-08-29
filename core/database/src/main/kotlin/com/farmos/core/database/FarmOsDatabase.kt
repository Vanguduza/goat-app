package com.farmos.core.database

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.farmos.core.model.SyncState
import com.farmos.domain.goat.GoatRepository
import com.farmos.domain.goat.GoatSearchResult
import com.farmos.domain.goat.GoatSex
import com.farmos.domain.goat.GoatSnapshot
import com.farmos.domain.goat.GoatValidationResult
import com.farmos.domain.goat.GoatValidator
import com.farmos.domain.goat.LocalCommandContext
import com.farmos.domain.goat.LocalCommandResult
import com.farmos.domain.goat.RecordGoatWeight
import com.farmos.domain.goat.RegisterGoat
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(
    tableName = "animals",
    indices = [
        Index(value = ["farmId", "tag"], unique = true),
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFromServer(animal: AnimalEntity)

    @Query("SELECT * FROM animals WHERE farmId = :farmId AND id = :animalId LIMIT 1")
    suspend fun get(farmId: String, animalId: String): AnimalEntity?

    @Query("""
        SELECT * FROM animals
        WHERE farmId = :farmId
          AND speciesCode = 'goat'
          AND status != 'closed'
          AND (
              :query = ''
              OR tag LIKE '%' || :query || '%' COLLATE NOCASE
              OR COALESCE(name, '') LIKE '%' || :query || '%' COLLATE NOCASE
          )
        ORDER BY CASE WHEN tag = :query THEN 0 ELSE 1 END, tag
        LIMIT :limit
    """)
    suspend fun searchGoats(farmId: String, query: String, limit: Int): List<AnimalEntity>
}

@Dao
interface MeasurementDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(measurement: MeasurementEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFromServer(measurement: MeasurementEntity)

    @Query("SELECT * FROM measurements WHERE farmId = :farmId AND animalId = :animalId AND type = 'weight' ORDER BY measuredAtEpochMillis DESC LIMIT 1")
    suspend fun latestWeight(farmId: String, animalId: String): MeasurementEntity?
}

@Dao
interface OutboxDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: OutboxEntity)

    @Query("SELECT * FROM sync_outbox WHERE state IN ('PENDING','RETRY_WAIT') AND (nextAttemptAtEpochMillis IS NULL OR nextAttemptAtEpochMillis <= :now) ORDER BY createdAtEpochMillis LIMIT :limit")
    suspend fun pending(now: Long, limit: Int): List<OutboxEntity>

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
interface SyncCursorDao {
    @Query("SELECT changeCursor FROM sync_cursors WHERE farmId = :farmId LIMIT 1")
    suspend fun get(farmId: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cursor: SyncCursorEntity)
}

@Database(
    entities = [AnimalEntity::class, MeasurementEntity::class, OutboxEntity::class, SyncCursorEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class FarmOsDatabase : RoomDatabase() {
    abstract fun animals(): AnimalDao
    abstract fun measurements(): MeasurementDao
    abstract fun outbox(): OutboxDao
    abstract fun syncCursors(): SyncCursorDao
}

class RoomGoatRepository(
    private val database: FarmOsDatabase,
    private val farmId: String,
    private val json: Json = Json { encodeDefaults = true },
) : GoatRepository {

    override suspend fun registerGoat(command: RegisterGoat, context: LocalCommandContext): LocalCommandResult {
        require(context.farmId == farmId) { "Farm context mismatch" }
        val validation = GoatValidator.register(command)
        require(validation is GoatValidationResult.Valid) {
            (validation as GoatValidationResult.Invalid).message
        }

        database.withTransaction {
            database.animals().insert(
                AnimalEntity(
                    id = command.animalId,
                    farmId = farmId,
                    tag = command.tag.trim(),
                    name = command.name?.trim()?.takeIf { it.isNotEmpty() },
                    speciesCode = "goat",
                    sex = command.sex.name,
                    status = "active",
                    dateOfBirthEpochDay = command.dateOfBirthEpochDay,
                    updatedAtEpochMillis = context.occurredAtEpochMillis,
                ),
            )
            database.outbox().insert(
                OutboxEntity(
                    mutationId = context.mutationId,
                    farmId = farmId,
                    actorId = context.actorId,
                    deviceId = context.deviceId,
                    commandName = "goat.register.v1",
                    commandSchemaVersion = 1,
                    aggregateType = "animal",
                    aggregateId = command.animalId,
                    expectedStreamVersion = 0,
                    payloadJson = json.encodeToString(command),
                    occurredAtEpochMillis = context.occurredAtEpochMillis,
                    createdAtEpochMillis = System.currentTimeMillis(),
                    state = SyncState.PENDING.name,
                    attemptCount = 0,
                    nextAttemptAtEpochMillis = null,
                    lastErrorCode = null,
                    serverEventId = null,
                    serverStreamVersion = null,
                ),
            )
        }
        return LocalCommandResult(context.mutationId, command.animalId, locallyDurable = true)
    }

    override suspend fun recordWeight(command: RecordGoatWeight, context: LocalCommandContext): LocalCommandResult {
        require(context.farmId == farmId) { "Farm context mismatch" }
        val validation = GoatValidator.weight(command)
        require(validation is GoatValidationResult.Valid) {
            (validation as GoatValidationResult.Invalid).message
        }
        requireNotNull(database.animals().get(farmId, command.animalId)) { "Goat not found" }

        database.withTransaction {
            database.measurements().insert(
                MeasurementEntity(
                    id = command.measurementId,
                    farmId = farmId,
                    animalId = command.animalId,
                    type = "weight",
                    valueLong = command.weightGrams,
                    unit = "g",
                    measuredAtEpochMillis = command.measuredAtEpochMillis,
                ),
            )
            database.outbox().insert(
                OutboxEntity(
                    mutationId = context.mutationId,
                    farmId = farmId,
                    actorId = context.actorId,
                    deviceId = context.deviceId,
                    commandName = "goat.record_weight.v1",
                    commandSchemaVersion = 1,
                    aggregateType = "animal",
                    aggregateId = command.animalId,
                    expectedStreamVersion = null,
                    payloadJson = json.encodeToString(command),
                    occurredAtEpochMillis = context.occurredAtEpochMillis,
                    createdAtEpochMillis = System.currentTimeMillis(),
                    state = SyncState.PENDING.name,
                    attemptCount = 0,
                    nextAttemptAtEpochMillis = null,
                    lastErrorCode = null,
                    serverEventId = null,
                    serverStreamVersion = null,
                ),
            )
        }
        return LocalCommandResult(context.mutationId, command.animalId, locallyDurable = true)
    }

    override suspend fun getGoat(animalId: String): GoatSnapshot? {
        val animal = database.animals().get(farmId, animalId) ?: return null
        val weight = database.measurements().latestWeight(farmId, animalId)
        return GoatSnapshot(
            animalId = animal.id,
            farmId = animal.farmId,
            tag = animal.tag,
            name = animal.name,
            sex = GoatSex.valueOf(animal.sex),
            latestWeightGrams = weight?.valueLong,
            syncPending = database.outbox().hasPending(farmId, animalId),
        )
    }

    override suspend fun searchGoats(query: String, limit: Int): List<GoatSearchResult> =
        database.animals().searchGoats(farmId, query.trim(), limit.coerceIn(1, 100)).map { animal ->
            GoatSearchResult(
                animalId = animal.id,
                tag = animal.tag,
                name = animal.name,
                status = animal.status,
            )
        }
}
