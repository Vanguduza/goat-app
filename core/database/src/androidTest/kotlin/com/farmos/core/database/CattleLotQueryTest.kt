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

/** Feedlot reads are farm-scoped and newest first with a deterministic id tie-break. */
@RunWith(AndroidJUnit4::class)
class CattleLotQueryTest {
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
    fun feedlotReadsAreFarmScopedAndOrdered() = runBlocking {
        val lifecycle = database.lifecycle()
        lifecycle.insertLotPlace(CattleLotPlacementEntity("p-b", farmA, "g1", 20, 10))
        lifecycle.insertLotPlace(CattleLotPlacementEntity("p-a", farmA, "g1", 40, 10))
        lifecycle.insertLotPlace(CattleLotPlacementEntity("p-farmB", farmB, "g1", 99, 10))
        lifecycle.insertDof(CattleDofEntity("d1", farmA, "g1", 45, 55))
        lifecycle.insertDof(CattleDofEntity("d-farmB", farmB, "g1", 10, 20))
        lifecycle.insertLotClose(CattleLotCloseEntity("c1", farmA, "g2", 29, 13_050_500, null, 90))

        assertEquals(listOf("p-a", "p-b"), lifecycle.cattleLotPlacements(farmA).map { it.id })
        assertEquals(listOf("p-farmB"), lifecycle.cattleLotPlacements(farmB).map { it.id })
        assertEquals(listOf("d1"), lifecycle.cattleDaysOnFeed(farmA).map { it.id })
        assertEquals(listOf("c1"), lifecycle.cattleLotCloseouts(farmA).map { it.id })
        assertEquals(emptyList<String>(), lifecycle.cattleLotCloseouts(farmB).map { it.id })
    }
}
