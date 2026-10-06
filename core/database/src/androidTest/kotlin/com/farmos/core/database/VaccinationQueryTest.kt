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

/** General health vaccination reads stay inside one farm and preserve animal/group targeting. */
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
        database.vaccinations().insert(
            HealthVaccinationEntity("v-animal", farmA, "goat-1", null, "goat", "form-1", "1 ml", "SC", 30),
        )
        database.vaccinations().insert(
            HealthVaccinationEntity("v-group", farmA, null, "group-1", "goat", "form-1", null, null, 20),
        )
        database.vaccinations().insert(
            HealthVaccinationEntity("v-other", farmB, "goat-1", null, "goat", "form-1", null, null, 40),
        )

        assertEquals(listOf("v-animal", "v-group"), database.vaccinations().recent(farmA, 10).map { it.id })
        assertEquals(listOf("v-animal"), database.vaccinations().forAnimal(farmA, "goat-1").map { it.id })
        assertEquals(listOf("v-group"), database.vaccinations().forGroup(farmA, "group-1").map { it.id })
        assertEquals(listOf("v-other"), database.vaccinations().recent(farmB, 10).map { it.id })
    }
}
