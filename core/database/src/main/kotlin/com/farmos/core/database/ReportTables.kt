package com.farmos.core.database

import androidx.room.Dao
import androidx.room.Query

/** One animal in the herd register, with its latest recorded weight in grams if any. */
data class HerdRegisterRow(
    val id: String,
    val speciesCode: String,
    val tag: String,
    val name: String?,
    val sex: String,
    val status: String,
    val dateOfBirthEpochDay: Long?,
    val poultryKindCode: String?,
    val latestWeightGrams: Long?,
)

/**
 * Exhaustive reads for metrics and exports (owner decision D-026, resolution R10). These queries have no
 * row cap: a metric or an export over a capped list would misstate the farm.
 */
@Dao
interface FarmReportDao {
    @Query(
        """
        SELECT a.id, a.speciesCode, a.tag, a.name, a.sex, a.status, a.dateOfBirthEpochDay, a.poultryKindCode,
            (SELECT m.valueLong FROM measurements m
                WHERE m.farmId = a.farmId AND m.animalId = a.id AND m.type = 'weight'
                ORDER BY m.measuredAtEpochMillis DESC, m.id DESC LIMIT 1) AS latestWeightGrams
        FROM animals a
        WHERE a.farmId = :farmId
        ORDER BY a.speciesCode, a.tag, a.id
        """,
    )
    suspend fun herdRegister(farmId: String): List<HerdRegisterRow>

    /** Income and expense totals per currency over every money record; amounts are never converted. */
    @Query(
        """
        SELECT kind, currency, SUM(amountMinor) AS amountMinor, COUNT(*) AS records
        FROM money_records WHERE farmId = :farmId
        GROUP BY kind, currency ORDER BY currency, kind
        """,
    )
    suspend fun moneyTotals(farmId: String): List<MoneyTotalRow>

    /** Kiddings, lambings, calvings and kindlings over every recorded birth event (D-026). */
    @Query(
        """
        SELECT 'goat' AS speciesCode, COUNT(*) AS events, IFNULL(SUM(liveCount), 0) AS live, IFNULL(SUM(deadCount), 0) AS dead FROM kidding_events WHERE farmId = :farmId
        UNION ALL
        SELECT 'sheep', COUNT(*), IFNULL(SUM(liveCount), 0), IFNULL(SUM(deadCount), 0) FROM sheep_lambings WHERE farmId = :farmId
        UNION ALL
        SELECT 'cattle', COUNT(*), IFNULL(SUM(liveCount), 0), IFNULL(SUM(deadCount), 0) FROM cattle_calvings WHERE farmId = :farmId
        UNION ALL
        SELECT 'rabbit', COUNT(*), IFNULL(SUM(liveCount), 0), IFNULL(SUM(deadCount), 0) FROM rabbit_kindlings WHERE farmId = :farmId
        """,
    )
    suspend fun birthTotals(farmId: String): List<BirthTotalRow>

    /** Observations and treatments over every health record, and withdrawals running through [todayEpochDay]. */
    @Query(
        """
        SELECT (SELECT COUNT(*) FROM health_observations WHERE farmId = :farmId) AS observations,
            (SELECT COUNT(*) FROM health_treatments WHERE farmId = :farmId) AS treatments,
            (SELECT COUNT(*) FROM withdrawal_windows WHERE farmId = :farmId AND endsEpochDay >= :todayEpochDay) AS activeWithdrawals
        """,
    )
    suspend fun healthTotals(farmId: String, todayEpochDay: Long): HealthTotalRow

    /** Goat and cattle milk, sheep wool and poultry eggs over every production record (D-026). */
    @Query(
        """
        SELECT 'goat-milk' AS product, COUNT(*) AS records, IFNULL(SUM(litresMilli), 0) AS amount FROM goat_milk_records WHERE farmId = :farmId
        UNION ALL
        SELECT 'cattle-milk', COUNT(*), IFNULL(SUM(litresMilli), 0) FROM cattle_milk_records WHERE farmId = :farmId
        UNION ALL
        SELECT 'sheep-wool', COUNT(*), IFNULL(SUM(greasyGrams), 0) FROM sheep_wool_clips WHERE farmId = :farmId
        UNION ALL
        SELECT 'poultry-eggs', COUNT(*), IFNULL(SUM(eggs), 0) FROM poultry_flock_days WHERE farmId = :farmId
        """,
    )
    suspend fun productionTotals(farmId: String): List<ProductionTotalRow>

    /** Every stock item, and those at or below the reorder level set for them. */
    @Query(
        """
        SELECT COUNT(*) AS items, IFNULL(SUM(CASE WHEN reorderMilli > 0 AND quantityMilli <= reorderMilli THEN 1 ELSE 0 END), 0) AS atOrBelowReorder
        FROM inventory_items WHERE farmId = :farmId
        """,
    )
    suspend fun inventoryTotals(farmId: String): InventoryTotalRow

    /** Every money record on the farm, oldest first, for export. */
    @Query("SELECT * FROM money_records WHERE farmId = :farmId ORDER BY occurredEpochDay, id")
    suspend fun moneyRecords(farmId: String): List<MoneyRecordEntity>
}

/** Births recorded for one species over every birth event: events, and young born alive and dead. */
data class BirthTotalRow(val speciesCode: String, val events: Int, val live: Long, val dead: Long)

/** Health records over the whole farm, with the withdrawal windows still running on the given day. */
data class HealthTotalRow(val observations: Int, val treatments: Int, val activeWithdrawals: Int)

/** One product over every record: milk in millilitres, wool in grams, eggs in eggs. */
data class ProductionTotalRow(val product: String, val records: Int, val amount: Long)

/** Stock items tracked, and those at or below a reorder level set for them. */
data class InventoryTotalRow(val items: Int, val atOrBelowReorder: Int)

/** Money of one kind (income or expense) in one currency, over every record. */
data class MoneyTotalRow(val kind: String, val currency: String, val amountMinor: Long, val records: Int)
