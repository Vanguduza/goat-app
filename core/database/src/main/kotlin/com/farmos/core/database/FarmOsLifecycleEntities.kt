package com.farmos.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

@Entity(tableName = "sheep_joinings")
data class SheepJoiningEntity(@PrimaryKey val id: String, val farmId: String, val groupId: String, val startedEpochDay: Long)

@Entity(tableName = "sheep_scans", indices = [Index(value = ["farmId", "animalId"])])
data class SheepScanEntity(@PrimaryKey val id: String, val farmId: String, val animalId: String, val result: String, val occurredEpochDay: Long)

@Entity(tableName = "sheep_lambings", indices = [Index(value = ["farmId", "damId"])])
data class SheepLambingEntity(@PrimaryKey val id: String, val farmId: String, val damId: String, val bornCount: Int, val liveCount: Int, val deadCount: Int, val occurredEpochDay: Long)

@Entity(tableName = "cattle_services")
data class CattleServiceEntity(@PrimaryKey val id: String, val farmId: String, val animalId: String, val method: String, val occurredEpochDay: Long)

@Entity(tableName = "cattle_pd")
data class CattlePdEntity(@PrimaryKey val id: String, val farmId: String, val animalId: String, val result: String, val occurredEpochDay: Long)

@Entity(tableName = "cattle_calvings")
data class CattleCalvingEntity(@PrimaryKey val id: String, val farmId: String, val damId: String, val bornCount: Int, val liveCount: Int, val deadCount: Int, val occurredEpochDay: Long)

@Entity(tableName = "rabbit_palpations")
data class RabbitPalpationEntity(@PrimaryKey val id: String, val farmId: String, val waveId: String, val result: String, val occurredEpochDay: Long)

@Entity(tableName = "rabbit_kindlings")
data class RabbitKindlingEntity(@PrimaryKey val id: String, val farmId: String, val waveId: String, val liveCount: Int, val deadCount: Int, val occurredEpochDay: Long)

@Entity(tableName = "rabbit_fosters")
data class RabbitFosterEntity(@PrimaryKey val id: String, val farmId: String, val fromWaveId: String, val toWaveId: String, val kitCount: Int, val withinWindow: Boolean, val occurredEpochDay: Long)

@Entity(tableName = "suppliers", indices = [Index(value = ["farmId", "name"], unique = true)])
data class SupplierEntity(@PrimaryKey val id: String, val farmId: String, val name: String, val leadTimeDays: Int)

@Entity(tableName = "purchases")
data class PurchaseEntity(@PrimaryKey val id: String, val farmId: String, val supplierId: String, val itemId: String, val quantityMilli: Long, val amountMinor: Long, val currency: String, val occurredEpochDay: Long)

/** Exhaustive per-supplier, per-currency purchase aggregate; currencies are never summed together. */
data class SupplierCurrencyTotal(val supplierId: String, val currency: String, val amountMinor: Long, val purchaseCount: Int, val latestEpochDay: Long)

@Entity(tableName = "withdrawal_windows")
data class WithdrawalWindowEntity(@PrimaryKey val id: String, val farmId: String, val treatmentId: String, val product: String, val windowKind: String, val endsEpochDay: Long)

@Entity(tableName = "goat_milk_records")
data class GoatMilkEntity(@PrimaryKey val id: String, val farmId: String, val animalId: String, val litresMilli: Long, val occurredEpochDay: Long)

@Entity(tableName = "health_protocol_packs")
data class HealthPackEntity(@PrimaryKey val id: String, val farmId: String, val speciesCode: String, val name: String, val status: String, val acceptedByVet: String?)

@Entity(tableName = "rabbit_weans")
data class RabbitWeanEntity(@PrimaryKey val id: String, val farmId: String, val waveId: String, val weanedCount: Int, val occurredEpochDay: Long)

@Entity(tableName = "sheep_markings")
data class SheepMarkingEntity(@PrimaryKey val id: String, val farmId: String, val groupId: String?, val animalId: String?, val markedCount: Int, val occurredEpochDay: Long)

@Entity(tableName = "sheep_weanings")
data class SheepWeaningEntity(@PrimaryKey val id: String, val farmId: String, val groupId: String?, val animalId: String?, val weanedCount: Int, val occurredEpochDay: Long)

@Entity(tableName = "cattle_bcs_scores")
data class CattleBcsEntity(@PrimaryKey val id: String, val farmId: String, val animalId: String, val scale: String, val scoreTenths: Int, val occurredEpochDay: Long)

@Entity(tableName = "sheep_wool_clips")
data class SheepWoolEntity(@PrimaryKey val id: String, val farmId: String, val animalId: String?, val groupId: String?, val greasyGrams: Int, val occurredEpochDay: Long)

@Entity(tableName = "cattle_milk_records")
data class CattleMilkEntity(@PrimaryKey val id: String, val farmId: String, val animalId: String, val litresMilli: Long, val occurredEpochDay: Long)

@Entity(tableName = "sheep_dag_scores")
data class SheepDagEntity(@PrimaryKey val id: String, val farmId: String, val animalId: String, val score: Int, val occurredEpochDay: Long)

@Entity(tableName = "sheep_footrot_scores")
data class SheepFootrotEntity(@PrimaryKey val id: String, val farmId: String, val animalId: String, val score: Int, val occurredEpochDay: Long)

@Entity(tableName = "rabbit_kits")
data class RabbitKitEntity(@PrimaryKey val id: String, val farmId: String, val waveId: String, val animalId: String?, val tempLabel: String, val sex: String, val status: String, val retention: String, val earTag: String?)

@Entity(tableName = "rabbit_retention_decisions")
data class RabbitRetentionEntity(@PrimaryKey val id: String, val farmId: String, val kitId: String, val decision: String, val occurredEpochDay: Long)

@Entity(tableName = "rabbit_sales_waitlist")
data class RabbitWaitlistEntity(@PrimaryKey val id: String, val farmId: String, val contactName: String, val desiredSex: String?, val qty: Int, val status: String, val matchedKitId: String?)

@Entity(tableName = "rabbit_sales_contracts")
data class RabbitContractEntity(@PrimaryKey val id: String, val farmId: String, val waitlistId: String?, val buyerName: String, val animalId: String?, val amountMinor: Long, val currency: String, val status: String, val occurredEpochDay: Long)

@Entity(tableName = "rabbit_market_plans")
data class RabbitMarketPlanEntity(@PrimaryKey val id: String, val farmId: String, val kitId: String?, val waveId: String?, val targetWeightGrams: Int, val targetEpochDay: Long, val purpose: String, val status: String)

@Entity(tableName = "goat_bcs_scores")
data class GoatBcsEntity(@PrimaryKey val id: String, val farmId: String, val animalId: String, val scoreTenths: Int, val occurredEpochDay: Long)

@Entity(tableName = "cattle_locomotion_scores")
data class CattleLocomotionEntity(@PrimaryKey val id: String, val farmId: String, val animalId: String, val score: Int, val occurredEpochDay: Long)

@Entity(tableName = "cattle_scc_records")
data class CattleSccEntity(@PrimaryKey val id: String, val farmId: String, val animalId: String, val cellsPerMl: Int, val dimDays: Int?, val occurredEpochDay: Long)

