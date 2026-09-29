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

/** Sheep record reads stay inside one farm; per-animal reads stay inside one animal. */
@RunWith(AndroidJUnit4::class)
class SheepRecordQueryTest {
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
    fun perAnimalAndFarmWoolReadsAreScoped() = runBlocking {
        val lifecycle = database.lifecycle()
        lifecycle.insertLambing(SheepLambingEntity("l1", farmA, "ewe", 2, 2, 0, occurredEpochDay = 10))
        lifecycle.insertLambing(SheepLambingEntity("l2", farmB, "ewe", 1, 1, 0, occurredEpochDay = 11))
        lifecycle.insertDag(SheepDagEntity("d1", farmA, "ewe", 2, occurredEpochDay = 5))
        lifecycle.insertDag(SheepDagEntity("d2", farmA, "ram", 4, occurredEpochDay = 6))
        lifecycle.insertWool(SheepWoolEntity("w1", farmA, "ewe", null, 4_200, occurredEpochDay = 20))
        lifecycle.insertWool(SheepWoolEntity("w2", farmA, null, "mob", 3_100, occurredEpochDay = 21))
        lifecycle.insertWool(SheepWoolEntity("w3", farmB, "ewe", null, 9_999, occurredEpochDay = 22))

        assertEquals(listOf("l1"), lifecycle.sheepLambingsFor(farmA, "ewe").map { it.id })
        assertEquals(listOf("d1"), lifecycle.sheepDagFor(farmA, "ewe").map { it.id })
        assertEquals(listOf("w1"), lifecycle.sheepWoolFor(farmA, "ewe").map { it.id })
        assertEquals(listOf("w2", "w1"), lifecycle.sheepWoolClips(farmA, 10).map { it.id })
        assertEquals(listOf("w3"), lifecycle.sheepWoolClips(farmB, 10).map { it.id })
    }
}
