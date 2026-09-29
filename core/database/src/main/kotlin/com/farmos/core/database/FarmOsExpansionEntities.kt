package com.farmos.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

@Entity(tableName = "animal_groups", indices = [Index(value = ["farmId", "speciesCode", "name"], unique = true)])
data class AnimalGroupEntity(
    @PrimaryKey val id: String,
    val farmId: String,
    val speciesCode: String,
    val name: String,
    val headCount: Int,
)

@Entity(tableName = "paddocks", indices = [Index(value = ["farmId", "code"], unique = true)])
data class PaddockEntity(
    @PrimaryKey val id: String,
    val farmId: String,
    val code: String,
    val displayName: String,
    val areaM2: Int?,
    val waterSource: String,
    val shade: Boolean,
    val active: Boolean,
)

@Entity(tableName = "grazing_sessions", indices = [Index(value = ["farmId", "paddockId"])])
data class GrazingSessionEntity(
    @PrimaryKey val id: String,
    val farmId: String,
    val paddockId: String,
    val groupId: String,
    val speciesCode: String,
    val enteredEpochDay: Long,
    val exitedEpochDay: Long?,
    val headCount: Int,
)

@Entity(tableName = "labour_entries", indices = [Index(value = ["farmId", "occurredEpochDay"])])
data class LabourEntryEntity(
    @PrimaryKey val id: String,
    val farmId: String,
    val workerName: String,
    val taskCode: String,
    val minutes: Int,
    val occurredEpochDay: Long,
    val note: String?,
)

@Entity(tableName = "farm_assets", indices = [Index(value = ["farmId", "code"], unique = true)])
data class FarmAssetEntity(
    @PrimaryKey val id: String,
    val farmId: String,
    val code: String,
    val name: String,
    val kind: String,
)

@Entity(tableName = "maintenance_events", indices = [Index(value = ["farmId", "assetId"])])
data class MaintenanceEventEntity(
    @PrimaryKey val id: String,
    val farmId: String,
    val assetId: String,
    val title: String,
    val occurredEpochDay: Long,
    val note: String?,
)

@Entity(tableName = "feed_issues", indices = [Index(value = ["farmId", "occurredEpochDay"])])
data class FeedIssueEntity(
    @PrimaryKey val id: String,
    val farmId: String,
    val itemId: String,
    val groupId: String?,
    val quantityMilli: Long,
    val occurredEpochDay: Long,
)

@Entity(tableName = "water_records", indices = [Index(value = ["farmId", "occurredEpochDay"])])
data class WaterRecordEntity(
    @PrimaryKey val id: String,
    val farmId: String,
    val source: String,
    val litresMilli: Long,
    val occurredEpochDay: Long,
)

@Entity(tableName = "sales_records", indices = [Index(value = ["farmId", "occurredEpochDay"])])
data class SaleRecordEntity(
    @PrimaryKey val id: String,
    val farmId: String,
    val itemKind: String,
    val quantityMilli: Long,
    val amountMinor: Long,
    val currency: String,
    val occurredEpochDay: Long,
)

@Entity(tableName = "formulary_items", indices = [Index(value = ["farmId", "productName"])])
data class FormularyItemEntity(
    @PrimaryKey val id: String,
    val farmId: String,
    val productName: String,
    val speciesCode: String,
    val vetClass: String,
    val meatWithdrawalDays: Int?,
    val milkWithdrawalDays: Int?,
    val eggWithdrawalDays: Int?,
    val vetApproved: Boolean,
)

@Entity(tableName = "health_treatments", indices = [Index(value = ["farmId", "occurredAtEpochMillis"])])
data class HealthTreatmentEntity(
    @PrimaryKey val id: String,
    val farmId: String,
    val animalId: String?,
    val speciesCode: String,
    val formularyItemId: String,
    val reason: String,
    val meatWithdrawalDays: Int?,
    val milkWithdrawalDays: Int?,
    val eggWithdrawalDays: Int?,
    val occurredAtEpochMillis: Long,
)

@Entity(tableName = "famacha_scores", indices = [Index(value = ["farmId", "animalId"])])
data class FamachaScoreEntity(
    @PrimaryKey val id: String,
    val farmId: String,
    val animalId: String,
    val score: Int,
    val occurredEpochDay: Long,
)