@Entity(tableName = "sheep_shearing_events")
data class SheepShearingEntity(@PrimaryKey val id: String, val farmId: String, val animalId: String?, val groupId: String?, val kind: String, val greasyGrams: Int?, val occurredEpochDay: Long)

@Entity(tableName = "poultry_houses", indices = [Index(value = ["farmId", "code"], unique = true)])
data class PoultryHouseEntity(@PrimaryKey val id: String, val farmId: String, val code: String, val kind: String, val poultryKindCode: String)

@Entity(tableName = "poultry_hatches")
data class PoultryHatchEntity(
    @PrimaryKey val id: String,
    val farmId: String,
    val poultryKindCode: String,
    val houseId: String?,
    val groupId: String?,
    val eggsSet: Int,
    val incubationDays: Int,
    val setEpochDay: Long,
    val status: String,
    val fertile: Int?,
    val infertile: Int?,
    val midDead: Int?,
    val hatched: Int?,
    val culls: Int?,
    val placementGroupId: String?,
)

@Entity(tableName = "sheep_flystrike_scores")
data class SheepFlystrikeEntity(@PrimaryKey val id: String, val farmId: String, val animalId: String, val score: Int, val region: String?, val occurredEpochDay: Long)

@Entity(tableName = "rabbit_mating_outcomes")
data class RabbitMatingOutcomeEntity(@PrimaryKey val id: String, val farmId: String, val waveId: String, val outcome: String, val occurredEpochDay: Long)

@Entity(tableName = "goat_scc_records")
data class GoatSccEntity(@PrimaryKey val id: String, val farmId: String, val animalId: String, val cellsPerMl: Int, val dimDays: Int?, val occurredEpochDay: Long)

@Entity(tableName = "rabbit_programme_inventory_links")
data class RabbitInventoryLinkEntity(
    @PrimaryKey val farmId: String,
    val nestBeddingItemId: String?,
    val nestBeddingQtyMilli: Long,
    val doeFeedItemId: String?,
    val lowStockNotify: Boolean,
)

@Entity(tableName = "poultry_vaccinations")
data class PoultryVaccinationEntity(
    @PrimaryKey val id: String,
    val farmId: String,
    val groupId: String,
    val poultryKindCode: String,
    val formularyItemId: String,
    val occurredEpochDay: Long,
)

/** Exhaustive per-flock placement aggregate; not bounded by any presentation row limit. */
data class PoultryPlacementTotal(
    val groupId: String,
    val poultryKindCode: String,
    val placedHeads: Long,
    val firstPlacedEpochDay: Long,
    val placementCount: Int,
)

data class PoultryGroupHouse(val groupId: String, val houseId: String)

/** A farm-scoped record count keyed by a parent id (house, flock). */
data class RecordKeyCount(val key: String, val count: Int)

/** Exhaustive milk aggregate for one goat; not bounded by any presentation row limit. */
data class GoatMilkTotal(val animalId: String, val totalMilli: Long, val recordCount: Int, val firstEpochDay: Long, val latestEpochDay: Long)

/** Milk recorded for one goat on its latest recorded day. */
data class GoatMilkDayTotal(val animalId: String, val litresMilli: Long)

@Entity(tableName = "poultry_placements")
data class PoultryPlacementEntity(
    @PrimaryKey val id: String,
    val farmId: String,
    val groupId: String,
    val houseId: String,
    val poultryKindCode: String,
    val headCount: Int,
    val occurredEpochDay: Long,
)

@Entity(tableName = "poultry_biosecurity_walks")
data class PoultryBiosecurityEntity(
    @PrimaryKey val id: String,
    val farmId: String,
    val houseId: String?,
    val groupId: String?,
    val findings: String,
    val mixedSpecies: Boolean,
    val occurredEpochDay: Long,
)

@Entity(tableName = "cattle_dry_offs")
data class CattleDryOffEntity(
    @PrimaryKey val id: String,
    val farmId: String,
    val animalId: String,
    val occurredEpochDay: Long,
    val expectedCalvingEpochDay: Long?,
)

@Entity(tableName = "goat_heats")
data class GoatHeatEntity(@PrimaryKey val id: String, val farmId: String, val animalId: String, val occurredEpochDay: Long)

@Entity(tableName = "goat_matings")
data class GoatMatingEntity(@PrimaryKey val id: String, val farmId: String, val damId: String, val sireId: String?, val method: String, val occurredEpochDay: Long)

@Entity(tableName = "goat_pregnancy_checks")
data class GoatPregnancyEntity(@PrimaryKey val id: String, val farmId: String, val animalId: String, val result: String, val occurredEpochDay: Long)

@Entity(tableName = "animal_identifiers")
data class AnimalIdentifierEntity(@PrimaryKey val id: String, val farmId: String, val animalId: String, val type: String, val value: String, val isActive: Boolean, val assignedEpochDay: Long)

@Entity(tableName = "official_movements")
data class OfficialMovementEntity(@PrimaryKey val id: String, val farmId: String, val animalId: String, val speciesCode: String, val direction: String, val fromPlace: String?, val toPlace: String?, val occurredEpochDay: Long)

@Entity(tableName = "inventory_lots")
data class InventoryLotEntity(@PrimaryKey val id: String, val farmId: String, val itemId: String, val lotCode: String, val expiresEpochDay: Long, val quantityMilli: Long)

@Entity(tableName = "vet_visits")
data class VetVisitEntity(@PrimaryKey val id: String, val farmId: String, val speciesCode: String, val animalId: String?, val groupId: String?, val reason: String, val attendingVet: String, val occurredEpochDay: Long)

@Entity(tableName = "lab_results")
data class LabResultEntity(@PrimaryKey val id: String, val farmId: String, val animalId: String?, val groupId: String?, val testName: String, val resultText: String, val cellsPerMl: Int?, val occurredEpochDay: Long)

@Entity(tableName = "cattle_weanings")
data class CattleWeaningEntity(@PrimaryKey val id: String, val farmId: String, val animalId: String?, val groupId: String?, val weightGrams: Long?, val occurredEpochDay: Long)

@Entity(tableName = "goat_weanings")
data class GoatWeaningEntity(@PrimaryKey val id: String, val farmId: String, val animalId: String, val weightGrams: Long?, val occurredEpochDay: Long)

@Entity(tableName = "sheep_micron_tests")
data class SheepMicronEntity(@PrimaryKey val id: String, val farmId: String, val animalId: String?, val groupId: String?, val micronTenths: Int, val occurredEpochDay: Long)

@Entity(tableName = "farm_enabled_poultry_kinds", primaryKeys = ["farmId", "poultryKindCode"])
data class EnabledPoultryKindEntity(val farmId: String, val poultryKindCode: String)

@Entity(tableName = "rabbit_gi_stasis_flags")
data class RabbitGiStasisEntity(@PrimaryKey val id: String, val farmId: String, val animalId: String, val signs: String, val occurredEpochDay: Long)

@Entity(tableName = "pedigree_relations")
data class PedigreeRelationEntity(@PrimaryKey val id: String, val farmId: String, val animalId: String, val parentId: String, val relationType: String)

