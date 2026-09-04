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
    @Query("SELECT * FROM purchases WHERE farmId = :farmId ORDER BY occurredEpochDay DESC LIMIT :limit")
    suspend fun purchases(farmId: String, limit: Int): List<PurchaseEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertWithdrawal(row: WithdrawalWindowEntity)
    @Upsert suspend fun upsertWithdrawal(row: WithdrawalWindowEntity)
    @Query("SELECT * FROM withdrawal_windows WHERE farmId = :farmId ORDER BY endsEpochDay DESC LIMIT :limit")
    suspend fun withdrawals(farmId: String, limit: Int): List<WithdrawalWindowEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertMilk(row: GoatMilkEntity)
    @Upsert suspend fun upsertMilk(row: GoatMilkEntity)
    @Query("SELECT * FROM goat_milk_records WHERE farmId = :farmId AND animalId = :animalId ORDER BY occurredEpochDay DESC")
    suspend fun milkFor(farmId: String, animalId: String): List<GoatMilkEntity>
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
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertRetention(row: RabbitRetentionEntity)
    @Upsert suspend fun upsertRetention(row: RabbitRetentionEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertWaitlist(row: RabbitWaitlistEntity)
    @Upsert suspend fun upsertWaitlist(row: RabbitWaitlistEntity)
    @Query("SELECT * FROM rabbit_sales_waitlist WHERE farmId = :farmId ORDER BY contactName")
    suspend fun waitlist(farmId: String): List<RabbitWaitlistEntity>
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
    @Query("SELECT * FROM poultry_vaccinations WHERE farmId = :farmId ORDER BY occurredEpochDay DESC LIMIT :limit")
    suspend fun vaccinations(farmId: String, limit: Int = 50): List<PoultryVaccinationEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertDryOff(row: CattleDryOffEntity)
    @Upsert suspend fun upsertDryOff(row: CattleDryOffEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertPlacement(row: PoultryPlacementEntity)
    @Upsert suspend fun upsertPlacement(row: PoultryPlacementEntity)
    @Query("SELECT * FROM poultry_placements WHERE farmId = :farmId ORDER BY occurredEpochDay DESC LIMIT :limit")
    suspend fun placements(farmId: String, limit: Int = 50): List<PoultryPlacementEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertBiosecurity(row: PoultryBiosecurityEntity)
    @Upsert suspend fun upsertBiosecurity(row: PoultryBiosecurityEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertHeat(row: GoatHeatEntity)
    @Upsert suspend fun upsertHeat(row: GoatHeatEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertMating(row: GoatMatingEntity)
    @Upsert suspend fun upsertMating(row: GoatMatingEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertPregnancy(row: GoatPregnancyEntity)
    @Upsert suspend fun upsertPregnancy(row: GoatPregnancyEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertIdentifier(row: AnimalIdentifierEntity)
    @Upsert suspend fun upsertIdentifier(row: AnimalIdentifierEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertMovement(row: OfficialMovementEntity)
    @Upsert suspend fun upsertMovement(row: OfficialMovementEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertLot(row: InventoryLotEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertLotIfMissing(row: InventoryLotEntity): Long
    @Upsert suspend fun upsertLot(row: InventoryLotEntity)
    @Query("SELECT * FROM inventory_lots WHERE farmId = :farmId AND id = :lotId LIMIT 1")
    suspend fun lot(farmId: String, lotId: String): InventoryLotEntity?
    @Query("SELECT * FROM inventory_lots WHERE farmId = :farmId AND itemId = :itemId AND quantityMilli > 0 ORDER BY expiresEpochDay, id")
    suspend fun lotsFor(farmId: String, itemId: String): List<InventoryLotEntity>
    @Query("UPDATE inventory_lots SET quantityMilli = :quantityMilli WHERE farmId = :farmId AND id = :lotId")
    suspend fun setLotQuantity(farmId: String, lotId: String, quantityMilli: Long)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertVetVisit(row: VetVisitEntity)
    @Upsert suspend fun upsertVetVisit(row: VetVisitEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertLab(row: LabResultEntity)
    @Upsert suspend fun upsertLab(row: LabResultEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertCattleWeaning(row: CattleWeaningEntity)
    @Upsert suspend fun upsertCattleWeaning(row: CattleWeaningEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertMicron(row: SheepMicronEntity)
    @Upsert suspend fun upsertMicron(row: SheepMicronEntity)
    @Upsert suspend fun upsertEnabledKind(row: EnabledPoultryKindEntity)
    @Query("SELECT * FROM enabled_poultry_kinds WHERE farmId = :farmId ORDER BY poultryKindCode")
    suspend fun enabledPoultryKinds(farmId: String): List<EnabledPoultryKindEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertGiStasis(row: RabbitGiStasisEntity)
    @Upsert suspend fun upsertGiStasis(row: RabbitGiStasisEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertPedigree(row: PedigreeRelationEntity)
    @Upsert suspend fun upsertPedigree(row: PedigreeRelationEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertPackSlot(row: HealthPackSlotEntity)
    @Upsert suspend fun upsertPackSlot(row: HealthPackSlotEntity)
    @Query("SELECT * FROM health_schedule_slots WHERE farmId = :farmId AND packId = :packId AND isCore = 1")
    suspend fun coreSlots(farmId: String, packId: String): List<HealthPackSlotEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertPackApply(row: HealthPackApplyEntity)
    @Upsert suspend fun upsertPackApply(row: HealthPackApplyEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertLotPlace(row: CattleLotPlacementEntity)
    @Upsert suspend fun upsertLotPlace(row: CattleLotPlacementEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertDof(row: CattleDofEntity)
    @Upsert suspend fun upsertDof(row: CattleDofEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertLotClose(row: CattleLotCloseEntity)
    @Upsert suspend fun upsertLotClose(row: CattleLotCloseEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertLactation(row: GoatLactationPlanEntity)
    @Upsert suspend fun upsertLactation(row: GoatLactationPlanEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertReorderAlert(row: ReorderAlertEntity)
    @Upsert suspend fun upsertReorderAlert(row: ReorderAlertEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertCensus(row: GroupCensusEntity)
    @Upsert suspend fun upsertCensus(row: GroupCensusEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertKid(row: GoatKidEntity)
    @Upsert suspend fun upsertKid(row: GoatKidEntity)
    @Query("SELECT COUNT(*) FROM goat_kid_records WHERE farmId = :farmId AND kiddingId = :kiddingId")
    suspend fun kidCount(farmId: String, kiddingId: String): Long
}
