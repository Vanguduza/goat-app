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

/** Vet visit and lab result reads stay inside one farm, newest first and bounded. */
@RunWith(AndroidJUnit4::class)
class HealthRecordQueryTest {
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
    fun vetVisitsAndLabResultsAreFarmScopedNewestFirst() = runBlocking {
        val lifecycle = database.lifecycle()
        lifecycle.insertVetVisit(VetVisitEntity("v1", farmA, "goat", null, null, "check", "Dr A", occurredEpochDay = 10))
        lifecycle.insertVetVisit(VetVisitEntity("v2", farmA, "goat", "nala", null, "lame", "Dr A", occurredEpochDay = 20))
        lifecycle.insertVetVisit(VetVisitEntity("v3", farmB, "goat", null, null, "check", "Dr B", occurredEpochDay = 30))
        lifecycle.insertLab(LabResultEntity("l1", farmA, "nala", null, "CAE", "neg", null, occurredEpochDay = 5))
        lifecycle.insertLab(LabResultEntity("l2", farmB, "nala", null, "CAE", "pos", null, occurredEpochDay = 6))

        assertEquals(listOf("v2", "v1"), lifecycle.vetVisits(farmA, 10).map { it.id })
        assertEquals(listOf("v2"), lifecycle.vetVisits(farmA, 1).map { it.id })
        assertEquals(listOf("l1"), lifecycle.labResults(farmA, 10).map { it.id })
        assertEquals(listOf("l2"), lifecycle.labResults(farmB, 10).map { it.id })
    }
}