@Entity(tableName = "poultry_flock_days", indices = [Index(value = ["farmId", "groupId", "occurredEpochDay"], unique = true)])
data class PoultryFlockDayEntity(
    @PrimaryKey val id: String,
    val farmId: String,
    val groupId: String,
    val eggs: Int,
    val dead: Int,
    val culls: Int,
    val feedGrams: Long,
    val occurredEpochDay: Long,
)

@Entity(tableName = "disease_catalog")
data class DiseaseCatalogEntity(
    @PrimaryKey val code: String,
    val speciesCode: String,
    val displayName: String,
    val signs: String,
    val firstAid: String,
    val prevention: String,
    val vetClass: String,
    val redFlag: Boolean,
)

@Dao
interface AnimalGroupDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(group: AnimalGroupEntity)

    @Upsert
    suspend fun upsertFromServer(group: AnimalGroupEntity)

    @Query("SELECT * FROM animal_groups WHERE farmId = :farmId ORDER BY name")
    suspend fun forFarm(farmId: String): List<AnimalGroupEntity>

    @Query("SELECT * FROM animal_groups WHERE farmId = :farmId AND id = :groupId LIMIT 1")
    suspend fun get(farmId: String, groupId: String): AnimalGroupEntity?

    @Query("UPDATE animal_groups SET headCount = :headCount WHERE farmId = :farmId AND id = :groupId")
    suspend fun setHeadCount(farmId: String, groupId: String, headCount: Int)
}

@Dao
interface PaddockDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(paddock: PaddockEntity)

    @Upsert
    suspend fun upsertFromServer(paddock: PaddockEntity)

    @Query("SELECT * FROM paddocks WHERE farmId = :farmId AND active = 1 ORDER BY code")
    suspend fun active(farmId: String): List<PaddockEntity>

    @Query("SELECT * FROM paddocks WHERE farmId = :farmId ORDER BY code")
    suspend fun forFarm(farmId: String): List<PaddockEntity>
}

@Dao
interface GrazingDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(session: GrazingSessionEntity)

    @Upsert
    suspend fun upsertFromServer(session: GrazingSessionEntity)

    @Query("UPDATE grazing_sessions SET exitedEpochDay = :exitedEpochDay WHERE farmId = :farmId AND id = :sessionId")
    suspend fun end(farmId: String, sessionId: String, exitedEpochDay: Long)

    @Query("SELECT * FROM grazing_sessions WHERE farmId = :farmId AND exitedEpochDay IS NULL")
    suspend fun open(farmId: String): List<GrazingSessionEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM grazing_sessions WHERE farmId = :farmId AND paddockId = :paddockId AND exitedEpochDay IS NULL)")
    suspend fun hasOpen(farmId: String, paddockId: String): Boolean

    @Query("SELECT * FROM grazing_sessions WHERE farmId = :farmId ORDER BY enteredEpochDay DESC, id LIMIT :limit")
    suspend fun recent(farmId: String, limit: Int): List<GrazingSessionEntity>

    @Query("SELECT * FROM grazing_sessions WHERE farmId = :farmId AND groupId = :groupId ORDER BY enteredEpochDay DESC, id")
    suspend fun forGroup(farmId: String, groupId: String): List<GrazingSessionEntity>

    @Query("SELECT COUNT(*) FROM grazing_sessions WHERE farmId = :farmId")
    suspend fun count(farmId: String): Int

    @Query(
        """
        SELECT paddockId, COUNT(*) AS sessionCount,
            SUM(CASE WHEN exitedEpochDay IS NULL THEN 1 ELSE 0 END) AS openSessions,
            SUM(CASE WHEN exitedEpochDay IS NULL THEN headCount ELSE 0 END) AS openHeadCount,
            MIN(enteredEpochDay) AS firstEnteredEpochDay, MAX(enteredEpochDay) AS latestEnteredEpochDay,
            MAX(exitedEpochDay) AS latestExitedEpochDay
        FROM grazing_sessions WHERE farmId = :farmId GROUP BY paddockId ORDER BY paddockId
        """,
    )
    suspend fun summaryByPaddock(farmId: String): List<PaddockGrazingSummary>
}

/**
 * Exhaustive per-paddock grazing aggregate over every recorded session. [openHeadCount] is the head
 * count recorded when the still-open sessions started, not a physical count.
 */
data class PaddockGrazingSummary(
    val paddockId: String,
    val sessionCount: Int,
    val openSessions: Int,
    val openHeadCount: Int,
    val firstEnteredEpochDay: Long,
    val latestEnteredEpochDay: Long,
    val latestExitedEpochDay: Long?,
)

