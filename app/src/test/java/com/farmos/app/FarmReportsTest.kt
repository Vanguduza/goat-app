package com.farmos.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.AnimalEntity
import com.farmos.core.database.CattleMilkEntity
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.GoatMilkEntity
import com.farmos.core.database.InventoryItemEntity
import com.farmos.core.database.MeasurementEntity
import com.farmos.core.database.KiddingEntity
import com.farmos.core.database.MoneyRecordEntity
import com.farmos.core.database.SheepLambingEntity
import com.farmos.core.database.WithdrawalWindowEntity
import com.farmos.domain.access.Permission
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Owner decision D-026 (R10, R3): metrics and exports read every record, and a missing value is never zero. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-rUS")
class FarmReportsTest {
    private val farm = "ffffffff-ffff-4fff-8fff-ffffffffffff"
    private val otherFarm = "abababab-abab-4bab-8bab-abababababab"
    private val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FarmOsDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    @After
    fun tearDown() = database.close()

    private suspend fun animal(id: String, species: String, status: String = "active", farmId: String = farm, name: String? = null) =
        database.animals().insert(AnimalEntity(id = id, farmId = farmId, tag = "T-$id", name = name, speciesCode = species, sex = "FEMALE", status = status, dateOfBirthEpochDay = 20_000L, updatedAtEpochMillis = 1L))

    private suspend fun weight(id: String, animalId: String, grams: Long, at: Long) =
        database.measurements().insert(MeasurementEntity(id, farm, animalId, "weight", grams, "g", at))

    @Test
    fun metricsCoverEveryRecordAndCountMissingWeightsAsMissing(): Unit = runBlocking {
        // More goats than any capped list shows, so a capped source would under-count.
        (1..260).forEach { animal("g$it", "goat") }
        animal("g-sold", "goat", status = "sold")
        animal("s1", "sheep")
        animal("s2", "sheep")
        animal("x1", "goat", farmId = otherFarm)
        weight("w1", "s1", 40_000, 1)
        weight("w2", "s1", 42_500, 2)

        val rows = database.reports().herdRegister(farm)
        assertEquals(263, rows.size)
        assertTrue(rows.none { it.id == "x1" })
        assertEquals(42_500L, rows.first { it.id == "s1" }.latestWeightGrams)

        val metrics = herdMetrics(rows).associateBy { it.definition.id }
        assertEquals(260L, metrics.getValue("active-goat").value)
        assertTrue(metrics.getValue("active-goat").complete)
        val sheepWeight = metrics.getValue("live-weight-sheep")
        assertEquals(42_500L, sheepWeight.value)
        assertEquals(1, sheepWeight.missing)
        assertEquals("Partial: 1 of 2 record(s) have no value", sheepWeight.completeness)
        assertEquals("42.5 kg", metricValueText(sheepWeight))
        assertEquals(1L, metrics.getValue("exited").value)
        assertEquals(listOf("active-goat", "live-weight-goat", "active-sheep", "live-weight-sheep", "exited"), herdMetrics(rows).map { it.definition.id })
    }

    @Test
    fun onlyManagementExportsFarmRecords() {
        assertTrue(rolePermits("OWNER", Permission.EXPORT_FARM_DATA))
        assertTrue(rolePermits("MANAGER", Permission.EXPORT_FARM_DATA))
        listOf("SUPERVISOR", "WORKER", "VIEWER", "buyer").forEach { assertFalse(rolePermits(it, Permission.EXPORT_FARM_DATA)) }
    }

    @Test
    fun theHerdRegisterCsvHasEveryAnimalAndNeutralisesFormulas(): Unit = runBlocking {
        animal("a", "goat", name = "=cmd")
        animal("b", "sheep", name = "Dora, the ewe")
        weight("w", "b", 51_250, 1)
        val lines = herdRegisterCsv(database.reports().herdRegister(farm)).split("\r\n")
        assertEquals("Species,Tag,Name,Sex,Status,Born,Latest weight (kg),Poultry kind", lines[0])
        assertEquals("goat,T-a,'=cmd,female,active,2024-10-04,,", lines[1])
        assertEquals("sheep,T-b,\"Dora, the ewe\",female,active,2024-10-04,51.250,", lines[2])
        assertEquals("", lines[3])
    }

