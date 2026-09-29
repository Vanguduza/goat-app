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

/** Per-flock poultry reads are exhaustive, farm- and flock-scoped, newest first with id tie-breaks. */
@RunWith(AndroidJUnit4::class)
class PoultryFlockQueryTest {
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
    fun flockReadsStayInsideOneFarmAndFlock() = runBlocking {
        val lifecycle = database.lifecycle()
        repeat(520) { lifecycle.insertPlacement(PoultryPlacementEntity("a$it", farmA, "g1", "h1", "chicken", 10, 10L + it)) }
        lifecycle.insertPlacement(PoultryPlacementEntity("a-g2", farmA, "g2", "h1", "duck", 5, 5))
        lifecycle.insertPlacement(PoultryPlacementEntity("b1", farmB, "g1", "h9", "chicken", 99, 5))
        lifecycle.insertVaccination(PoultryVaccinationEntity("v-b", farmA, "g1", "chicken", "f1", 7))
        lifecycle.insertVaccination(PoultryVaccinationEntity("v-a", farmA, "g1", "chicken", "f1", 7))
        lifecycle.insertVaccination(PoultryVaccinationEntity("v-farmB", farmB, "g1", "chicken", "f1", 7))
        lifecycle.insertBiosecurity(PoultryBiosecurityEntity("w1", farmA, "h1", "g1", "ok", false, 3))
        lifecycle.insertBiosecurity(PoultryBiosecurityEntity("w-house", farmA, "h1", null, "ok", false, 4))
        lifecycle.insertHatch(PoultryHatchEntity("h-in", farmA, "chicken", null, null, 100, 21, 2, "hatched", null, null, null, 88, 2, "g1"))
        lifecycle.insertHatch(PoultryHatchEntity("h-other", farmA, "chicken", null, null, 100, 21, 2, "set", null, null, null, null, null, null))
        val days = database.poultryFlockDays()
        days.insert(PoultryFlockDayEntity("d-b", farmA, "g1", 1, 0, 0, 0, occurredEpochDay = 9))
        days.insert(PoultryFlockDayEntity("d-a", farmA, "g1", 1, 0, 0, 0, occurredEpochDay = 9))

        assertEquals(520, lifecycle.placementsForGroup(farmA, "g1").size)
        assertEquals("a519", lifecycle.placementsForGroup(farmA, "g1").first().id)
        assertEquals(listOf("b1"), lifecycle.placementsForGroup(farmB, "g1").map { it.id })
        assertEquals(listOf("v-a", "v-b"), lifecycle.vaccinationsForGroup(farmA, "g1").map { it.id })
        assertEquals(listOf("w1"), lifecycle.walksForGroup(farmA, "g1").map { it.id })
        assertEquals(listOf("h-in"), lifecycle.hatchesPlacedInto(farmA, "g1").map { it.id })
        assertEquals(emptyList<String>(), lifecycle.hatchesPlacedInto(farmB, "g1").map { it.id })
        assertEquals(listOf("d-a", "d-b"), days.forGroup(farmA, "g1").map { it.id })
    }
}