@Entity(tableName = "health_schedule_slots")
data class HealthPackSlotEntity(@PrimaryKey val id: String, val farmId: String, val packId: String, val slotCode: String, val title: String, val offsetDays: Int, val fromEvent: String, val isCore: Boolean)

@Entity(tableName = "health_pack_applications")
data class HealthPackApplyEntity(@PrimaryKey val id: String, val farmId: String, val packId: String, val animalId: String?, val groupId: String?, val anchorEpochDay: Long)

@Entity(tableName = "cattle_lot_placements")
data class CattleLotPlacementEntity(@PrimaryKey val id: String, val farmId: String, val groupId: String, val headCount: Int, val placedEpochDay: Long)

@Entity(tableName = "cattle_days_on_feed")
data class CattleDofEntity(@PrimaryKey val id: String, val farmId: String, val groupId: String, val daysOnFeed: Int, val occurredEpochDay: Long)

@Entity(tableName = "cattle_lot_closeouts")
data class CattleLotCloseEntity(@PrimaryKey val id: String, val farmId: String, val groupId: String, val headOut: Int, val weightGrams: Long?, val daysOnFeed: Int?, val occurredEpochDay: Long)

@Entity(tableName = "goat_lactation_plans")
data class GoatLactationPlanEntity(@PrimaryKey val id: String, val farmId: String, val animalId: String, val kiddingId: String?, val occurredEpochDay: Long)

@Entity(tableName = "inventory_reorder_alerts")
data class ReorderAlertEntity(@PrimaryKey val id: String, val farmId: String, val itemId: String, val onHandMilli: Long, val reorderMilli: Long, val occurredEpochDay: Long)

@Entity(tableName = "group_census_records")
data class GroupCensusEntity(@PrimaryKey val id: String, val farmId: String, val groupId: String, val headCount: Int, val occurredEpochDay: Long)

@Entity(tableName = "goat_kid_records")
data class GoatKidEntity(@PrimaryKey val id: String, val farmId: String, val animalId: String, val kiddingId: String, val damId: String)

