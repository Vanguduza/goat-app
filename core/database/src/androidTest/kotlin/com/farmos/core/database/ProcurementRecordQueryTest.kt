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

/** Procurement aggregates are exhaustive per supplier and currency, farm-scoped, and never mix currencies. */
@RunWith(AndroidJUnit4::class)
class ProcurementRecordQueryTest {
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
    fun supplierTotalsCoverEveryPurchaseBeyondTheListBoundPerCurrency() = runBlocking {
        val lifecycle = database.lifecycle()
        repeat(520) { lifecycle.insertPurchase(PurchaseEntity("a$it", farmA, "s1", "i1", 1_000, 100, "USD", 10L + it)) }
        lifecycle.insertPurchase(PurchaseEntity("a-zar", farmA, "s1", "i1", 1_000, 7, "ZAR", 3))
        lifecycle.insertPurchase(PurchaseEntity("a-s2", farmA, "s2", "i1", 1_000, 50, "USD", 900))
        lifecycle.insertPurchase(PurchaseEntity("b1", farmB, "s1", "i1", 1_000, 99_999, "USD", 1))

        assertEquals(500, lifecycle.purchases(farmA, 500).size)
        assertEquals(522, lifecycle.purchaseCount(farmA))
        assertEquals(1, lifecycle.purchaseCount(farmB))
        assertEquals(
            listOf(
                SupplierCurrencyTotal("s1", "USD", 52_000, 520, 529),
                SupplierCurrencyTotal("s1", "ZAR", 7, 1, 3),
                SupplierCurrencyTotal("s2", "USD", 50, 1, 900),
            ),
            lifecycle.purchaseTotalsBySupplier(farmA),
        )
        assertEquals(listOf(SupplierCurrencyTotal("s1", "USD", 99_999, 1, 1)), lifecycle.purchaseTotalsBySupplier(farmB))
    }

    @Test
    fun sameDayPurchasesOrderNewestFirstThenById() = runBlocking {
        val lifecycle = database.lifecycle()
        lifecycle.insertPurchase(PurchaseEntity("p-c", farmA, "s1", "i1", 1_000, 1, "USD", 5))
        lifecycle.insertPurchase(PurchaseEntity("p-a", farmA, "s1", "i1", 1_000, 1, "USD", 5))
        lifecycle.insertPurchase(PurchaseEntity("p-b", farmA, "s1", "i1", 1_000, 1, "USD", 6))

        assertEquals(listOf("p-b", "p-a", "p-c"), lifecycle.purchases(farmA, 10).map { it.id })
    }
}
