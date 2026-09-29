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

/** Per-animal cattle record reads stay inside one farm and one animal, newest first. */
@RunWith(AndroidJUnit4::class)
class CattleRecordQueryTest {
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
    fun milkSccAndCalvingsAreAnimalAndFarmScoped() = runBlocking {
        val lifecycle = database.lifecycle()
        lifecycle.insertCattleMilk(CattleMilkEntity("m1", farmA, "daisy", 20_000, occurredEpochDay = 10))
        lifecycle.insertCattleMilk(CattleMilkEntity("m2", farmA, "daisy", 21_000, occurredEpochDay = 11))
        lifecycle.insertCattleMilk(CattleMilkEntity("m3", farmA, "bella", 22_000, occurredEpochDay = 12))
        lifecycle.insertCattleMilk(CattleMilkEntity("m4", farmB, "daisy", 23_000, occurredEpochDay = 13))
        lifecycle.insertScc(CattleSccEntity("s1", farmA, "daisy", 150_000, 30, occurredEpochDay = 11))
        lifecycle.insertScc(CattleSccEntity("s2", farmB, "daisy", 900_000, 30, occurredEpochDay = 12))
        lifecycle.insertCalving(CattleCalvingEntity("c1", farmA, "daisy", 1, 1, 0, occurredEpochDay = 5))
        lifecycle.insertCalving(CattleCalvingEntity("c2", farmB, "daisy", 1, 1, 0, occurredEpochDay = 6))

        assertEquals(listOf("m2", "m1"), lifecycle.cattleMilkFor(farmA, "daisy").map { it.id })
        assertEquals(listOf("s1"), lifecycle.cattleSccFor(farmA, "daisy").map { it.id })
        assertEquals(listOf("c1"), lifecycle.cattleCalvingsFor(farmA, "daisy").map { it.id })
        assertEquals(listOf("m4"), lifecycle.cattleMilkFor(farmB, "daisy").map { it.id })
    }
}
