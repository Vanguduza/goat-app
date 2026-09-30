package com.farmos.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.AnimalEntity
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.MeasurementEntity
import com.farmos.core.database.MoneyRecordEntity
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
        assertEquals(123, lines.size - 2)
        assertTrue(lines.contains("2024-10-05,expense,purchase,25.50,USD,'=feed"))
    }
}
