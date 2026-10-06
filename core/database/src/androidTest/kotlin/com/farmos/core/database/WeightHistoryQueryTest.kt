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

/** Weight history is farm- and animal-scoped, oldest first, with a deterministic same-instant order. */
@RunWith(AndroidJUnit4::class)
class WeightHistoryQueryTest {
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
    fun historyIsScopedAndDeterministic() = runBlocking {
        val animals = database.animals()
        animals.insert(AnimalEntity("cow-a", farmA, "C-1", null, "cattle", "FEMALE", "active", null, updatedAtEpochMillis = 1))
        animals.insert(AnimalEntity("cow-b", farmA, "C-2", null, "cattle", "FEMALE", "active", null, updatedAtEpochMillis = 1))
        animals.insert(AnimalEntity("cow-x", farmB, "C-1", null, "cattle", "FEMALE", "active", null, updatedAtEpochMillis = 1))
        val measurements = database.measurements()
        measurements.insert(MeasurementEntity("m-c", farmA, "cow-a", "weight", 400_000, "g", 2_000))
        measurements.insert(MeasurementEntity("m-b", farmA, "cow-a", "weight", 390_000, "g", 1_000))
        measurements.insert(MeasurementEntity("m-a", farmA, "cow-a", "weight", 391_000, "g", 1_000))
        measurements.insert(MeasurementEntity("m-other", farmA, "cow-b", "weight", 500_000, "g", 1_000))
        measurements.insert(MeasurementEntity("m-farmB", farmB, "cow-x", "weight", 600_000, "g", 1_000))

        assertEquals(listOf("m-a", "m-b", "m-c"), measurements.history(farmA, "cow-a", "weight").map { it.id })
        assertEquals(emptyList<String>(), measurements.history(farmB, "cow-a", "weight").map { it.id })
        assertEquals(listOf("m-farmB"), measurements.history(farmB, "cow-x", "weight").map { it.id })
    }
}
