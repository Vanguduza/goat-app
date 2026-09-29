package com.farmos.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Sales totals are exhaustive per currency and farm-scoped; the bounded list orders deterministically. */
@RunWith(AndroidJUnit4::class)
class SalesRecordQueryTest {
    private lateinit var database: FarmOsDatabase
    private val farmA = "11111111-1111-4111-8111-111111111111"
    private val farmB = "22222222-2222-4222-8222-222222222222"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, FarmOsDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun totalsCoverEverySaleBeyondTheListBoundPerCurrency() = runBlocking {
        val sales = database.sales()
        repeat(520) { sales.insert(SaleRecordEntity("a$it", farmA, "milk", 1_000, 100, "USD", 10L + it)) }
        sales.insert(SaleRecordEntity("a-kes", farmA, "live_goat", 1_000, 4_000, "KES", 3))
        sales.insert(SaleRecordEntity("b1", farmB, "milk", 1_000, 99_999, "USD", 1))

        assertEquals(500, sales.recent(farmA, 500).size)
        assertEquals(521, sales.count(farmA))
        assertEquals(1, sales.count(farmB))
        assertEquals(listOf(SaleCurrencyTotal("KES", 4_000, 1), SaleCurrencyTotal("USD", 52_000, 520)), sales.totalsByCurrency(farmA))
        assertEquals(listOf(SaleCurrencyTotal("USD", 99_999, 1)), sales.totalsByCurrency(farmB))
    }

    @Test
    fun sameDaySalesOrderNewestFirstThenById() = runBlocking {
        val sales = database.sales()
        sales.insert(SaleRecordEntity("s-c", farmA, "milk", 1_000, 1, "USD", 5))
        sales.insert(SaleRecordEntity("s-a", farmA, "milk", 1_000, 1, "USD", 5))
        sales.insert(SaleRecordEntity("s-b", farmA, "milk", 1_000, 1, "USD", 6))

        assertEquals(listOf("s-b", "s-a", "s-c"), sales.recent(farmA, 10).map { it.id })
    }
}