@Dao
interface LabourDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: LabourEntryEntity)

    @Upsert
    suspend fun upsertFromServer(entry: LabourEntryEntity)

    @Query("SELECT * FROM labour_entries WHERE farmId = :farmId ORDER BY occurredEpochDay DESC, id DESC LIMIT :limit")
    suspend fun recent(farmId: String, limit: Int): List<LabourEntryEntity>

    @Query("SELECT COUNT(*) FROM labour_entries WHERE farmId = :farmId")
    suspend fun count(farmId: String): Int

    @Query(
        """
        SELECT workerName, SUM(minutes) AS minutes, COUNT(*) AS entryCount, MAX(occurredEpochDay) AS latestEpochDay
        FROM labour_entries WHERE farmId = :farmId GROUP BY workerName ORDER BY workerName
        """,
    )
    suspend fun totalsByWorkerName(farmId: String): List<LabourWorkerTotal>
}

/** Exhaustive minutes per recorded worker label; the label is a farm label, not a login or roster entry. */
data class LabourWorkerTotal(val workerName: String, val minutes: Long, val entryCount: Int, val latestEpochDay: Long)

@Dao
interface AssetDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(asset: FarmAssetEntity)

    @Upsert
    suspend fun upsertFromServer(asset: FarmAssetEntity)

    @Query("SELECT * FROM farm_assets WHERE farmId = :farmId ORDER BY code")
    suspend fun forFarm(farmId: String): List<FarmAssetEntity>
}

@Dao
interface MaintenanceDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(event: MaintenanceEventEntity)

    @Upsert
    suspend fun upsertFromServer(event: MaintenanceEventEntity)

    @Query("SELECT * FROM maintenance_events WHERE farmId = :farmId ORDER BY occurredEpochDay DESC, id LIMIT :limit")
    suspend fun recent(farmId: String, limit: Int): List<MaintenanceEventEntity>

    @Query("SELECT COUNT(*) FROM maintenance_events WHERE farmId = :farmId")
    suspend fun count(farmId: String): Int

    @Query(
        """
        SELECT assetId, COUNT(*) AS serviceCount, MAX(occurredEpochDay) AS latestEpochDay
        FROM maintenance_events WHERE farmId = :farmId GROUP BY assetId ORDER BY assetId
        """,
    )
    suspend fun summaryByAsset(farmId: String): List<AssetServiceSummary>
}

/** Exhaustive per-asset maintenance aggregate; not bounded by any presentation row limit. */
data class AssetServiceSummary(val assetId: String, val serviceCount: Int, val latestEpochDay: Long)

@Dao
interface FeedIssueDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(issue: FeedIssueEntity)

    @Upsert
    suspend fun upsertFromServer(issue: FeedIssueEntity)

    @Query("SELECT * FROM feed_issues WHERE farmId = :farmId ORDER BY occurredEpochDay DESC LIMIT :limit")
    suspend fun recent(farmId: String, limit: Int): List<FeedIssueEntity>

    @Query("SELECT * FROM feed_issues WHERE farmId = :farmId AND groupId = :groupId ORDER BY occurredEpochDay DESC, id")
    suspend fun forGroup(farmId: String, groupId: String): List<FeedIssueEntity>
}

@Dao
interface WaterDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: WaterRecordEntity)

    @Upsert
    suspend fun upsertFromServer(record: WaterRecordEntity)

    @Query("SELECT * FROM water_records WHERE farmId = :farmId ORDER BY occurredEpochDay DESC, id LIMIT :limit")
    suspend fun recent(farmId: String, limit: Int): List<WaterRecordEntity>

    @Query("SELECT COUNT(*) FROM water_records WHERE farmId = :farmId")
    suspend fun count(farmId: String): Int

    @Query(
        """
        SELECT source, SUM(litresMilli) AS litresMilli, COUNT(*) AS recordCount,
            MIN(occurredEpochDay) AS firstEpochDay, MAX(occurredEpochDay) AS latestEpochDay
        FROM water_records WHERE farmId = :farmId GROUP BY source ORDER BY source
        """,
    )
    suspend fun totalsBySource(farmId: String): List<WaterSourceTotal>
}

/** Exhaustive per-source water aggregate over every recorded row; not bounded by any list limit. */
data class WaterSourceTotal(val source: String, val litresMilli: Long, val recordCount: Int, val firstEpochDay: Long, val latestEpochDay: Long)

