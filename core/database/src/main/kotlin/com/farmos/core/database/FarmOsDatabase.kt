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
    @ColumnInfo(defaultValue = "NULL") val poultryKindCode: String? = null,
    val updatedAtEpochMillis: Long,
)

/** Exhaustive counts for one species and status; [young] counts animals born fewer than 365 days ago. */
data class HerdCountRow(val active: Int, val females: Int, val males: Int, val young: Int)

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

@Entity(tableName = "farm_tasks", indices = [Index(value = ["farmId", "status", "dueOnEpochDay"])])
data class TaskEntity(
    @PrimaryKey val id: String,
    val farmId: String,
    val moduleCode: String,
    val taskCode: String,
    val title: String,
    val dueOnEpochDay: Long,
    val status: String,
    val animalId: String?,
    val cageId: String?,
    val waveId: String?,
    val updatedAtEpochMillis: Long,
    /** The task series this occurrence belongs to (D-020), or null for a one-off task. */
    val seriesId: String? = null,
    /** The series day this stored occurrence stands for; its due day may have been moved. Null without a series. */
    val occurrenceEpochDay: Long? = null,
    /** Assigned to a local account on this farm, or null. */
    val assigneeAccountId: String? = null,
    /** Assigned to a worker record on this farm, or null. */
    val assigneeWorkerId: String? = null,
)

@Entity(tableName = "kidding_events", indices = [Index(value = ["farmId", "damId"])])
data class KiddingEntity(
    @PrimaryKey val id: String,
    val farmId: String,
    val damId: String,
    val bornCount: Int,
    val liveCount: Int,
    val deadCount: Int,
    val occurredEpochDay: Long,
)

@Entity(tableName = "rabbit_cages", indices = [Index(value = ["farmId", "code"], unique = true)])
data class RabbitCageEntity(
    @PrimaryKey val id: String,
    val farmId: String,
    val code: String,
    val doeCapacity: Int,
)

@Entity(tableName = "rabbit_nest_boxes", indices = [Index(value = ["farmId", "cageId", "code"], unique = true)])
data class RabbitNestBoxEntity(
    @PrimaryKey val id: String,
    val farmId: String,
    val cageId: String,
    val code: String,
    val status: String,
)

@Entity(tableName = "rabbit_breeding_waves", indices = [Index(value = ["farmId", "cageId"])])
data class RabbitWaveEntity(
    @PrimaryKey val id: String,
    val farmId: String,
    val cageId: String,
    val packId: String,
    val doeCount: Int,
    val matingEpochDay: Long,
    val nestInEpochDay: Long,
    val kindlingEpochDay: Long,
    val nestOutEpochDay: Long,
    val rebreedEpochDay: Long,
    val weanEpochDay: Long,
)

@Entity(tableName = "health_observations", indices = [Index(value = ["farmId", "occurredAtEpochMillis"])])
data class HealthObservationEntity(
    @PrimaryKey val id: String,
    val farmId: String,
    val animalId: String?,
    val speciesCode: String,
    val signs: String,
    val firstAidApplied: String?,
    val redFlag: Boolean,
    val occurredAtEpochMillis: Long,
)

@Entity(tableName = "money_records", indices = [Index(value = ["farmId", "occurredEpochDay"])])
data class MoneyRecordEntity(
    @PrimaryKey val id: String,
    val farmId: String,
    val kind: String,
    val categoryCode: String,
    val amountMinor: Long,
    val currency: String,
    val occurredEpochDay: Long,
    val note: String?,
)

@Entity(tableName = "inventory_items", indices = [Index(value = ["farmId", "sku"], unique = true)])
data class InventoryItemEntity(
    @PrimaryKey val id: String,
    val farmId: String,
    val sku: String,
    val name: String,
    val unit: String,
    val quantityMilli: Long,
    val updatedAtEpochMillis: Long,
    val reorderMilli: Long = 0,
)

