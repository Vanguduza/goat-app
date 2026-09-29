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

/** Herd counts cover every active animal of the species past the 500-row herd list, farm-scoped. */
@RunWith(AndroidJUnit4::class)
class HerdCountQueryTest {
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
    fun herdCountsAreExhaustiveAndFarmScoped() = runBlocking {
        val animals = database.animals()
        val today = 20_000L
        repeat(520) {
            animals.insert(AnimalEntity("f$it", farmA, "F-$it", null, "goat", "FEMALE", "active", if (it < 30) today - 100 else today - 900, updatedAtEpochMillis = 1))
        }
        repeat(40) { animals.insert(AnimalEntity("m$it", farmA, "M-$it", null, "goat", "MALE", "active", null, updatedAtEpochMillis = 1)) }
        animals.insert(AnimalEntity("born-365", farmA, "K-365", null, "goat", "FEMALE", "active", today - 365, updatedAtEpochMillis = 1))
        animals.insert(AnimalEntity("sold", farmA, "S-1", null, "goat", "FEMALE", "sold", today - 10, updatedAtEpochMillis = 1))
        animals.insert(AnimalEntity("closed", farmA, "C-1", null, "goat", "FEMALE", "closed", null, updatedAtEpochMillis = 1))
        animals.insert(AnimalEntity("sheep", farmA, "E-1", null, "sheep", "FEMALE", "active", today - 10, updatedAtEpochMillis = 1))
        animals.insert(AnimalEntity("b1", farmB, "F-1", null, "goat", "FEMALE", "active", today - 10, updatedAtEpochMillis = 1))

        assertEquals(HerdCountRow(active = 561, females = 521, males = 40, young = 30), animals.herdCounts(farmA, "goat", "active", today))
        assertEquals(HerdCountRow(active = 1, females = 1, males = 0, young = 1), animals.herdCounts(farmB, "goat", "active", today))
        assertEquals(HerdCountRow(active = 0, females = 0, males = 0, young = 0), animals.herdCounts("33333333-3333-4333-8333-333333333333", "goat", "active", today))
        assertEquals(500, animals.listBySpecies(farmA, "goat", 500).size)
        assertEquals(562, animals.countBySpecies(farmA, "goat"))
    }
}
