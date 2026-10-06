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

/** Vaccinations are farm-scoped; animal and group targets are queryable independently. */
@RunWith(AndroidJUnit4::class)
class VaccinationQueryTest {
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
    fun vaccinationsAreFarmScopedAndTargetAddressable() = runBlocking {
        val vaccinations = database.vaccinations()
        vaccinations.insert(HealthVaccinationEntity("v1", farmA, "goat-1", null, "goat", "form-1", "2ml", "IM", 3_000))
        vaccinations.insert(HealthVaccinationEntity("v2", farmA, "goat-1", null, "goat", "form-1", "2ml", "IM", 1_000))
        vaccinations.insert(HealthVaccinationEntity("v3", farmA, null, "group-1", "goat", "form-1", null, null, 2_000))
        vaccinations.insert(HealthVaccinationEntity("v4", farmB, "goat-1", null, "goat", "form-1", "2ml", "IM", 4_000))

        assertEquals(3, vaccinations.count(farmA))
        assertEquals(1, vaccinations.count(farmB))
        assertEquals(listOf("v2", "v1"), vaccinations.forAnimal(farmA, "goat-1").map { it.id })
        assertEquals(listOf("v3"), vaccinations.forGroup(farmA, "group-1").map { it.id })
        assertEquals(emptyList<String>(), vaccinations.forAnimal(farmB, "goat-9").map { it.id })
        assertEquals(listOf("v3", "v2", "v1"), vaccinations.recent(farmA, 10).map { it.id })
    }
}