@Dao
interface LifecycleDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertJoining(row: SheepJoiningEntity)
    @Upsert suspend fun upsertJoining(row: SheepJoiningEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertScan(row: SheepScanEntity)
    @Upsert suspend fun upsertScan(row: SheepScanEntity)
    @Query("SELECT * FROM sheep_scans WHERE farmId = :farmId ORDER BY occurredEpochDay DESC LIMIT :limit")
    suspend fun scans(farmId: String, limit: Int): List<SheepScanEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertLambing(row: SheepLambingEntity)
    @Upsert suspend fun upsertLambing(row: SheepLambingEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertService(row: CattleServiceEntity)
    @Upsert suspend fun upsertService(row: CattleServiceEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertPd(row: CattlePdEntity)
    @Upsert suspend fun upsertPd(row: CattlePdEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertCalving(row: CattleCalvingEntity)
    @Upsert suspend fun upsertCalving(row: CattleCalvingEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertPalpation(row: RabbitPalpationEntity)
    @Upsert suspend fun upsertPalpation(row: RabbitPalpationEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertKindling(row: RabbitKindlingEntity)
    @Upsert suspend fun upsertKindling(row: RabbitKindlingEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertFoster(row: RabbitFosterEntity)
    @Upsert suspend fun upsertFoster(row: RabbitFosterEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertSupplier(row: SupplierEntity)
    @Upsert suspend fun upsertSupplier(row: SupplierEntity)
    @Query("SELECT * FROM suppliers WHERE farmId = :farmId ORDER BY name")
    suspend fun suppliers(farmId: String): List<SupplierEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertPurchase(row: PurchaseEntity)
    @Upsert suspend fun upsertPurchase(row: PurchaseEntity)
    @Query("SELECT * FROM purchases WHERE farmId = :farmId ORDER BY occurredEpochDay DESC, id LIMIT :limit")
    suspend fun purchases(farmId: String, limit: Int): List<PurchaseEntity>
    @Query("SELECT COUNT(*) FROM purchases WHERE farmId = :farmId")
    suspend fun purchaseCount(farmId: String): Int
    @Query(
        """
        SELECT supplierId, currency, SUM(amountMinor) AS amountMinor, COUNT(*) AS purchaseCount,
            MAX(occurredEpochDay) AS latestEpochDay
        FROM purchases WHERE farmId = :farmId GROUP BY supplierId, currency ORDER BY supplierId, currency
        """,
    )
    suspend fun purchaseTotalsBySupplier(farmId: String): List<SupplierCurrencyTotal>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertWithdrawal(row: WithdrawalWindowEntity)
    @Upsert suspend fun upsertWithdrawal(row: WithdrawalWindowEntity)
    @Query("SELECT * FROM withdrawal_windows WHERE farmId = :farmId ORDER BY endsEpochDay DESC LIMIT :limit")
    suspend fun withdrawals(farmId: String, limit: Int): List<WithdrawalWindowEntity>
    @Query("SELECT COUNT(*) FROM withdrawal_windows WHERE farmId = :farmId")
    suspend fun withdrawalCount(farmId: String): Int
    /** Active through the end day, the same rule as HealthRecords.isActive and the farm home. */
    @Query("SELECT COUNT(*) FROM withdrawal_windows WHERE farmId = :farmId AND endsEpochDay >= :todayEpochDay")
    suspend fun activeWithdrawalCount(farmId: String, todayEpochDay: Long): Int
    /** Windows reach an animal only through its treatment; both sides stay inside the farm. */
    @Query(
        """
        SELECT w.* FROM withdrawal_windows AS w
        JOIN health_treatments AS t ON t.id = w.treatmentId AND t.farmId = w.farmId
        WHERE w.farmId = :farmId AND t.animalId = :animalId
        ORDER BY w.endsEpochDay DESC, w.id
        """,
    )
    suspend fun withdrawalsForAnimal(farmId: String, animalId: String): List<WithdrawalWindowEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertMilk(row: GoatMilkEntity)
    @Upsert suspend fun upsertMilk(row: GoatMilkEntity)
    @Query("SELECT * FROM goat_milk_records WHERE farmId = :farmId AND animalId = :animalId ORDER BY occurredEpochDay DESC")
    suspend fun milkFor(farmId: String, animalId: String): List<GoatMilkEntity>
    /** Exhaustive per-goat milk totals; litres are summed within one goat only. */
    @Query(
        """
        SELECT animalId, SUM(litresMilli) AS totalMilli, COUNT(*) AS recordCount,
            MIN(occurredEpochDay) AS firstEpochDay, MAX(occurredEpochDay) AS latestEpochDay
        FROM goat_milk_records WHERE farmId = :farmId GROUP BY animalId ORDER BY animalId
        """,
    )
    suspend fun goatMilkTotals(farmId: String): List<GoatMilkTotal>

    /**
     * Every active doe whose latest service has no kidding on or after it, with the latest pregnancy check
     * since that service. Exhaustive over the farm: due lists never come from a capped herd page.
     */
    @Query(
        """
        SELECT a.id AS animalId, a.tag AS tag, a.name AS name, m.occurredEpochDay AS serviceEpochDay,
            (SELECT p.result FROM goat_pregnancy_checks p
             WHERE p.farmId = a.farmId AND p.animalId = a.id AND p.occurredEpochDay >= m.occurredEpochDay
             ORDER BY p.occurredEpochDay DESC, p.id DESC LIMIT 1) AS latestCheckResult
        FROM animals a JOIN goat_matings m ON m.farmId = a.farmId AND m.damId = a.id
        WHERE a.farmId = :farmId AND a.speciesCode = 'goat' AND a.status = 'active'
          AND m.id = (SELECT m2.id FROM goat_matings m2 WHERE m2.farmId = a.farmId AND m2.damId = a.id
                      ORDER BY m2.occurredEpochDay DESC, m2.id DESC LIMIT 1)
          AND NOT EXISTS (SELECT 1 FROM kidding_events k
                          WHERE k.farmId = a.farmId AND k.damId = a.id AND k.occurredEpochDay >= m.occurredEpochDay)
        ORDER BY m.occurredEpochDay, a.tag, a.id
        """,
    )
    suspend fun goatKiddingDue(farmId: String): List<GoatDueRow>

    /**
     * Every active cow whose latest service has no calving on or after it, with the latest pregnancy
     * diagnosis since that service and the latest expected calving date recorded at a dry-off since then.
     * Exhaustive over the farm: due lists never come from a capped herd page.
     */
    @Query(
        """
        SELECT a.id AS animalId, a.tag AS tag, a.name AS name, s.occurredEpochDay AS serviceEpochDay, s.method AS method,
            (SELECT p.result FROM cattle_pd p
             WHERE p.farmId = a.farmId AND p.animalId = a.id AND p.occurredEpochDay >= s.occurredEpochDay
             ORDER BY p.occurredEpochDay DESC, p.id DESC LIMIT 1) AS latestPdResult,
            (SELECT d.expectedCalvingEpochDay FROM cattle_dry_offs d
             WHERE d.farmId = a.farmId AND d.animalId = a.id AND d.occurredEpochDay >= s.occurredEpochDay
               AND d.expectedCalvingEpochDay IS NOT NULL
             ORDER BY d.occurredEpochDay DESC, d.id DESC LIMIT 1) AS storedDueEpochDay
        FROM animals a JOIN cattle_services s ON s.farmId = a.farmId AND s.animalId = a.id
        WHERE a.farmId = :farmId AND a.speciesCode = 'cattle' AND a.status = 'active'
          AND s.id = (SELECT s2.id FROM cattle_services s2 WHERE s2.farmId = a.farmId AND s2.animalId = a.id
                      ORDER BY s2.occurredEpochDay DESC, s2.id DESC LIMIT 1)
          AND NOT EXISTS (SELECT 1 FROM cattle_calvings c
                          WHERE c.farmId = a.farmId AND c.damId = a.id AND c.occurredEpochDay >= s.occurredEpochDay)
        ORDER BY s.occurredEpochDay, a.tag, a.id
        """,
    )
    suspend fun cattleCalvingDue(farmId: String): List<CattleDueRow>

    /** Each sheep mob's latest joining (ram-in day), with the mob's name and recorded head count. */
    @Query(
        """
        SELECT j.id AS joiningId, j.groupId AS groupId, g.name AS groupName, g.headCount AS headCount, j.startedEpochDay AS startedEpochDay
        FROM sheep_joinings j JOIN animal_groups g ON g.farmId = j.farmId AND g.id = j.groupId
        WHERE j.farmId = :farmId
          AND j.id = (SELECT j2.id FROM sheep_joinings j2 WHERE j2.farmId = j.farmId AND j2.groupId = j.groupId
                      ORDER BY j2.startedEpochDay DESC, j2.id DESC LIMIT 1)
        ORDER BY j.startedEpochDay, g.name, j.groupId
        """,
    )
    suspend fun sheepLatestJoinings(farmId: String): List<SheepJoiningDueRow>

    /**
     * Every active ewe whose latest scan found her in lamb (not dry) with no lambing on or after that scan.
     * Exhaustive over the farm.
     */
    @Query(
        """
        SELECT a.id AS animalId, a.tag AS tag, a.name AS name, s.result AS result, s.occurredEpochDay AS scanEpochDay
        FROM animals a JOIN sheep_scans s ON s.farmId = a.farmId AND s.animalId = a.id
        WHERE a.farmId = :farmId AND a.speciesCode = 'sheep' AND a.status = 'active'
          AND s.id = (SELECT s2.id FROM sheep_scans s2 WHERE s2.farmId = a.farmId AND s2.animalId = a.id
                      ORDER BY s2.occurredEpochDay DESC, s2.id DESC LIMIT 1)
          AND s.result != 'dry'
          AND NOT EXISTS (SELECT 1 FROM sheep_lambings l
                          WHERE l.farmId = a.farmId AND l.damId = a.id AND l.occurredEpochDay >= s.occurredEpochDay)
        ORDER BY s.occurredEpochDay, a.tag, a.id
        """,
    )
    suspend fun sheepScannedInLamb(farmId: String): List<SheepInLambRow>
    /** Milk summed over each goat's latest recorded day, across every record on that day. */
    @Query(
        """
        SELECT m.animalId AS animalId, SUM(m.litresMilli) AS litresMilli
        FROM goat_milk_records m
        JOIN (SELECT animalId, MAX(occurredEpochDay) AS latestDay FROM goat_milk_records WHERE farmId = :farmId GROUP BY animalId) l
            ON l.animalId = m.animalId AND l.latestDay = m.occurredEpochDay
        WHERE m.farmId = :farmId GROUP BY m.animalId ORDER BY m.animalId
        """,
    )
    suspend fun goatMilkLatestDay(farmId: String): List<GoatMilkDayTotal>
    @Query("SELECT animalId AS `key`, COUNT(*) AS count FROM goat_scc_records WHERE farmId = :farmId GROUP BY animalId ORDER BY animalId")
    suspend fun goatSccCounts(farmId: String): List<RecordKeyCount>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertPack(row: HealthPackEntity)
    @Upsert suspend fun upsertPack(row: HealthPackEntity)
    @Query("SELECT * FROM health_protocol_packs WHERE farmId = :farmId ORDER BY name")
    suspend fun packs(farmId: String): List<HealthPackEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertWean(row: RabbitWeanEntity)
    @Upsert suspend fun upsertWean(row: RabbitWeanEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertMarking(row: SheepMarkingEntity)
    @Upsert suspend fun upsertMarking(row: SheepMarkingEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertWeaning(row: SheepWeaningEntity)
    @Upsert suspend fun upsertWeaning(row: SheepWeaningEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertBcs(row: CattleBcsEntity)
    @Upsert suspend fun upsertBcs(row: CattleBcsEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertWool(row: SheepWoolEntity)
    @Upsert suspend fun upsertWool(row: SheepWoolEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertCattleMilk(row: CattleMilkEntity)
    @Upsert suspend fun upsertCattleMilk(row: CattleMilkEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertDag(row: SheepDagEntity)
    @Upsert suspend fun upsertDag(row: SheepDagEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertFootrot(row: SheepFootrotEntity)
    @Upsert suspend fun upsertFootrot(row: SheepFootrotEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertKit(row: RabbitKitEntity)
    @Upsert suspend fun upsertKit(row: RabbitKitEntity)
    @Query("SELECT * FROM rabbit_kits WHERE farmId = :farmId ORDER BY tempLabel")
    suspend fun kits(farmId: String): List<RabbitKitEntity>
    @Query("SELECT * FROM rabbit_kits WHERE farmId = :farmId AND id = :kitId LIMIT 1")
    suspend fun kit(farmId: String, kitId: String): RabbitKitEntity?
    @Query("SELECT * FROM rabbit_palpations WHERE farmId = :farmId ORDER BY occurredEpochDay DESC, id")
    suspend fun rabbitPalpations(farmId: String): List<RabbitPalpationEntity>
    @Query("SELECT * FROM rabbit_kindlings WHERE farmId = :farmId ORDER BY occurredEpochDay DESC, id")
    suspend fun rabbitKindlings(farmId: String): List<RabbitKindlingEntity>
    @Query("SELECT DISTINCT waveId FROM rabbit_kindlings WHERE farmId = :farmId ORDER BY waveId")
    suspend fun rabbitKindledWaveIds(farmId: String): List<String>
    @Query("SELECT * FROM rabbit_fosters WHERE farmId = :farmId ORDER BY occurredEpochDay DESC, id")
    suspend fun rabbitFosters(farmId: String): List<RabbitFosterEntity>
    @Query("SELECT * FROM rabbit_weans WHERE farmId = :farmId ORDER BY occurredEpochDay DESC, id")
    suspend fun rabbitWeans(farmId: String): List<RabbitWeanEntity>
    @Query("SELECT * FROM rabbit_mating_outcomes WHERE farmId = :farmId ORDER BY occurredEpochDay DESC, id")
    suspend fun rabbitMatingOutcomes(farmId: String): List<RabbitMatingOutcomeEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertRetention(row: RabbitRetentionEntity)
    @Upsert suspend fun upsertRetention(row: RabbitRetentionEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertWaitlist(row: RabbitWaitlistEntity)
    @Upsert suspend fun upsertWaitlist(row: RabbitWaitlistEntity)
    @Query("SELECT * FROM rabbit_sales_waitlist WHERE farmId = :farmId ORDER BY contactName, id")
    suspend fun waitlist(farmId: String): List<RabbitWaitlistEntity>
    @Query("SELECT * FROM rabbit_retention_decisions WHERE farmId = :farmId ORDER BY occurredEpochDay DESC, id")
    suspend fun retentionDecisions(farmId: String): List<RabbitRetentionEntity>
    @Query("SELECT * FROM rabbit_sales_contracts WHERE farmId = :farmId ORDER BY occurredEpochDay DESC, id")
    suspend fun rabbitContracts(farmId: String): List<RabbitContractEntity>
    @Query("SELECT * FROM rabbit_market_plans WHERE farmId = :farmId ORDER BY targetEpochDay, id")
    suspend fun rabbitMarketPlans(farmId: String): List<RabbitMarketPlanEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertContract(row: RabbitContractEntity)
    @Upsert suspend fun upsertContract(row: RabbitContractEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertPlan(row: RabbitMarketPlanEntity)
    @Upsert suspend fun upsertPlan(row: RabbitMarketPlanEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertGoatBcs(row: GoatBcsEntity)
    @Upsert suspend fun upsertGoatBcs(row: GoatBcsEntity)
    @Query("SELECT * FROM goat_bcs_scores WHERE farmId = :farmId AND animalId = :animalId ORDER BY occurredEpochDay DESC")
    suspend fun goatBcsFor(farmId: String, animalId: String): List<GoatBcsEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertLocomotion(row: CattleLocomotionEntity)
    @Upsert suspend fun upsertLocomotion(row: CattleLocomotionEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertScc(row: CattleSccEntity)
    @Upsert suspend fun upsertScc(row: CattleSccEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertShearing(row: SheepShearingEntity)
    @Upsert suspend fun upsertShearing(row: SheepShearingEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertHouse(row: PoultryHouseEntity)
    @Upsert suspend fun upsertHouse(row: PoultryHouseEntity)
    @Query("SELECT * FROM poultry_houses WHERE farmId = :farmId ORDER BY code")
    suspend fun houses(farmId: String): List<PoultryHouseEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertHatch(row: PoultryHatchEntity)
    @Upsert suspend fun upsertHatch(row: PoultryHatchEntity)
    @Query("SELECT * FROM poultry_hatches WHERE farmId = :farmId ORDER BY setEpochDay DESC")
    suspend fun hatches(farmId: String): List<PoultryHatchEntity>
    @Query("SELECT * FROM poultry_hatches WHERE farmId = :farmId AND id = :hatchId LIMIT 1")
    suspend fun hatch(farmId: String, hatchId: String): PoultryHatchEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertFlystrike(row: SheepFlystrikeEntity)
    @Upsert suspend fun upsertFlystrike(row: SheepFlystrikeEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertMatingOutcome(row: RabbitMatingOutcomeEntity)
    @Upsert suspend fun upsertMatingOutcome(row: RabbitMatingOutcomeEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertGoatScc(row: GoatSccEntity)
    @Upsert suspend fun upsertGoatScc(row: GoatSccEntity)
    @Query("SELECT * FROM goat_scc_records WHERE farmId = :farmId AND animalId = :animalId ORDER BY occurredEpochDay DESC")
    suspend fun goatSccFor(farmId: String, animalId: String): List<GoatSccEntity>
    @Upsert suspend fun upsertInventoryLink(row: RabbitInventoryLinkEntity)
    @Query("SELECT * FROM rabbit_programme_inventory_links WHERE farmId = :farmId LIMIT 1")
    suspend fun inventoryLink(farmId: String): RabbitInventoryLinkEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertVaccination(row: PoultryVaccinationEntity)
    @Upsert suspend fun upsertVaccination(row: PoultryVaccinationEntity)
    @Query("SELECT * FROM poultry_vaccinations WHERE farmId = :farmId ORDER BY occurredEpochDay DESC, id LIMIT :limit")
    suspend fun vaccinations(farmId: String, limit: Int = 50): List<PoultryVaccinationEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertDryOff(row: CattleDryOffEntity)
    @Upsert suspend fun upsertDryOff(row: CattleDryOffEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertPlacement(row: PoultryPlacementEntity)
    @Upsert suspend fun upsertPlacement(row: PoultryPlacementEntity)
    @Query("SELECT * FROM poultry_placements WHERE farmId = :farmId ORDER BY occurredEpochDay DESC, id LIMIT :limit")
    suspend fun placements(farmId: String, limit: Int = 50): List<PoultryPlacementEntity>
    @Query(
        """
        SELECT groupId, MIN(poultryKindCode) AS poultryKindCode, SUM(headCount) AS placedHeads,
            MIN(occurredEpochDay) AS firstPlacedEpochDay, COUNT(*) AS placementCount
        FROM poultry_placements WHERE farmId = :farmId GROUP BY groupId ORDER BY groupId
        """,
    )
    suspend fun poultryPlacementTotals(farmId: String): List<PoultryPlacementTotal>
    @Query("SELECT DISTINCT groupId, houseId FROM poultry_placements WHERE farmId = :farmId ORDER BY groupId, houseId")
    suspend fun poultryGroupHouses(farmId: String): List<PoultryGroupHouse>
    @Query("SELECT houseId AS `key`, COUNT(*) AS count FROM poultry_placements WHERE farmId = :farmId GROUP BY houseId ORDER BY houseId")
    suspend fun poultryPlacementCountsByHouse(farmId: String): List<RecordKeyCount>
    @Query("SELECT groupId AS `key`, COUNT(*) AS count FROM poultry_vaccinations WHERE farmId = :farmId GROUP BY groupId ORDER BY groupId")
    suspend fun poultryVaccinationCountsByGroup(farmId: String): List<RecordKeyCount>
    @Query("SELECT houseId AS `key`, COUNT(*) AS count FROM poultry_biosecurity_walks WHERE farmId = :farmId AND houseId IS NOT NULL GROUP BY houseId ORDER BY houseId")
    suspend fun poultryWalkCountsByHouse(farmId: String): List<RecordKeyCount>
    @Query("SELECT COUNT(*) FROM poultry_biosecurity_walks WHERE farmId = :farmId")
    suspend fun poultryWalkCount(farmId: String): Int
    @Query("SELECT COUNT(*) FROM poultry_biosecurity_walks WHERE farmId = :farmId AND mixedSpecies = 1")
    suspend fun poultryMixedSpeciesWalkCount(farmId: String): Int
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertBiosecurity(row: PoultryBiosecurityEntity)
    @Upsert suspend fun upsertBiosecurity(row: PoultryBiosecurityEntity)
    @Query("SELECT * FROM poultry_biosecurity_walks WHERE farmId = :farmId ORDER BY occurredEpochDay DESC, id LIMIT :limit")
    suspend fun biosecurityWalks(farmId: String, limit: Int): List<PoultryBiosecurityEntity>
    @Query("SELECT * FROM poultry_placements WHERE farmId = :farmId AND groupId = :groupId ORDER BY occurredEpochDay DESC, id")
    suspend fun placementsForGroup(farmId: String, groupId: String): List<PoultryPlacementEntity>
    @Query("SELECT * FROM poultry_vaccinations WHERE farmId = :farmId AND groupId = :groupId ORDER BY occurredEpochDay DESC, id")
    suspend fun vaccinationsForGroup(farmId: String, groupId: String): List<PoultryVaccinationEntity>
    @Query("SELECT * FROM poultry_biosecurity_walks WHERE farmId = :farmId AND groupId = :groupId ORDER BY occurredEpochDay DESC, id")
    suspend fun walksForGroup(farmId: String, groupId: String): List<PoultryBiosecurityEntity>
    @Query("SELECT * FROM poultry_hatches WHERE farmId = :farmId AND placementGroupId = :groupId ORDER BY setEpochDay DESC, id")
    suspend fun hatchesPlacedInto(farmId: String, groupId: String): List<PoultryHatchEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertHeat(row: GoatHeatEntity)
    @Upsert suspend fun upsertHeat(row: GoatHeatEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertMating(row: GoatMatingEntity)
    @Upsert suspend fun upsertMating(row: GoatMatingEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertPregnancy(row: GoatPregnancyEntity)
    @Upsert suspend fun upsertPregnancy(row: GoatPregnancyEntity)
    @Query("SELECT * FROM goat_heats WHERE farmId = :farmId AND animalId = :animalId ORDER BY occurredEpochDay DESC, id")
    suspend fun goatHeatsFor(farmId: String, animalId: String): List<GoatHeatEntity>
    @Query("SELECT * FROM goat_matings WHERE farmId = :farmId AND damId = :damId ORDER BY occurredEpochDay DESC, id")
    suspend fun goatMatingsForDam(farmId: String, damId: String): List<GoatMatingEntity>
    @Query("SELECT * FROM goat_pregnancy_checks WHERE farmId = :farmId AND animalId = :animalId ORDER BY occurredEpochDay DESC, id")
    suspend fun goatPregnanciesFor(farmId: String, animalId: String): List<GoatPregnancyEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertIdentifier(row: AnimalIdentifierEntity)
    @Upsert suspend fun upsertIdentifier(row: AnimalIdentifierEntity)
    @Query("UPDATE animal_identifiers SET isActive = 0 WHERE farmId = :farmId AND animalId = :animalId AND type = :type")
    suspend fun deactivateIdentifiers(farmId: String, animalId: String, type: String)
    @Query("""
        SELECT * FROM animal_identifiers
        WHERE farmId = :farmId
          AND isActive = 1
          AND type IN ('rfid', 'eid', 'ear_tag', 'farm_id', 'official_id', 'registration')
          AND value = :value COLLATE NOCASE
        ORDER BY assignedEpochDay DESC, id
        LIMIT 1
    """)
    suspend fun activeIdentifierByValue(farmId: String, value: String): AnimalIdentifierEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertMovement(row: OfficialMovementEntity)
    @Upsert suspend fun upsertMovement(row: OfficialMovementEntity)
    @Query("SELECT * FROM official_movements WHERE farmId = :farmId AND animalId = :animalId ORDER BY occurredEpochDay DESC, id")
    suspend fun movementsForAnimal(farmId: String, animalId: String): List<OfficialMovementEntity>
    @Query("SELECT * FROM animal_identifiers WHERE farmId = :farmId AND animalId = :animalId ORDER BY isActive DESC, assignedEpochDay DESC, id")
    suspend fun identifiersForAnimal(farmId: String, animalId: String): List<AnimalIdentifierEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertLot(row: InventoryLotEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertLotIfMissing(row: InventoryLotEntity): Long
    @Upsert suspend fun upsertLot(row: InventoryLotEntity)
    @Query("SELECT * FROM inventory_lots WHERE farmId = :farmId AND id = :lotId LIMIT 1")
    suspend fun lot(farmId: String, lotId: String): InventoryLotEntity?
    @Query("SELECT * FROM inventory_lots WHERE farmId = :farmId AND itemId = :itemId AND quantityMilli > 0 ORDER BY expiresEpochDay, id")
    suspend fun lotsFor(farmId: String, itemId: String): List<InventoryLotEntity>
    @Query("SELECT * FROM inventory_lots WHERE farmId = :farmId AND quantityMilli > 0 ORDER BY expiresEpochDay, id")
    suspend fun openLots(farmId: String): List<InventoryLotEntity>
    @Query("UPDATE inventory_lots SET quantityMilli = :quantityMilli WHERE farmId = :farmId AND id = :lotId")
    suspend fun setLotQuantity(farmId: String, lotId: String, quantityMilli: Long)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertVetVisit(row: VetVisitEntity)
    @Upsert suspend fun upsertVetVisit(row: VetVisitEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertLab(row: LabResultEntity)
    @Upsert suspend fun upsertLab(row: LabResultEntity)
    @Query("SELECT * FROM vet_visits WHERE farmId = :farmId ORDER BY occurredEpochDay DESC, id LIMIT :limit")
    suspend fun vetVisits(farmId: String, limit: Int): List<VetVisitEntity>
    @Query("SELECT * FROM lab_results WHERE farmId = :farmId ORDER BY occurredEpochDay DESC, id LIMIT :limit")
    suspend fun labResults(farmId: String, limit: Int): List<LabResultEntity>
    @Query("SELECT * FROM vet_visits WHERE farmId = :farmId AND animalId = :animalId ORDER BY occurredEpochDay DESC, id")
    suspend fun vetVisitsForAnimal(farmId: String, animalId: String): List<VetVisitEntity>
    @Query("SELECT * FROM lab_results WHERE farmId = :farmId AND animalId = :animalId ORDER BY occurredEpochDay DESC, id")
    suspend fun labResultsForAnimal(farmId: String, animalId: String): List<LabResultEntity>
    @Query("SELECT COUNT(*) FROM vet_visits WHERE farmId = :farmId")
    suspend fun vetVisitCount(farmId: String): Int
    @Query("SELECT COUNT(*) FROM lab_results WHERE farmId = :farmId")
    suspend fun labResultCount(farmId: String): Int
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertCattleWeaning(row: CattleWeaningEntity)
    @Upsert suspend fun upsertCattleWeaning(row: CattleWeaningEntity)
    @Query("SELECT * FROM cattle_services WHERE farmId = :farmId AND animalId = :animalId ORDER BY occurredEpochDay DESC, id")
    suspend fun cattleServicesFor(farmId: String, animalId: String): List<CattleServiceEntity>
    @Query("SELECT * FROM cattle_pd WHERE farmId = :farmId AND animalId = :animalId ORDER BY occurredEpochDay DESC, id")
    suspend fun cattlePdsFor(farmId: String, animalId: String): List<CattlePdEntity>
    @Query("SELECT * FROM cattle_calvings WHERE farmId = :farmId AND damId = :animalId ORDER BY occurredEpochDay DESC, id")
    suspend fun cattleCalvingsFor(farmId: String, animalId: String): List<CattleCalvingEntity>
    @Query("SELECT * FROM cattle_bcs_scores WHERE farmId = :farmId AND animalId = :animalId ORDER BY occurredEpochDay DESC, id")
    suspend fun cattleBcsFor(farmId: String, animalId: String): List<CattleBcsEntity>
    @Query("SELECT * FROM cattle_milk_records WHERE farmId = :farmId AND animalId = :animalId ORDER BY occurredEpochDay DESC, id")
    suspend fun cattleMilkFor(farmId: String, animalId: String): List<CattleMilkEntity>
    @Query("SELECT * FROM cattle_locomotion_scores WHERE farmId = :farmId AND animalId = :animalId ORDER BY occurredEpochDay DESC, id")
    suspend fun cattleLocomotionFor(farmId: String, animalId: String): List<CattleLocomotionEntity>
    @Query("SELECT * FROM cattle_scc_records WHERE farmId = :farmId AND animalId = :animalId ORDER BY occurredEpochDay DESC, id")
    suspend fun cattleSccFor(farmId: String, animalId: String): List<CattleSccEntity>
    @Query("SELECT * FROM cattle_dry_offs WHERE farmId = :farmId AND animalId = :animalId ORDER BY occurredEpochDay DESC, id")
    suspend fun cattleDryOffsFor(farmId: String, animalId: String): List<CattleDryOffEntity>
    @Query("SELECT * FROM cattle_weanings WHERE farmId = :farmId AND animalId = :animalId ORDER BY occurredEpochDay DESC, id")
    suspend fun cattleWeaningsFor(farmId: String, animalId: String): List<CattleWeaningEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertGoatWeaning(row: GoatWeaningEntity)
    @Upsert suspend fun upsertGoatWeaning(row: GoatWeaningEntity)
    @Query("SELECT * FROM goat_weanings WHERE farmId = :farmId AND animalId = :animalId ORDER BY occurredEpochDay DESC, id")
    suspend fun goatWeaningsFor(farmId: String, animalId: String): List<GoatWeaningEntity>
    @Query("SELECT * FROM sheep_scans WHERE farmId = :farmId AND animalId = :animalId ORDER BY occurredEpochDay DESC, id")
    suspend fun sheepScansFor(farmId: String, animalId: String): List<SheepScanEntity>
    @Query("SELECT * FROM sheep_lambings WHERE farmId = :farmId AND damId = :animalId ORDER BY occurredEpochDay DESC, id")
    suspend fun sheepLambingsFor(farmId: String, animalId: String): List<SheepLambingEntity>
    @Query("SELECT * FROM sheep_dag_scores WHERE farmId = :farmId AND animalId = :animalId ORDER BY occurredEpochDay DESC, id")
    suspend fun sheepDagFor(farmId: String, animalId: String): List<SheepDagEntity>
    @Query("SELECT * FROM sheep_footrot_scores WHERE farmId = :farmId AND animalId = :animalId ORDER BY occurredEpochDay DESC, id")
    suspend fun sheepFootrotFor(farmId: String, animalId: String): List<SheepFootrotEntity>
    @Query("SELECT * FROM sheep_flystrike_scores WHERE farmId = :farmId AND animalId = :animalId ORDER BY occurredEpochDay DESC, id")
    suspend fun sheepFlystrikeFor(farmId: String, animalId: String): List<SheepFlystrikeEntity>
    @Query("SELECT * FROM sheep_wool_clips WHERE farmId = :farmId AND animalId = :animalId ORDER BY occurredEpochDay DESC, id")
    suspend fun sheepWoolFor(farmId: String, animalId: String): List<SheepWoolEntity>
    @Query("SELECT * FROM sheep_shearing_events WHERE farmId = :farmId AND animalId = :animalId ORDER BY occurredEpochDay DESC, id")
    suspend fun sheepShearingFor(farmId: String, animalId: String): List<SheepShearingEntity>
    @Query("SELECT * FROM sheep_micron_tests WHERE farmId = :farmId AND animalId = :animalId ORDER BY occurredEpochDay DESC, id")
    suspend fun sheepMicronFor(farmId: String, animalId: String): List<SheepMicronEntity>
    @Query("SELECT * FROM sheep_markings WHERE farmId = :farmId AND animalId = :animalId ORDER BY occurredEpochDay DESC, id")
    suspend fun sheepMarkingsFor(farmId: String, animalId: String): List<SheepMarkingEntity>
    @Query("SELECT * FROM sheep_weanings WHERE farmId = :farmId AND animalId = :animalId ORDER BY occurredEpochDay DESC, id")
    suspend fun sheepWeaningsFor(farmId: String, animalId: String): List<SheepWeaningEntity>
    @Query("SELECT * FROM sheep_wool_clips WHERE farmId = :farmId ORDER BY occurredEpochDay DESC, id LIMIT :limit")
    suspend fun sheepWoolClips(farmId: String, limit: Int): List<SheepWoolEntity>
    @Query("SELECT * FROM sheep_shearing_events WHERE farmId = :farmId ORDER BY occurredEpochDay DESC, id LIMIT :limit")
    suspend fun sheepShearingEvents(farmId: String, limit: Int): List<SheepShearingEntity>
    @Query("SELECT * FROM sheep_micron_tests WHERE farmId = :farmId ORDER BY occurredEpochDay DESC, id LIMIT :limit")
    suspend fun sheepMicronTests(farmId: String, limit: Int): List<SheepMicronEntity>
    @Query("SELECT COALESCE(SUM(greasyGrams), 0) FROM sheep_wool_clips WHERE farmId = :farmId")
    suspend fun sheepWoolGreasyGramsTotal(farmId: String): Long
    @Query("SELECT COUNT(*) FROM sheep_wool_clips WHERE farmId = :farmId")
    suspend fun sheepWoolClipCount(farmId: String): Int
    @Query("SELECT COUNT(*) FROM sheep_shearing_events WHERE farmId = :farmId")
    suspend fun sheepShearingEventCount(farmId: String): Int
    @Query("SELECT COUNT(*) FROM sheep_micron_tests WHERE farmId = :farmId")
    suspend fun sheepMicronTestCount(farmId: String): Int
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertMicron(row: SheepMicronEntity)
    @Upsert suspend fun upsertMicron(row: SheepMicronEntity)
    @Upsert suspend fun upsertEnabledKind(row: EnabledPoultryKindEntity)
    @Query("SELECT * FROM farm_enabled_poultry_kinds WHERE farmId = :farmId ORDER BY poultryKindCode")
    suspend fun enabledPoultryKinds(farmId: String): List<EnabledPoultryKindEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertGiStasis(row: RabbitGiStasisEntity)
    @Upsert suspend fun upsertGiStasis(row: RabbitGiStasisEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertPedigree(row: PedigreeRelationEntity)
    @Upsert suspend fun upsertPedigree(row: PedigreeRelationEntity)
    @Query("SELECT * FROM pedigree_relations WHERE farmId = :farmId AND animalId = :animalId ORDER BY relationType, id")
    suspend fun pedigreeParents(farmId: String, animalId: String): List<PedigreeRelationEntity>
    @Query("SELECT * FROM pedigree_relations WHERE farmId = :farmId AND parentId = :parentId ORDER BY animalId, id")
    suspend fun pedigreeChildren(farmId: String, parentId: String): List<PedigreeRelationEntity>
    @Query("SELECT COUNT(*) FROM pedigree_relations WHERE farmId = :farmId")
    suspend fun pedigreeRelationCount(farmId: String): Int
    @Query("SELECT COUNT(DISTINCT animalId) FROM pedigree_relations WHERE farmId = :farmId")
    suspend fun animalsWithParentageCount(farmId: String): Int
    /**
     * Animals with more than one distinct recorded sire, or more than one distinct recorded dam
     * (genetic_dam outranks dam, mirroring [PedigreeQueries]).
     */
    @Query(
        """
        SELECT animalId FROM pedigree_relations WHERE farmId = :farmId
        GROUP BY animalId
        HAVING COUNT(DISTINCT CASE WHEN relationType = 'sire' THEN parentId END) > 1
            OR COUNT(DISTINCT CASE WHEN relationType IN ('dam', 'genetic_dam') THEN parentId END) > 1
        """,
    )
    suspend fun animalsWithConflictingParentage(farmId: String): List<String>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertPackSlot(row: HealthPackSlotEntity)
    @Upsert suspend fun upsertPackSlot(row: HealthPackSlotEntity)
    @Query("SELECT * FROM health_schedule_slots WHERE farmId = :farmId AND packId = :packId AND isCore = 1")
    suspend fun coreSlots(farmId: String, packId: String): List<HealthPackSlotEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertPackApply(row: HealthPackApplyEntity)
    @Upsert suspend fun upsertPackApply(row: HealthPackApplyEntity)
    @Query("SELECT * FROM health_schedule_slots WHERE farmId = :farmId ORDER BY packId, offsetDays, slotCode, id")
    suspend fun packSlots(farmId: String): List<HealthPackSlotEntity>
    @Query("SELECT packId AS `key`, COUNT(*) AS count FROM health_pack_applications WHERE farmId = :farmId GROUP BY packId ORDER BY packId")
    suspend fun packApplicationCounts(farmId: String): List<RecordKeyCount>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertLotPlace(row: CattleLotPlacementEntity)
    @Upsert suspend fun upsertLotPlace(row: CattleLotPlacementEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertDof(row: CattleDofEntity)
    @Upsert suspend fun upsertDof(row: CattleDofEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertLotClose(row: CattleLotCloseEntity)
    @Upsert suspend fun upsertLotClose(row: CattleLotCloseEntity)
    @Query("SELECT * FROM cattle_lot_placements WHERE farmId = :farmId ORDER BY placedEpochDay DESC, id")
    suspend fun cattleLotPlacements(farmId: String): List<CattleLotPlacementEntity>
    @Query("SELECT * FROM cattle_days_on_feed WHERE farmId = :farmId ORDER BY occurredEpochDay DESC, id")
    suspend fun cattleDaysOnFeed(farmId: String): List<CattleDofEntity>
    @Query("SELECT * FROM cattle_lot_closeouts WHERE farmId = :farmId ORDER BY occurredEpochDay DESC, id")
    suspend fun cattleLotCloseouts(farmId: String): List<CattleLotCloseEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertLactation(row: GoatLactationPlanEntity)
    @Upsert suspend fun upsertLactation(row: GoatLactationPlanEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertReorderAlert(row: ReorderAlertEntity)
    @Upsert suspend fun upsertReorderAlert(row: ReorderAlertEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertCensus(row: GroupCensusEntity)
    @Upsert suspend fun upsertCensus(row: GroupCensusEntity)
    @Query("SELECT * FROM group_census_records WHERE farmId = :farmId AND groupId = :groupId ORDER BY occurredEpochDay DESC, id")
    suspend fun censusForGroup(farmId: String, groupId: String): List<GroupCensusEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertKid(row: GoatKidEntity)
    @Upsert suspend fun upsertKid(row: GoatKidEntity)
    @Query("SELECT COUNT(*) FROM goat_kid_records WHERE farmId = :farmId AND kiddingId = :kiddingId")
    suspend fun kidCount(farmId: String, kiddingId: String): Long
    @Query("SELECT * FROM goat_kid_records WHERE farmId = :farmId AND kiddingId = :kiddingId ORDER BY animalId")
    suspend fun kidsForKidding(farmId: String, kiddingId: String): List<GoatKidEntity>
    @Query("SELECT * FROM goat_kid_records WHERE farmId = :farmId AND animalId = :animalId LIMIT 1")
    suspend fun kidRecordFor(farmId: String, animalId: String): GoatKidEntity?
}

/** One doe awaiting kidding: her latest service and the latest pregnancy check since it, if any. */
data class GoatDueRow(val animalId: String, val tag: String, val name: String?, val serviceEpochDay: Long, val latestCheckResult: String?)

data class SheepJoiningDueRow(val joiningId: String, val groupId: String, val groupName: String, val headCount: Int, val startedEpochDay: Long)

data class SheepInLambRow(val animalId: String, val tag: String, val name: String?, val result: String, val scanEpochDay: Long)

data class CattleDueRow(
    val animalId: String,
    val tag: String,
    val name: String?,
    val serviceEpochDay: Long,
    val method: String,
    val latestPdResult: String?,
    val storedDueEpochDay: Long?,
)
