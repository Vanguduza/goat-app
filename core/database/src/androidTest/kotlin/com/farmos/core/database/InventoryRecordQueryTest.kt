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

/** Inventory record reads stay inside one farm; open lots exclude spent lots. */
@RunWith(AndroidJUnit4::class)
class InventoryRecordQueryTest {
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
    fun openLotsAreFarmScopedExpiryOrderedAndExcludeSpentLots() = runBlocking {
        val lifecycle = database.lifecycle()
        lifecycle.insertLot(InventoryLotEntity("late", farmA, "i1", "L2", expiresEpochDay = 40, quantityMilli = 500))
        lifecycle.insertLot(InventoryLotEntity("early", farmA, "i1", "L1", expiresEpochDay = 10, quantityMilli = 500))
        lifecycle.insertLot(InventoryLotEntity("spent", farmA, "i1", "L0", expiresEpochDay = 5, quantityMilli = 0))
        lifecycle.insertLot(InventoryLotEntity("other", farmB, "i1", "LX", expiresEpochDay = 1, quantityMilli = 500))

        assertEquals(listOf("early", "late"), lifecycle.openLots(farmA).map { it.id })
        assertEquals(listOf("other"), lifecycle.openLots(farmB).map { it.id })
    }

    @Test
    fun movementsAreFarmScopedNewestFirstAndBounded() = runBlocking {
        val inventory = database.inventory()
        inventory.insertMovement(InventoryMovementEntity("m1", farmA, "i1", "receive", 1_000, occurredAtEpochMillis = 1))
        inventory.insertMovement(InventoryMovementEntity("m2", farmA, "i1", "issue", 200, occurredAtEpochMillis = 2))
        inventory.insertMovement(InventoryMovementEntity("m3", farmB, "i1", "issue", 300, occurredAtEpochMillis = 3))

        assertEquals(listOf("m2", "m1"), inventory.movements(farmA, 10).map { it.id })
        assertEquals(listOf("m2"), inventory.movements(farmA, 1).map { it.id })
        assertEquals(listOf("m3"), inventory.movements(farmB, 10).map { it.id })
    }
}
