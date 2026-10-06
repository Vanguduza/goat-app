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

/** Goat reproduction and pedigree reads stay inside one farm and one animal. */
@RunWith(AndroidJUnit4::class)
class GoatReproductionQueryTest {
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
    fun breedingRecordsAreDoeAndFarmScopedNewestFirst() = runBlocking {
        val lifecycle = database.lifecycle()
        lifecycle.insertHeat(GoatHeatEntity("h1", farmA, "nala", 10))
        lifecycle.insertHeat(GoatHeatEntity("h2", farmA, "nala", 20))
        lifecycle.insertHeat(GoatHeatEntity("h3", farmB, "nala", 30))
        lifecycle.insertMating(GoatMatingEntity("m1", farmA, "nala", "kito", "natural", 21))
        lifecycle.insertMating(GoatMatingEntity("m2", farmA, "zuri", null, "ai", 22))
        lifecycle.insertPregnancy(GoatPregnancyEntity("c1", farmA, "nala", "pregnant", 60))
        lifecycle.insertPregnancy(GoatPregnancyEntity("c2", farmB, "nala", "open", 61))

        assertEquals(listOf("h2", "h1"), lifecycle.goatHeatsFor(farmA, "nala").map { it.id })
        assertEquals(listOf("m1"), lifecycle.goatMatingsForDam(farmA, "nala").map { it.id })
        assertEquals(listOf("c1"), lifecycle.goatPregnanciesFor(farmA, "nala").map { it.id })
    }

    @Test
    fun pedigreeParentsAndChildrenNeverCrossFarms() = runBlocking {
        val lifecycle = database.lifecycle()
        lifecycle.insertPedigree(PedigreeRelationEntity("l1", farmA, "kid-1", "nala", "dam"))
        lifecycle.insertPedigree(PedigreeRelationEntity("l2", farmA, "kid-1", "kito", "sire"))
        lifecycle.insertPedigree(PedigreeRelationEntity("l3", farmB, "kid-9", "nala", "dam"))

        assertEquals(listOf("l1", "l2"), lifecycle.pedigreeParents(farmA, "kid-1").map { it.id })
        assertEquals(listOf("l1"), lifecycle.pedigreeChildren(farmA, "nala").map { it.id })
        assertEquals(listOf("l3"), lifecycle.pedigreeChildren(farmB, "nala").map { it.id })
    }
}