@Dao
interface SaleDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: SaleRecordEntity)

    @Upsert
    suspend fun upsertFromServer(record: SaleRecordEntity)

    @Query("SELECT * FROM sales_records WHERE farmId = :farmId ORDER BY occurredEpochDay DESC, id LIMIT :limit")
    suspend fun recent(farmId: String, limit: Int): List<SaleRecordEntity>

    @Query("SELECT COUNT(*) FROM sales_records WHERE farmId = :farmId")
    suspend fun count(farmId: String): Int

    @Query(
        """
        SELECT currency, SUM(amountMinor) AS amountMinor, COUNT(*) AS saleCount
        FROM sales_records WHERE farmId = :farmId GROUP BY currency ORDER BY currency
        """,
    )
    suspend fun totalsByCurrency(farmId: String): List<SaleCurrencyTotal>
}

/** Exhaustive per-currency sales aggregate; currencies are never summed together. */
data class SaleCurrencyTotal(val currency: String, val amountMinor: Long, val saleCount: Int)

@Dao
interface FormularyDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: FormularyItemEntity)

    @Upsert
    suspend fun upsertFromServer(item: FormularyItemEntity)

    @Query("SELECT * FROM formulary_items WHERE farmId = :farmId AND vetApproved = 1 ORDER BY productName")
    suspend fun approved(farmId: String): List<FormularyItemEntity>

    @Query("SELECT * FROM formulary_items WHERE farmId = :farmId ORDER BY productName, id")
    suspend fun forFarm(farmId: String): List<FormularyItemEntity>

    @Query("SELECT * FROM formulary_items WHERE farmId = :farmId AND id = :itemId LIMIT 1")
    suspend fun get(farmId: String, itemId: String): FormularyItemEntity?

    @Query("SELECT * FROM formulary_items WHERE farmId = :farmId AND id IN (:itemIds) ORDER BY id")
    suspend fun getMany(farmId: String, itemIds: List<String>): List<FormularyItemEntity>
}

@Dao
interface TreatmentDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(treatment: HealthTreatmentEntity)

    @Upsert
    suspend fun upsertFromServer(treatment: HealthTreatmentEntity)

    @Query("SELECT * FROM health_treatments WHERE farmId = :farmId ORDER BY occurredAtEpochMillis DESC LIMIT :limit")
    suspend fun recent(farmId: String, limit: Int): List<HealthTreatmentEntity>

    @Query("SELECT COUNT(*) FROM health_treatments WHERE farmId = :farmId")
    suspend fun count(farmId: String): Int

    @Query("SELECT formularyItemId AS `key`, COUNT(*) AS count FROM health_treatments WHERE farmId = :farmId GROUP BY formularyItemId ORDER BY formularyItemId")
    suspend fun countsByFormularyItem(farmId: String): List<RecordKeyCount>

    @Query("SELECT * FROM health_treatments WHERE farmId = :farmId AND animalId = :animalId ORDER BY occurredAtEpochMillis DESC, id")
    suspend fun forAnimal(farmId: String, animalId: String): List<HealthTreatmentEntity>
}

@Dao
interface FamachaDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(score: FamachaScoreEntity)

    @Upsert
    suspend fun upsertFromServer(score: FamachaScoreEntity)

    @Query("SELECT * FROM famacha_scores WHERE farmId = :farmId AND animalId = :animalId ORDER BY occurredEpochDay DESC")
    suspend fun forAnimal(farmId: String, animalId: String): List<FamachaScoreEntity>
}

@Dao
interface PoultryFlockDayDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(day: PoultryFlockDayEntity)

    @Upsert
    suspend fun upsertFromServer(day: PoultryFlockDayEntity)

    @Query("SELECT * FROM poultry_flock_days WHERE farmId = :farmId ORDER BY occurredEpochDay DESC LIMIT :limit")
    suspend fun recent(farmId: String, limit: Int): List<PoultryFlockDayEntity>

    @Query("SELECT * FROM poultry_flock_days WHERE farmId = :farmId AND groupId = :groupId ORDER BY occurredEpochDay DESC, id")
    suspend fun forGroup(farmId: String, groupId: String): List<PoultryFlockDayEntity>
}

@Dao
interface DiseaseCatalogDao {
    @Upsert
    suspend fun upsert(item: DiseaseCatalogEntity)

    @Query("SELECT * FROM disease_catalog ORDER BY speciesCode, displayName")
    suspend fun all(): List<DiseaseCatalogEntity>
}