    @Test
    fun moneyIsTotalledPerCurrencyOverEveryRecordAndExported(): Unit = runBlocking {
        // More records than any capped list shows, in two currencies that are never added together.
        (1..120).forEach { database.money().insert(MoneyRecordEntity("i$it", farm, "income", "sales", 1_000, "USD", 20_000L + it, "Sale")) }
        database.money().insert(MoneyRecordEntity("e1", farm, "expense", "purchase", 2_550, "USD", 20_001, "=feed"))
        database.money().insert(MoneyRecordEntity("z1", farm, "income", "sales", 5_000, "ZAR", 20_002, null))
        database.money().insert(MoneyRecordEntity("x1", otherFarm, "income", "sales", 9_999, "USD", 20_002, null))

        val metrics = moneyMetrics(database.reports().moneyTotals(farm)).associateBy { it.definition.id }
        assertEquals(setOf("money-expense-USD", "money-income-USD", "money-income-ZAR"), metrics.keys)
        assertEquals("1200.00 USD", metricValueText(metrics.getValue("money-income-USD")))
        assertEquals(120, metrics.getValue("money-income-USD").included)
        assertEquals("25.50 USD", metricValueText(metrics.getValue("money-expense-USD")))
        assertEquals("50.00 ZAR", metricValueText(metrics.getValue("money-income-ZAR")))

        val lines = moneyRecordsCsv(database.reports().moneyRecords(farm)).split("\r\n")
        assertEquals("Date,Kind,Category,Amount,Currency,Note", lines[0])
        // 120 sales, one expense and one ZAR sale on this farm; the other farm's record is not exported.
        assertEquals(122, lines.size - 2)
        assertTrue(lines.contains("2024-10-05,expense,purchase,25.50,USD,'=feed"))
    }

    @Test
    fun birthsAndHealthAreCountedOverEveryRecord(): Unit = runBlocking {
        database.kidding().insert(KiddingEntity("k1", farm, "doe-1", 3, 2, 1, 20_100))
        database.kidding().insert(KiddingEntity("k2", farm, "doe-2", 2, 2, 0, 20_110))
        database.kidding().insert(KiddingEntity("k3", otherFarm, "doe-9", 4, 4, 0, 20_110))
        database.lifecycle().insertLambing(SheepLambingEntity("l1", farm, "ewe-1", 1, 1, 0, 20_120))
        database.lifecycle().insertWithdrawal(WithdrawalWindowEntity("w1", farm, "t1", "Oxytetracycline", "meat", 20_500))
        database.lifecycle().insertWithdrawal(WithdrawalWindowEntity("w2", farm, "t2", "Ivermectin", "meat", 20_000))

        val births = birthMetrics(database.reports().birthTotals(farm)).associateBy { it.definition.id }
        // Cattle and rabbits recorded no births and are left out rather than shown as zero.
        assertEquals(setOf("births-goat", "born-alive-goat", "born-dead-goat", "births-sheep", "born-alive-sheep", "born-dead-sheep"), births.keys)
        assertEquals(2L, births.getValue("births-goat").value)
        assertEquals(4L, births.getValue("born-alive-goat").value)
        assertEquals(1L, births.getValue("born-dead-goat").value)
        assertEquals("Kiddings recorded", births.getValue("births-goat").definition.name)

        val health = healthMetrics(database.reports().healthTotals(farm, 20_300)).associateBy { it.definition.id }
        assertEquals(1L, health.getValue("health-withdrawals").value)
        assertEquals(0L, health.getValue("health-treatments").value)
    }

    @Test
    fun productionAndStockAreTotalledOverEveryRecord(): Unit = runBlocking {
        database.lifecycle().insertMilk(GoatMilkEntity("m1", farm, "doe-1", 2_000, 20_100))
        database.lifecycle().insertMilk(GoatMilkEntity("m2", farm, "doe-2", 1_500, 20_101))
        database.lifecycle().insertMilk(GoatMilkEntity("m3", otherFarm, "doe-9", 9_000, 20_101))
        database.lifecycle().insertCattleMilk(CattleMilkEntity("c1", otherFarm, "cow-9", 9_000, 20_101))
        database.inventory().insertItem(InventoryItemEntity("i1", farm, "FEED", "Feed", "kg", 5_000, 1, reorderMilli = 10_000))
        database.inventory().insertItem(InventoryItemEntity("i2", farm, "SALT", "Salt", "kg", 50_000, 1, reorderMilli = 10_000))
        database.inventory().insertItem(InventoryItemEntity("i3", farm, "WIRE", "Wire", "m", 0, 1))

        val production = productionMetrics(database.reports().productionTotals(farm)).associateBy { it.definition.id }
        // Cattle milk, wool and eggs have nothing recorded on this farm and are left out rather than shown as zero.
        assertEquals(setOf("production-goat-milk"), production.keys)
        assertEquals(3_500L, production.getValue("production-goat-milk").value)
        assertEquals("3.5 L", metricValueText(production.getValue("production-goat-milk")))

        val stock = inventoryMetrics(database.reports().inventoryTotals(farm)).associateBy { it.definition.id }
        assertEquals(3L, stock.getValue("inventory-items").value)
        // Only an item with a reorder level set can be at or below it.
        assertEquals(1L, stock.getValue("inventory-reorder").value)
        assertTrue(inventoryMetrics(database.reports().inventoryTotals("cdcdcdcd-cdcd-4dcd-8dcd-cdcdcdcdcdcd")).isEmpty())
    }
}
