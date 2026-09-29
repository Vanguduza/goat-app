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

/** Per-animal vet visit and lab result reads stay inside one farm and one animal, newest first. */
@RunWith(AndroidJUnit4::class)
class AnimalVetLabQueryTest {
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
    fun vetAndLabReadsAreAnimalAndFarmScoped() = runBlocking {
        val lifecycle = database.lifecycle()
        lifecycle.insertVetVisit(VetVisitEntity("v-b", farmA, "goat", "g1", null, "check", "Dr Moyo", 5))
        lifecycle.insertVetVisit(VetVisitEntity("v-a", farmA, "goat", "g1", null, "check", "Dr Moyo", 5))
        lifecycle.insertVetVisit(VetVisitEntity("v-other", farmA, "goat", "g2", null, "check", "Dr Moyo", 6))
        lifecycle.insertVetVisit(VetVisitEntity("v-farmB", farmB, "goat", "g1", null, "check", "Dr Moyo", 6))
        lifecycle.insertLab(LabResultEntity("l1", farmA, "g1", null, "CAE", "Negative", null, 9))
        lifecycle.insertLab(LabResultEntity("l-farmB", farmB, "g1", null, "CAE", "Positive", null, 9))

        assertEquals(listOf("v-a", "v-b"), lifecycle.vetVisitsForAnimal(farmA, "g1").map { it.id })
        assertEquals(listOf("v-farmB"), lifecycle.vetVisitsForAnimal(farmB, "g1").map { it.id })
        assertEquals(listOf("l1"), lifecycle.labResultsForAnimal(farmA, "g1").map { it.id })
        assertEquals(listOf("l-farmB"), lifecycle.labResultsForAnimal(farmB, "g1").map { it.id })
    }
}
