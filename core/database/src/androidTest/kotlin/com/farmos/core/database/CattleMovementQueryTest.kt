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

/** Movement and identifier records for one animal are farm-scoped, complete and deterministically ordered. */
@RunWith(AndroidJUnit4::class)
class CattleMovementQueryTest {
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
    fun movementsForOneAnimalAreFarmScopedNewestFirstThenById() = runBlocking {
        val lifecycle = database.lifecycle()
        lifecycle.insertMovement(OfficialMovementEntity("mv-b", farmA, "cow", "cattle", "off", "Home", null, 20))
        lifecycle.insertMovement(OfficialMovementEntity("mv-a", farmA, "cow", "cattle", "transfer", "Home", "Lease", 20))
        lifecycle.insertMovement(OfficialMovementEntity("mv-old", farmA, "cow", "cattle", "on", null, "Home", 3))
        lifecycle.insertMovement(OfficialMovementEntity("mv-other", farmA, "heifer", "cattle", "on", null, "Home", 25))
        lifecycle.insertMovement(OfficialMovementEntity("mv-farm-b", farmB, "cow", "cattle", "on", null, "Elsewhere", 30))

        assertEquals(listOf("mv-a", "mv-b", "mv-old"), lifecycle.movementsForAnimal(farmA, "cow").map { it.id })
        assertEquals(listOf("mv-farm-b"), lifecycle.movementsForAnimal(farmB, "cow").map { it.id })
    }

    @Test
    fun identifiersListActiveFirstThenNewestAssignment() = runBlocking {
        val lifecycle = database.lifecycle()
        lifecycle.insertIdentifier(AnimalIdentifierEntity("i-old-tag", farmA, "cow", "ear_tag", "Y-17", false, 40))
        lifecycle.insertIdentifier(AnimalIdentifierEntity("i-eid", farmA, "cow", "eid", "982000123", true, 10))
        lifecycle.insertIdentifier(AnimalIdentifierEntity("i-official", farmA, "cow", "official_id", "ZA-1", true, 30))
        lifecycle.insertIdentifier(AnimalIdentifierEntity("i-farm-b", farmB, "cow", "official_id", "ZA-9", true, 50))

        assertEquals(listOf("i-official", "i-eid", "i-old-tag"), lifecycle.identifiersForAnimal(farmA, "cow").map { it.id })
        assertEquals(listOf("i-farm-b"), lifecycle.identifiersForAnimal(farmB, "cow").map { it.id })
    }
}