@Entity(tableName = "inventory_movements", indices = [Index(value = ["farmId", "itemId"])])
data class InventoryMovementEntity(
    @PrimaryKey val id: String,
    val farmId: String,
    val itemId: String,
    val direction: String,
    val quantityMilli: Long,
    val occurredAtEpochMillis: Long,
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

    @Query("SELECT * FROM animals WHERE farmId = :farmId AND id IN (:animalIds) ORDER BY id")
    suspend fun getMany(farmId: String, animalIds: List<String>): List<AnimalEntity>

    /**
     * Whole-farm selector search. Covers every animal of the farm (optionally one species and sex), never a
     * capped presentation list; ordered deterministically by tag then id and paged by [limit]/[offset].
     * [pattern] is an escaped LIKE pattern matched against tag or name, or null for all animals.
     */
    @Query("""
        SELECT * FROM animals
        WHERE farmId = :farmId
          AND (:speciesCode IS NULL OR speciesCode = :speciesCode)
          AND (:sex IS NULL OR sex = :sex)
          AND (:pattern IS NULL OR tag LIKE :pattern ESCAPE '\' OR name LIKE :pattern ESCAPE '\')
        ORDER BY tag, id
        LIMIT :limit OFFSET :offset
    """)
    suspend fun search(
        farmId: String,
        speciesCode: String?,
        sex: String?,
        pattern: String?,
        limit: Int,
        offset: Int,
    ): List<AnimalEntity>

    @Query("""
        SELECT * FROM animals
        WHERE farmId = :farmId
          AND speciesCode = :speciesCode
          AND status != 'closed'
        ORDER BY tag
        LIMIT :limit
    """)
    suspend fun listBySpecies(
        farmId: String,
        speciesCode: String,
        limit: Int,
    ): List<AnimalEntity>

    @Query(
        """
        SELECT COUNT(*) AS active,
            COALESCE(SUM(CASE WHEN sex = 'FEMALE' THEN 1 ELSE 0 END), 0) AS females,
            COALESCE(SUM(CASE WHEN sex = 'MALE' THEN 1 ELSE 0 END), 0) AS males,
            COALESCE(SUM(CASE WHEN dateOfBirthEpochDay IS NOT NULL AND :todayEpochDay - dateOfBirthEpochDay < 365 THEN 1 ELSE 0 END), 0) AS young
        FROM animals WHERE farmId = :farmId AND speciesCode = :speciesCode AND status = :status
        """,
    )
    suspend fun herdCounts(farmId: String, speciesCode: String, status: String, todayEpochDay: Long): HerdCountRow

    /** Same filter as [listBySpecies] (every status except closed), counted over all rows. */
    @Query("SELECT COUNT(*) FROM animals WHERE farmId = :farmId AND speciesCode = :speciesCode AND status != 'closed'")
    suspend fun countBySpecies(farmId: String, speciesCode: String): Int

    @Query(
        """
        UPDATE animals
        SET status = :status,
            updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE farmId = :farmId AND id = :animalId
        """,
    )
    suspend fun updateStatus(
        farmId: String,
        animalId: String,
        status: String,
        updatedAtEpochMillis: Long,
    )

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

    @Query("""
        SELECT * FROM animals
        WHERE farmId = :farmId
          AND status != 'closed'
          AND (
              :query = ''
              OR tag LIKE '%' || :query || '%' COLLATE NOCASE
              OR COALESCE(name, '') LIKE '%' || :query || '%' COLLATE NOCASE
              OR speciesCode LIKE '%' || :query || '%' COLLATE NOCASE
          )
        ORDER BY
            CASE WHEN tag = :query COLLATE NOCASE THEN 0 ELSE 1 END,
            speciesCode,
            tag
        LIMIT :limit
    """)
    suspend fun searchAll(
        farmId: String,
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

    @Query("SELECT * FROM measurements WHERE farmId = :farmId AND animalId = :animalId AND type = :type ORDER BY measuredAtEpochMillis ASC, id")
    suspend fun history(farmId: String, animalId: String, type: String): List<MeasurementEntity>
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
        UPDATE sync_outbox
        SET state = 'IN_FLIGHT',
            nextAttemptAtEpochMillis = :leaseUntil,
            lastErrorCode = NULL
        WHERE mutationId = :mutationId
          AND (
              (state IN ('PENDING','RETRY_WAIT')
                AND (nextAttemptAtEpochMillis IS NULL OR nextAttemptAtEpochMillis <= :now))
              OR
              (state = 'IN_FLIGHT'
                AND (nextAttemptAtEpochMillis IS NULL OR nextAttemptAtEpochMillis <= :now))
          )
    """)
    suspend fun claim(
        mutationId: String,
        now: Long,
        leaseUntil: Long,
    ): Int

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

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE farmId = :farmId AND state != 'ACKNOWLEDGED'")
    suspend fun countUnacknowledgedForFarm(farmId: String): Long

    /** Read-only queue view: rows for one farm in one sync state, newest first. */
    @Query(
        """
        SELECT * FROM sync_outbox
        WHERE farmId = :farmId AND state = :state
        ORDER BY createdAtEpochMillis DESC, mutationId
        LIMIT :limit
        """,
    )
    suspend fun listForFarmInState(farmId: String, state: String, limit: Int): List<OutboxEntity>

    @Query("SELECT state, COUNT(*) AS count FROM sync_outbox WHERE farmId = :farmId GROUP BY state")
    suspend fun countByStateForFarm(farmId: String): List<OutboxStateCount>
}

data class OutboxStateCount(
    val state: String,
    val count: Long,
)

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
interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(task: TaskEntity)

    @Upsert
    suspend fun upsertFromServer(task: TaskEntity)

    @Query("UPDATE farm_tasks SET status = :status, updatedAtEpochMillis = :updatedAt WHERE farmId = :farmId AND id = :taskId")
    suspend fun updateStatus(farmId: String, taskId: String, status: String, updatedAt: Long)

    @Query("SELECT * FROM farm_tasks WHERE farmId = :farmId AND status = 'open' ORDER BY dueOnEpochDay, title")
    suspend fun openForFarm(farmId: String): List<TaskEntity>

    @Query("SELECT * FROM farm_tasks WHERE farmId = :farmId AND id = :taskId LIMIT 1")
    suspend fun get(farmId: String, taskId: String): TaskEntity?

    @Query("DELETE FROM farm_tasks WHERE farmId = :farmId AND id = :taskId AND status = 'open'")
    suspend fun deleteOpen(farmId: String, taskId: String)

    @Query("SELECT * FROM farm_tasks WHERE farmId = :farmId AND status = 'done' ORDER BY updatedAtEpochMillis DESC LIMIT :limit")
    suspend fun completedForFarm(farmId: String, limit: Int = 100): List<TaskEntity>

    @Query("SELECT COUNT(*) FROM farm_tasks WHERE farmId = :farmId AND status = 'done'")
    suspend fun countCompletedForFarm(farmId: String): Int
}

@Dao
interface KiddingDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(event: KiddingEntity)

    @Upsert
    suspend fun upsertFromServer(event: KiddingEntity)

    @Query("SELECT * FROM kidding_events WHERE farmId = :farmId AND damId = :damId ORDER BY occurredEpochDay DESC")
    suspend fun forDam(farmId: String, damId: String): List<KiddingEntity>

    @Query("SELECT * FROM kidding_events WHERE farmId = :farmId AND id = :kiddingId LIMIT 1")
    suspend fun get(farmId: String, kiddingId: String): KiddingEntity?
}

@Dao
interface RabbitProgrammeDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCage(cage: RabbitCageEntity)

    @Upsert
    suspend fun upsertCage(cage: RabbitCageEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBox(box: RabbitNestBoxEntity)

    @Upsert
    suspend fun upsertBox(box: RabbitNestBoxEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWave(wave: RabbitWaveEntity)

    @Upsert
    suspend fun upsertWave(wave: RabbitWaveEntity)

    @Query("SELECT * FROM rabbit_cages WHERE farmId = :farmId ORDER BY code")
    suspend fun cages(farmId: String): List<RabbitCageEntity>

    @Query("SELECT COUNT(*) FROM rabbit_nest_boxes WHERE farmId = :farmId AND cageId = :cageId AND status IN ('available','sanitized')")
    suspend fun availableBoxes(farmId: String, cageId: String): Long

    @Query("SELECT * FROM rabbit_breeding_waves WHERE farmId = :farmId ORDER BY matingEpochDay DESC")
    suspend fun waves(farmId: String): List<RabbitWaveEntity>

    @Query("SELECT * FROM rabbit_nest_boxes WHERE farmId = :farmId ORDER BY code")
    suspend fun boxes(farmId: String): List<RabbitNestBoxEntity>

    @Query("UPDATE rabbit_nest_boxes SET status = :status WHERE farmId = :farmId AND id = :boxId")
    suspend fun updateBoxStatus(farmId: String, boxId: String, status: String)
}

@Dao
interface HealthObservationDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(observation: HealthObservationEntity)

    @Upsert
    suspend fun upsertFromServer(observation: HealthObservationEntity)

    @Query("SELECT * FROM health_observations WHERE farmId = :farmId ORDER BY occurredAtEpochMillis DESC, id LIMIT :limit")
    suspend fun recent(farmId: String, limit: Int): List<HealthObservationEntity>

    @Query("SELECT COUNT(*) FROM health_observations WHERE farmId = :farmId")
    suspend fun count(farmId: String): Int

    @Query("SELECT * FROM health_observations WHERE farmId = :farmId AND animalId = :animalId ORDER BY occurredAtEpochMillis DESC, id")
    suspend fun forAnimal(farmId: String, animalId: String): List<HealthObservationEntity>
}

@Dao
interface MoneyDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: MoneyRecordEntity)

    @Upsert
    suspend fun upsertFromServer(record: MoneyRecordEntity)

    @Query("SELECT * FROM money_records WHERE farmId = :farmId ORDER BY occurredEpochDay DESC, id DESC LIMIT :limit")
    suspend fun recent(farmId: String, limit: Int): List<MoneyRecordEntity>
}

@Dao
interface InventoryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertItem(item: InventoryItemEntity)

    @Upsert
    suspend fun upsertItem(item: InventoryItemEntity)

    @Query("UPDATE inventory_items SET quantityMilli = :quantityMilli, updatedAtEpochMillis = :updatedAt WHERE farmId = :farmId AND id = :itemId")
    suspend fun setQuantity(farmId: String, itemId: String, quantityMilli: Long, updatedAt: Long)

    @Query("UPDATE inventory_items SET reorderMilli = :reorderMilli, updatedAtEpochMillis = :updatedAt WHERE farmId = :farmId AND id = :itemId")
    suspend fun setReorder(farmId: String, itemId: String, reorderMilli: Long, updatedAt: Long)

    @Query("SELECT * FROM inventory_items WHERE farmId = :farmId ORDER BY name")
    suspend fun items(farmId: String): List<InventoryItemEntity>

    @Query("SELECT * FROM inventory_items WHERE farmId = :farmId AND id = :itemId LIMIT 1")
    suspend fun item(farmId: String, itemId: String): InventoryItemEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMovement(movement: InventoryMovementEntity): Long

    @Query("SELECT * FROM inventory_movements WHERE farmId = :farmId ORDER BY occurredAtEpochMillis DESC, id LIMIT :limit")
    suspend fun movements(farmId: String, limit: Int): List<InventoryMovementEntity>
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
        TaskEntity::class,
        KiddingEntity::class,
        RabbitCageEntity::class,
        RabbitNestBoxEntity::class,
        RabbitWaveEntity::class,
        HealthObservationEntity::class,
        MoneyRecordEntity::class,
        InventoryItemEntity::class,
        InventoryMovementEntity::class,
        AnimalGroupEntity::class,
        PaddockEntity::class,
        GrazingSessionEntity::class,
        LabourEntryEntity::class,
        FarmAssetEntity::class,
        MaintenanceEventEntity::class,
        FeedIssueEntity::class,
        WaterRecordEntity::class,
        SaleRecordEntity::class,
        FormularyItemEntity::class,
        HealthTreatmentEntity::class,
        FamachaScoreEntity::class,
        PoultryFlockDayEntity::class,
        DiseaseCatalogEntity::class,
        SheepJoiningEntity::class,
        SheepScanEntity::class,
        SheepLambingEntity::class,
        CattleServiceEntity::class,
        CattlePdEntity::class,
        CattleCalvingEntity::class,
        RabbitPalpationEntity::class,
        RabbitKindlingEntity::class,
        RabbitFosterEntity::class,
        SupplierEntity::class,
        PurchaseEntity::class,
        WithdrawalWindowEntity::class,
        GoatMilkEntity::class,
        HealthPackEntity::class,
        RabbitWeanEntity::class,
        SheepMarkingEntity::class,
        SheepWeaningEntity::class,
        CattleBcsEntity::class,
        SheepWoolEntity::class,
        CattleMilkEntity::class,
        SheepDagEntity::class,
        SheepFootrotEntity::class,
        RabbitKitEntity::class,
        RabbitRetentionEntity::class,
        RabbitWaitlistEntity::class,
        RabbitContractEntity::class,
        RabbitMarketPlanEntity::class,
        GoatBcsEntity::class,
        CattleLocomotionEntity::class,
        CattleSccEntity::class,
        SheepShearingEntity::class,
        PoultryHouseEntity::class,
        PoultryHatchEntity::class,
        SheepFlystrikeEntity::class,
        RabbitMatingOutcomeEntity::class,
        GoatSccEntity::class,
        RabbitInventoryLinkEntity::class,
        PoultryVaccinationEntity::class,
        CattleDryOffEntity::class,
        PoultryPlacementEntity::class,
        PoultryBiosecurityEntity::class,
        GoatHeatEntity::class,
        GoatMatingEntity::class,
        GoatPregnancyEntity::class,
        AnimalIdentifierEntity::class,
        OfficialMovementEntity::class,
        InventoryLotEntity::class,
        VetVisitEntity::class,
        LabResultEntity::class,
        CattleWeaningEntity::class,
        SheepMicronEntity::class,
        EnabledPoultryKindEntity::class,
        RabbitGiStasisEntity::class,
        PedigreeRelationEntity::class,
        HealthPackSlotEntity::class,
        HealthPackApplyEntity::class,
        CattleLotPlacementEntity::class,
        CattleDofEntity::class,
        CattleLotCloseEntity::class,
        GoatLactationPlanEntity::class,
        ReorderAlertEntity::class,
        GroupCensusEntity::class,
        GoatKidEntity::class,
        ReplicationOperationEntity::class,
        ReplicationDeviceEntity::class,
        LocalFarmEntity::class,
        LocalAccountEntity::class,
        FarmRecoveryEntity::class,
        AccessAuditEntity::class,
        FarmSettingsEntity::class,
        ReplicationApplicationEntity::class,
        ReplicationPeerMarkEntity::class,
        FarmGestationEntity::class,
        TaskSeriesEntity::class,
        StockCountEntity::class,
        StockCountLineEntity::class,
        FarmWorkerEntity::class,
        AnimalExitEntity::class,
        FarmCustomerEntity::class,
    ],
    version = 27,
    exportSchema = true,
)
abstract class FarmOsDatabase : RoomDatabase() {
    abstract fun animals(): AnimalDao
    abstract fun measurements(): MeasurementDao
    abstract fun outbox(): OutboxDao
    abstract fun replication(): ReplicationDao
    abstract fun replicationBlocking(): ReplicationBlockingDao
    abstract fun localAccess(): LocalAccessDao
    abstract fun farmSettings(): FarmSettingsDao
    abstract fun farmGestation(): FarmGestationDao
    abstract fun taskSeries(): TaskSeriesDao
    abstract fun stockCounts(): StockCountDao
    abstract fun workers(): FarmWorkerDao
    abstract fun animalExits(): AnimalExitDao
    abstract fun replicationApplications(): ReplicationApplicationDao
    abstract fun replicationPeerMarks(): ReplicationPeerMarkDao
    abstract fun aggregateVersions(): AggregateVersionDao
    abstract fun syncCursors(): SyncCursorDao
    abstract fun tasks(): TaskDao
    abstract fun kidding(): KiddingDao
    abstract fun rabbitProgramme(): RabbitProgrammeDao
    abstract fun healthObservations(): HealthObservationDao
    abstract fun money(): MoneyDao
    abstract fun inventory(): InventoryDao
    abstract fun groups(): AnimalGroupDao
    abstract fun paddocks(): PaddockDao
    abstract fun grazing(): GrazingDao
    abstract fun labour(): LabourDao
    abstract fun assets(): AssetDao
    abstract fun maintenance(): MaintenanceDao
    abstract fun feedIssues(): FeedIssueDao
    abstract fun water(): WaterDao
    abstract fun sales(): SaleDao
    abstract fun formulary(): FormularyDao
    abstract fun treatments(): TreatmentDao
    abstract fun famacha(): FamachaDao
    abstract fun poultryFlockDays(): PoultryFlockDayDao
    abstract fun diseaseCatalog(): DiseaseCatalogDao
    abstract fun lifecycle(): LifecycleDao
    abstract fun reports(): FarmReportDao
    abstract fun customers(): FarmCustomerDao

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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // DEFAULT NULL is required: AnimalEntity declares
                // @ColumnInfo(defaultValue = "NULL"), so Room compares the default and a
                // migrated database without it fails validateMigration against schema v13.
                db.execSQL("ALTER TABLE animals ADD COLUMN poultryKindCode TEXT DEFAULT NULL")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS farm_tasks (
                        id TEXT NOT NULL PRIMARY KEY,
                        farmId TEXT NOT NULL,
                        moduleCode TEXT NOT NULL,
                        taskCode TEXT NOT NULL,
                        title TEXT NOT NULL,
                        dueOnEpochDay INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        animalId TEXT,
                        cageId TEXT,
                        waveId TEXT,
                        updatedAtEpochMillis INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_farm_tasks_farmId_status_dueOnEpochDay ON farm_tasks(farmId, status, dueOnEpochDay)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS kidding_events (
                        id TEXT NOT NULL PRIMARY KEY,
                        farmId TEXT NOT NULL,
                        damId TEXT NOT NULL,
                        bornCount INTEGER NOT NULL,
                        liveCount INTEGER NOT NULL,
                        deadCount INTEGER NOT NULL,
                        occurredEpochDay INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_kidding_events_farmId_damId ON kidding_events(farmId, damId)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS rabbit_cages (
                        id TEXT NOT NULL PRIMARY KEY,
                        farmId TEXT NOT NULL,
                        code TEXT NOT NULL,
                        doeCapacity INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_rabbit_cages_farmId_code ON rabbit_cages(farmId, code)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS rabbit_nest_boxes (
                        id TEXT NOT NULL PRIMARY KEY,
                        farmId TEXT NOT NULL,
                        cageId TEXT NOT NULL,
                        code TEXT NOT NULL,
                        status TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_rabbit_nest_boxes_farmId_cageId_code ON rabbit_nest_boxes(farmId, cageId, code)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS rabbit_breeding_waves (
                        id TEXT NOT NULL PRIMARY KEY,
                        farmId TEXT NOT NULL,
                        cageId TEXT NOT NULL,
                        packId TEXT NOT NULL,
                        doeCount INTEGER NOT NULL,
                        matingEpochDay INTEGER NOT NULL,
                        nestInEpochDay INTEGER NOT NULL,
                        kindlingEpochDay INTEGER NOT NULL,
                        nestOutEpochDay INTEGER NOT NULL,
                        rebreedEpochDay INTEGER NOT NULL,
                        weanEpochDay INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_rabbit_breeding_waves_farmId_cageId ON rabbit_breeding_waves(farmId, cageId)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS health_observations (
                        id TEXT NOT NULL PRIMARY KEY,
                        farmId TEXT NOT NULL,
                        animalId TEXT,
                        speciesCode TEXT NOT NULL,
                        signs TEXT NOT NULL,
                        firstAidApplied TEXT,
                        redFlag INTEGER NOT NULL,
                        occurredAtEpochMillis INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_health_observations_farmId_occurredAtEpochMillis ON health_observations(farmId, occurredAtEpochMillis)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS money_records (
                        id TEXT NOT NULL PRIMARY KEY,
                        farmId TEXT NOT NULL,
                        kind TEXT NOT NULL,
                        categoryCode TEXT NOT NULL,
                        amountMinor INTEGER NOT NULL,
                        currency TEXT NOT NULL,
                        occurredEpochDay INTEGER NOT NULL,
                        note TEXT
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_money_records_farmId_occurredEpochDay ON money_records(farmId, occurredEpochDay)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS inventory_items (
                        id TEXT NOT NULL PRIMARY KEY,
                        farmId TEXT NOT NULL,
                        sku TEXT NOT NULL,
                        name TEXT NOT NULL,
                        unit TEXT NOT NULL,
                        quantityMilli INTEGER NOT NULL,
                        updatedAtEpochMillis INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_inventory_items_farmId_sku ON inventory_items(farmId, sku)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS inventory_movements (
                        id TEXT NOT NULL PRIMARY KEY,
                        farmId TEXT NOT NULL,
                        itemId TEXT NOT NULL,
                        direction TEXT NOT NULL,
                        quantityMilli INTEGER NOT NULL,
                        occurredAtEpochMillis INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_movements_farmId_itemId ON inventory_movements(farmId, itemId)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS animal_groups (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, speciesCode TEXT NOT NULL, name TEXT NOT NULL, headCount INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_animal_groups_farmId_speciesCode_name ON animal_groups(farmId, speciesCode, name)")
                db.execSQL("CREATE TABLE IF NOT EXISTS paddocks (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, code TEXT NOT NULL, displayName TEXT NOT NULL, areaM2 INTEGER, waterSource TEXT NOT NULL, shade INTEGER NOT NULL, active INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_paddocks_farmId_code ON paddocks(farmId, code)")
                db.execSQL("CREATE TABLE IF NOT EXISTS grazing_sessions (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, paddockId TEXT NOT NULL, groupId TEXT NOT NULL, speciesCode TEXT NOT NULL, enteredEpochDay INTEGER NOT NULL, exitedEpochDay INTEGER, headCount INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_grazing_sessions_farmId_paddockId ON grazing_sessions(farmId, paddockId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS labour_entries (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, workerName TEXT NOT NULL, taskCode TEXT NOT NULL, minutes INTEGER NOT NULL, occurredEpochDay INTEGER NOT NULL, note TEXT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_labour_entries_farmId_occurredEpochDay ON labour_entries(farmId, occurredEpochDay)")
                db.execSQL("CREATE TABLE IF NOT EXISTS farm_assets (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, code TEXT NOT NULL, name TEXT NOT NULL, kind TEXT NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_farm_assets_farmId_code ON farm_assets(farmId, code)")
                db.execSQL("CREATE TABLE IF NOT EXISTS maintenance_events (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, assetId TEXT NOT NULL, title TEXT NOT NULL, occurredEpochDay INTEGER NOT NULL, note TEXT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_maintenance_events_farmId_assetId ON maintenance_events(farmId, assetId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS feed_issues (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, itemId TEXT NOT NULL, groupId TEXT, quantityMilli INTEGER NOT NULL, occurredEpochDay INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_feed_issues_farmId_occurredEpochDay ON feed_issues(farmId, occurredEpochDay)")
                db.execSQL("CREATE TABLE IF NOT EXISTS water_records (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, source TEXT NOT NULL, litresMilli INTEGER NOT NULL, occurredEpochDay INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_water_records_farmId_occurredEpochDay ON water_records(farmId, occurredEpochDay)")
                db.execSQL("CREATE TABLE IF NOT EXISTS sales_records (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, itemKind TEXT NOT NULL, quantityMilli INTEGER NOT NULL, amountMinor INTEGER NOT NULL, currency TEXT NOT NULL, occurredEpochDay INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sales_records_farmId_occurredEpochDay ON sales_records(farmId, occurredEpochDay)")
                db.execSQL("CREATE TABLE IF NOT EXISTS formulary_items (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, productName TEXT NOT NULL, speciesCode TEXT NOT NULL, vetClass TEXT NOT NULL, meatWithdrawalDays INTEGER, milkWithdrawalDays INTEGER, eggWithdrawalDays INTEGER, vetApproved INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_formulary_items_farmId_productName ON formulary_items(farmId, productName)")
                db.execSQL("CREATE TABLE IF NOT EXISTS health_treatments (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, animalId TEXT, speciesCode TEXT NOT NULL, formularyItemId TEXT NOT NULL, reason TEXT NOT NULL, meatWithdrawalDays INTEGER, milkWithdrawalDays INTEGER, eggWithdrawalDays INTEGER, occurredAtEpochMillis INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_health_treatments_farmId_occurredAtEpochMillis ON health_treatments(farmId, occurredAtEpochMillis)")
                db.execSQL("CREATE TABLE IF NOT EXISTS famacha_scores (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, animalId TEXT NOT NULL, score INTEGER NOT NULL, occurredEpochDay INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_famacha_scores_farmId_animalId ON famacha_scores(farmId, animalId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS poultry_flock_days (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, groupId TEXT NOT NULL, eggs INTEGER NOT NULL, dead INTEGER NOT NULL, culls INTEGER NOT NULL, feedGrams INTEGER NOT NULL, occurredEpochDay INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_poultry_flock_days_farmId_groupId_occurredEpochDay ON poultry_flock_days(farmId, groupId, occurredEpochDay)")
                db.execSQL("CREATE TABLE IF NOT EXISTS disease_catalog (code TEXT NOT NULL PRIMARY KEY, speciesCode TEXT NOT NULL, displayName TEXT NOT NULL, signs TEXT NOT NULL, firstAid TEXT NOT NULL, prevention TEXT NOT NULL, vetClass TEXT NOT NULL, redFlag INTEGER NOT NULL)")
                db.execSQL("INSERT OR IGNORE INTO disease_catalog VALUES ('enterotoxemia_cd','goat','Enterotoxemia','Sudden death, convulsions, bloated kids on rich feed','Isolate remaining animals, stop sudden grain, call the vet','CDT pack accepted by the attending vet','vaccine',1)")
                db.execSQL("INSERT OR IGNORE INTO disease_catalog VALUES ('haemonchus','goat','Haemonchus','Pale eyelids, bottle jaw, weakness','Shade and water. Do not blanket-drench the herd.','FAMACHA and FEC protocol, not a calendar drench','anthelmintic',1)")
                db.execSQL("INSERT OR IGNORE INTO disease_catalog VALUES ('gi_stasis','rabbit','Gut stasis','No faeces, hunched, off feed','Keep warm, offer hay and water, call the vet the same day','Hay always available, reduce stress','fluids',1)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS sheep_joinings (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, groupId TEXT NOT NULL, startedEpochDay INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS sheep_scans (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, animalId TEXT NOT NULL, result TEXT NOT NULL, occurredEpochDay INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sheep_scans_farmId_animalId ON sheep_scans(farmId, animalId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS sheep_lambings (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, damId TEXT NOT NULL, bornCount INTEGER NOT NULL, liveCount INTEGER NOT NULL, deadCount INTEGER NOT NULL, occurredEpochDay INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sheep_lambings_farmId_damId ON sheep_lambings(farmId, damId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS cattle_services (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, animalId TEXT NOT NULL, method TEXT NOT NULL, occurredEpochDay INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS cattle_pd (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, animalId TEXT NOT NULL, result TEXT NOT NULL, occurredEpochDay INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS cattle_calvings (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, damId TEXT NOT NULL, bornCount INTEGER NOT NULL, liveCount INTEGER NOT NULL, deadCount INTEGER NOT NULL, occurredEpochDay INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS rabbit_palpations (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, waveId TEXT NOT NULL, result TEXT NOT NULL, occurredEpochDay INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS rabbit_kindlings (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, waveId TEXT NOT NULL, liveCount INTEGER NOT NULL, deadCount INTEGER NOT NULL, occurredEpochDay INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS rabbit_fosters (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, fromWaveId TEXT NOT NULL, toWaveId TEXT NOT NULL, kitCount INTEGER NOT NULL, withinWindow INTEGER NOT NULL, occurredEpochDay INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS suppliers (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, name TEXT NOT NULL, leadTimeDays INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_suppliers_farmId_name ON suppliers(farmId, name)")
                db.execSQL("CREATE TABLE IF NOT EXISTS purchases (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, supplierId TEXT NOT NULL, itemId TEXT NOT NULL, quantityMilli INTEGER NOT NULL, amountMinor INTEGER NOT NULL, currency TEXT NOT NULL, occurredEpochDay INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS withdrawal_windows (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, treatmentId TEXT NOT NULL, product TEXT NOT NULL, windowKind TEXT NOT NULL, endsEpochDay INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS goat_milk_records (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, animalId TEXT NOT NULL, litresMilli INTEGER NOT NULL, occurredEpochDay INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS health_protocol_packs (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, speciesCode TEXT NOT NULL, name TEXT NOT NULL, status TEXT NOT NULL, acceptedByVet TEXT)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS rabbit_weans (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, waveId TEXT NOT NULL, weanedCount INTEGER NOT NULL, occurredEpochDay INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS sheep_markings (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, groupId TEXT, animalId TEXT, markedCount INTEGER NOT NULL, occurredEpochDay INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS sheep_weanings (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, groupId TEXT, animalId TEXT, weanedCount INTEGER NOT NULL, occurredEpochDay INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS cattle_bcs_scores (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, animalId TEXT NOT NULL, scale TEXT NOT NULL, scoreTenths INTEGER NOT NULL, occurredEpochDay INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS sheep_wool_clips (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, animalId TEXT, groupId TEXT, greasyGrams INTEGER NOT NULL, occurredEpochDay INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS cattle_milk_records (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, animalId TEXT NOT NULL, litresMilli INTEGER NOT NULL, occurredEpochDay INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS sheep_dag_scores (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, animalId TEXT NOT NULL, score INTEGER NOT NULL, occurredEpochDay INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS sheep_footrot_scores (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, animalId TEXT NOT NULL, score INTEGER NOT NULL, occurredEpochDay INTEGER NOT NULL)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS rabbit_kits (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, waveId TEXT NOT NULL, animalId TEXT, tempLabel TEXT NOT NULL, sex TEXT NOT NULL, status TEXT NOT NULL, retention TEXT NOT NULL, earTag TEXT)")
                db.execSQL("CREATE TABLE IF NOT EXISTS rabbit_retention_decisions (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, kitId TEXT NOT NULL, decision TEXT NOT NULL, occurredEpochDay INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS rabbit_sales_waitlist (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, contactName TEXT NOT NULL, desiredSex TEXT, qty INTEGER NOT NULL, status TEXT NOT NULL, matchedKitId TEXT)")
                db.execSQL("CREATE TABLE IF NOT EXISTS rabbit_sales_contracts (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, waitlistId TEXT, buyerName TEXT NOT NULL, animalId TEXT, amountMinor INTEGER NOT NULL, currency TEXT NOT NULL, status TEXT NOT NULL, occurredEpochDay INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS rabbit_market_plans (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, kitId TEXT, waveId TEXT, targetWeightGrams INTEGER NOT NULL, targetEpochDay INTEGER NOT NULL, purpose TEXT NOT NULL, status TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS goat_bcs_scores (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, animalId TEXT NOT NULL, scoreTenths INTEGER NOT NULL, occurredEpochDay INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS cattle_locomotion_scores (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, animalId TEXT NOT NULL, score INTEGER NOT NULL, occurredEpochDay INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS cattle_scc_records (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, animalId TEXT NOT NULL, cellsPerMl INTEGER NOT NULL, dimDays INTEGER, occurredEpochDay INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS sheep_shearing_events (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, animalId TEXT, groupId TEXT, kind TEXT NOT NULL, greasyGrams INTEGER, occurredEpochDay INTEGER NOT NULL)")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS poultry_houses (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, code TEXT NOT NULL, kind TEXT NOT NULL, poultryKindCode TEXT NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_poultry_houses_farmId_code ON poultry_houses(farmId, code)")
                db.execSQL("CREATE TABLE IF NOT EXISTS poultry_hatches (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, poultryKindCode TEXT NOT NULL, houseId TEXT, groupId TEXT, eggsSet INTEGER NOT NULL, incubationDays INTEGER NOT NULL, setEpochDay INTEGER NOT NULL, status TEXT NOT NULL, fertile INTEGER, infertile INTEGER, midDead INTEGER, hatched INTEGER, culls INTEGER, placementGroupId TEXT)")
                db.execSQL("CREATE TABLE IF NOT EXISTS sheep_flystrike_scores (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, animalId TEXT NOT NULL, score INTEGER NOT NULL, region TEXT, occurredEpochDay INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS rabbit_mating_outcomes (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, waveId TEXT NOT NULL, outcome TEXT NOT NULL, occurredEpochDay INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS goat_scc_records (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, animalId TEXT NOT NULL, cellsPerMl INTEGER NOT NULL, dimDays INTEGER, occurredEpochDay INTEGER NOT NULL)")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS rabbit_programme_inventory_links (farmId TEXT NOT NULL PRIMARY KEY, nestBeddingItemId TEXT, nestBeddingQtyMilli INTEGER NOT NULL, doeFeedItemId TEXT, lowStockNotify INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS poultry_vaccinations (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, groupId TEXT NOT NULL, poultryKindCode TEXT NOT NULL, formularyItemId TEXT NOT NULL, occurredEpochDay INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS cattle_dry_offs (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, animalId TEXT NOT NULL, occurredEpochDay INTEGER NOT NULL, expectedCalvingEpochDay INTEGER)")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS poultry_placements (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, groupId TEXT NOT NULL, houseId TEXT NOT NULL, poultryKindCode TEXT NOT NULL, headCount INTEGER NOT NULL, occurredEpochDay INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS poultry_biosecurity_walks (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, houseId TEXT, groupId TEXT, findings TEXT NOT NULL, mixedSpecies INTEGER NOT NULL, occurredEpochDay INTEGER NOT NULL)")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS goat_heats (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, animalId TEXT NOT NULL, occurredEpochDay INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS goat_matings (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, damId TEXT NOT NULL, sireId TEXT, method TEXT NOT NULL, occurredEpochDay INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS goat_pregnancy_checks (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, animalId TEXT NOT NULL, result TEXT NOT NULL, occurredEpochDay INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS animal_identifiers (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, animalId TEXT NOT NULL, type TEXT NOT NULL, value TEXT NOT NULL, isActive INTEGER NOT NULL, assignedEpochDay INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS official_movements (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, animalId TEXT NOT NULL, speciesCode TEXT NOT NULL, direction TEXT NOT NULL, fromPlace TEXT, toPlace TEXT, occurredEpochDay INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS inventory_lots (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, itemId TEXT NOT NULL, lotCode TEXT NOT NULL, expiresEpochDay INTEGER NOT NULL, quantityMilli INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS vet_visits (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, speciesCode TEXT NOT NULL, animalId TEXT, groupId TEXT, reason TEXT NOT NULL, attendingVet TEXT NOT NULL, occurredEpochDay INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS lab_results (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, animalId TEXT, groupId TEXT, testName TEXT NOT NULL, resultText TEXT NOT NULL, cellsPerMl INTEGER, occurredEpochDay INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS cattle_weanings (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, animalId TEXT, groupId TEXT, weightGrams INTEGER, occurredEpochDay INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS sheep_micron_tests (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, animalId TEXT, groupId TEXT, micronTenths INTEGER NOT NULL, occurredEpochDay INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS farm_enabled_poultry_kinds (farmId TEXT NOT NULL, poultryKindCode TEXT NOT NULL, PRIMARY KEY(farmId, poultryKindCode))")
                db.execSQL("CREATE TABLE IF NOT EXISTS rabbit_gi_stasis_flags (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, animalId TEXT NOT NULL, signs TEXT NOT NULL, occurredEpochDay INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS pedigree_relations (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, animalId TEXT NOT NULL, parentId TEXT NOT NULL, relationType TEXT NOT NULL)")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE inventory_items ADD COLUMN reorderMilli INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE TABLE IF NOT EXISTS health_schedule_slots (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, packId TEXT NOT NULL, slotCode TEXT NOT NULL, title TEXT NOT NULL, offsetDays INTEGER NOT NULL, fromEvent TEXT NOT NULL, isCore INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS health_pack_applications (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, packId TEXT NOT NULL, animalId TEXT, groupId TEXT, anchorEpochDay INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS cattle_lot_placements (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, groupId TEXT NOT NULL, headCount INTEGER NOT NULL, placedEpochDay INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS cattle_days_on_feed (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, groupId TEXT NOT NULL, daysOnFeed INTEGER NOT NULL, occurredEpochDay INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS cattle_lot_closeouts (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, groupId TEXT NOT NULL, headOut INTEGER NOT NULL, weightGrams INTEGER, daysOnFeed INTEGER, occurredEpochDay INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS goat_lactation_plans (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, animalId TEXT NOT NULL, kiddingId TEXT, occurredEpochDay INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS inventory_reorder_alerts (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, itemId TEXT NOT NULL, onHandMilli INTEGER NOT NULL, reorderMilli INTEGER NOT NULL, occurredEpochDay INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS group_census_records (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, groupId TEXT NOT NULL, headCount INTEGER NOT NULL, occurredEpochDay INTEGER NOT NULL)")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS goat_kid_records (id TEXT NOT NULL PRIMARY KEY, farmId TEXT NOT NULL, animalId TEXT NOT NULL, kiddingId TEXT NOT NULL, damId TEXT NOT NULL)")
            }
        }

        /** Adds the local-first replication journal and the farm device registry. */
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `replication_operations` (`operationId` TEXT NOT NULL, `farmId` TEXT NOT NULL, `entityType` TEXT NOT NULL, `entityId` TEXT NOT NULL, `actorId` TEXT NOT NULL, `deviceId` TEXT NOT NULL, `deviceSequence` INTEGER NOT NULL, `businessTimeEpochMillis` INTEGER NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, `baseVersion` INTEGER, `operationType` TEXT NOT NULL, `mergeClass` TEXT NOT NULL, `payloadJson` TEXT NOT NULL, `protocolVersion` INTEGER NOT NULL, `schemaVersion` INTEGER NOT NULL, `provenance` TEXT NOT NULL, `checksum` TEXT NOT NULL, PRIMARY KEY(`operationId`))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_replication_operations_farmId_deviceId_deviceSequence` ON `replication_operations` (`farmId`, `deviceId`, `deviceSequence`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_replication_operations_farmId_entityType_entityId` ON `replication_operations` (`farmId`, `entityType`, `entityId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `replication_devices` (`farmId` TEXT NOT NULL, `deviceId` TEXT NOT NULL, `name` TEXT NOT NULL, `status` TEXT NOT NULL, `lastReportedOwnSequence` INTEGER NOT NULL, `revokedAfterSequence` INTEGER, `isLocal` INTEGER NOT NULL, PRIMARY KEY(`farmId`, `deviceId`))")
            }
        }

        /** Adds local farms, local accounts, the owner recovery hash and access history. */
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `local_farms` (`farmId` TEXT NOT NULL, `name` TEXT NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`farmId`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `local_accounts` (`accountId` TEXT NOT NULL, `farmId` TEXT NOT NULL, `username` TEXT NOT NULL, `displayName` TEXT NOT NULL, `role` TEXT NOT NULL, `status` TEXT NOT NULL, `credentialKind` TEXT NOT NULL, `credentialHash` TEXT NOT NULL, `failedAttempts` INTEGER NOT NULL, `lockedUntilEpochMillis` INTEGER, `workerId` TEXT, `createdAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`accountId`))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_local_accounts_farmId_username` ON `local_accounts` (`farmId`, `username`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `farm_recovery` (`farmId` TEXT NOT NULL, `recoveryHash` TEXT NOT NULL, PRIMARY KEY(`farmId`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `access_audit` (`eventId` TEXT NOT NULL, `farmId` TEXT NOT NULL, `actorAccountId` TEXT, `subjectAccountId` TEXT, `action` TEXT NOT NULL, `detail` TEXT NOT NULL, `atEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`eventId`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_access_audit_farmId_atEpochMillis` ON `access_audit` (`farmId`, `atEpochMillis`)")
            }
        }

        /** Adds farm-scoped settings; farms without a row keep the documented defaults (currency USD). */
        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `farm_settings` (`farmId` TEXT NOT NULL, `currencyCode` TEXT NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, `updatedByActorId` TEXT NOT NULL, PRIMARY KEY(`farmId`))")
            }
        }

        /** Tracks whether each received operation has taken effect in the domain tables. */
        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `replication_applications` (`operationId` TEXT NOT NULL, `farmId` TEXT NOT NULL, `state` TEXT NOT NULL, `reason` TEXT, `attempts` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`operationId`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_replication_applications_farmId_state` ON `replication_applications` (`farmId`, `state`)")
            }
        }

        /** Local accounts and the recovery hash record when they last changed, so replicated changes merge by time. */
        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `local_accounts` ADD COLUMN `updatedAtEpochMillis` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `farm_recovery` ADD COLUMN `updatedAtEpochMillis` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Farm devices record their public key, so a rotated farm key can be wrapped to each remaining device. */
        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `replication_devices` ADD COLUMN `publicKey` TEXT")
            }
        }

        /** Records how far each farm peer has confirmed holding this device's own operations. */
        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `replication_peer_marks` (`farmId` TEXT NOT NULL, `peerDeviceId` TEXT NOT NULL, `holdsOwnThrough` INTEGER NOT NULL, `atEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`farmId`, `peerDeviceId`))")
            }
        }

        /** A farm's own gestation periods per species (owner decision D-019). */
        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `farm_gestation` (`farmId` TEXT NOT NULL, `species` TEXT NOT NULL, `earliestDays` INTEGER NOT NULL, `typicalDays` INTEGER NOT NULL, `latestDays` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, `updatedByActorId` TEXT NOT NULL, PRIMARY KEY(`farmId`, `species`))")
            }
        }

        /** Repeating tasks and task assignment (owner decision D-020). Existing tasks stay one-off and unassigned. */
        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `farm_tasks` ADD COLUMN `seriesId` TEXT")
                db.execSQL("ALTER TABLE `farm_tasks` ADD COLUMN `occurrenceEpochDay` INTEGER")
                db.execSQL("ALTER TABLE `farm_tasks` ADD COLUMN `assigneeAccountId` TEXT")
                db.execSQL("ALTER TABLE `farm_tasks` ADD COLUMN `assigneeWorkerId` TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `task_series` (`id` TEXT NOT NULL, `farmId` TEXT NOT NULL, `moduleCode` TEXT NOT NULL, " +
                        "`taskCode` TEXT NOT NULL, `title` TEXT NOT NULL, `recurrenceKind` TEXT NOT NULL, `recurrenceInterval` INTEGER NOT NULL, " +
                        "`startEpochDay` INTEGER NOT NULL, `endEpochDay` INTEGER, `animalId` TEXT, `assigneeAccountId` TEXT, `assigneeWorkerId` TEXT, " +
                        "`status` TEXT NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, `updatedByActorId` TEXT NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_series_farmId_status` ON `task_series` (`farmId`, `status`)")
            }
        }

        /** Stock counts and their lines (owner decision D-021). */
        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `stock_counts` (`id` TEXT NOT NULL, `farmId` TEXT NOT NULL, `status` TEXT NOT NULL, " +
                        "`startedByActorId` TEXT NOT NULL, `startedAtEpochMillis` INTEGER NOT NULL, `submittedByActorId` TEXT, `submittedAtEpochMillis` INTEGER, " +
                        "`decidedByActorId` TEXT, `decidedAtEpochMillis` INTEGER, `rejectionReason` TEXT, PRIMARY KEY(`id`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_counts_farmId_status` ON `stock_counts` (`farmId`, `status`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `stock_count_lines` (`id` TEXT NOT NULL, `farmId` TEXT NOT NULL, `countId` TEXT NOT NULL, `itemId` TEXT NOT NULL, " +
                        "`onHandAtCountMilli` INTEGER NOT NULL, `countedMilli` INTEGER NOT NULL, `countedByActorId` TEXT NOT NULL, `countedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_count_lines_farmId_countId` ON `stock_count_lines` (`farmId`, `countId`)")
            }
        }

        /** The farm's worker register (D-016, resolution R1). */
        val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `farm_workers` (`id` TEXT NOT NULL, `farmId` TEXT NOT NULL, `name` TEXT NOT NULL, `active` INTEGER NOT NULL, " +
                        "`updatedAtEpochMillis` INTEGER NOT NULL, `updatedByActorId` TEXT NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_farm_workers_farmId_active` ON `farm_workers` (`farmId`, `active`)")
            }
        }

        /** Append-only animal exits and their reversals (D-022, resolution R6). */
        val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `animal_exits` (`id` TEXT NOT NULL, `farmId` TEXT NOT NULL, `animalId` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                        "`occurredEpochDay` INTEGER NOT NULL, `deathCause` TEXT, `reason` TEXT, `buyer` TEXT, `priceMinor` INTEGER, `currency` TEXT, " +
                        "`reversesExitId` TEXT, `recordedByActorId` TEXT NOT NULL, `recordedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_animal_exits_farmId_animalId` ON `animal_exits` (`farmId`, `animalId`)")
            }
        }

        /** Version 26 links labour entries to the worker register (R1); earlier entries keep their typed label. */
        val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `labour_entries` ADD COLUMN `workerId` TEXT DEFAULT NULL")
            }
        }

        /** Version 27 adds the customer register and links sales to a customer; earlier sales name none. */
        val MIGRATION_26_27 = object : Migration(26, 27) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `farm_customers` (`id` TEXT NOT NULL, `farmId` TEXT NOT NULL, `name` TEXT NOT NULL, `phone` TEXT, " +
                        "`active` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, `updatedByActorId` TEXT NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_farm_customers_farmId_active` ON `farm_customers` (`farmId`, `active`)")
                db.execSQL("ALTER TABLE `sales_records` ADD COLUMN `customerId` TEXT DEFAULT NULL")
            }
        }

        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27)
    }
}
