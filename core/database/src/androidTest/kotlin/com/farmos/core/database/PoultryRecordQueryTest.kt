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

/** Poultry flock-day and biosecurity reads stay inside one farm (and one flock where scoped). */
@RunWith(AndroidJUnit4::class)
class PoultryRecordQueryTest {
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
    fun flockDaysAndBiosecurityWalksAreScoped() = runBlocking {
        val days = database.poultryFlockDays()
        days.insert(PoultryFlockDayEntity("d1", farmA, "g1", 900, 1, 0, 10_000, occurredEpochDay = 10))
        days.insert(PoultryFlockDayEntity("d2", farmA, "g1", 910, 0, 1, 10_000, occurredEpochDay = 11))
        days.insert(PoultryFlockDayEntity("d3", farmA, "g2", 50, 0, 0, 1_000, occurredEpochDay = 11))
        days.insert(PoultryFlockDayEntity("d4", farmB, "g1", 999, 0, 0, 1_000, occurredEpochDay = 12))
        val lifecycle = database.lifecycle()
        lifecycle.insertBiosecurity(PoultryBiosecurityEntity("w1", farmA, "h1", null, "ok", false, occurredEpochDay = 5))
        lifecycle.insertBiosecurity(PoultryBiosecurityEntity("w2", farmA, null, "g1", "rats", true, occurredEpochDay = 6))
        lifecycle.insertBiosecurity(PoultryBiosecurityEntity("w3", farmB, "h1", null, "ok", false, occurredEpochDay = 7))

        assertEquals(listOf("d2", "d1"), days.forGroup(farmA, "g1").map { it.id })
        assertEquals(listOf("d4"), days.forGroup(farmB, "g1").map { it.id })
        assertEquals(listOf("w2", "w1"), lifecycle.biosecurityWalks(farmA, 10).map { it.id })
        assertEquals(listOf("w3"), lifecycle.biosecurityWalks(farmB, 10).map { it.id })
    }
}
