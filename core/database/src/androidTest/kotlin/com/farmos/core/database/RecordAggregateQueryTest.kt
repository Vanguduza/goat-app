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

/**
 * Whole-history aggregates behind the sheep wool and poultry record pages are exhaustive and
 * farm-scoped: they stay correct past the bounded presentation lists (100 wool rows, 500
 * poultry rows) and never include another farm's records.
 */
@RunWith(AndroidJUnit4::class)
class RecordAggregateQueryTest {
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
    fun sheepWoolTotalsCoverEveryClipBeyondTheListBound() = runBlocking {
        val lifecycle = database.lifecycle()
        repeat(150) { lifecycle.insertWool(SheepWoolEntity("a$it", farmA, "s1", null, greasyGrams = 1_000, occurredEpochDay = it.toLong())) }
        repeat(3) { lifecycle.insertWool(SheepWoolEntity("b$it", farmB, "s1", null, greasyGrams = 7, occurredEpochDay = it.toLong())) }
        lifecycle.insertShearing(SheepShearingEntity("sh1", farmA, null, "mob-1", "full", 900, 5))
        lifecycle.insertMicron(SheepMicronEntity("m1", farmB, null, "mob-1", 185, 5))

        assertEquals(100, lifecycle.sheepWoolClips(farmA, 100).size)
        assertEquals(150_000L, lifecycle.sheepWoolGreasyGramsTotal(farmA))
        assertEquals(150, lifecycle.sheepWoolClipCount(farmA))
        assertEquals(21L, lifecycle.sheepWoolGreasyGramsTotal(farmB))
        assertEquals(3, lifecycle.sheepWoolClipCount(farmB))
        assertEquals(1, lifecycle.sheepShearingEventCount(farmA))
        assertEquals(0, lifecycle.sheepShearingEventCount(farmB))
        assertEquals(0, lifecycle.sheepMicronTestCount(farmA))
        assertEquals(1, lifecycle.sheepMicronTestCount(farmB))
        assertEquals(0L, database.lifecycle().sheepWoolGreasyGramsTotal("33333333-3333-4333-8333-333333333333"))
    }

    @Test
    fun poultryFlockAggregatesCoverEveryPlacementBeyondTheListBound() = runBlocking {
        val lifecycle = database.lifecycle()
        repeat(520) { lifecycle.insertPlacement(PoultryPlacementEntity("a$it", farmA, "g1", if (it % 2 == 0) "h1" else "h2", "chicken", 10, 100L + it)) }
        lifecycle.insertPlacement(PoultryPlacementEntity("a-g2", farmA, "g2", "h1", "duck", 40, 50))
        lifecycle.insertPlacement(PoultryPlacementEntity("b1", farmB, "g1", "h9", "chicken", 999, 1))
        repeat(3) { lifecycle.insertVaccination(PoultryVaccinationEntity("va$it", farmA, "g1", "chicken", "f1", it.toLong())) }
        lifecycle.insertVaccination(PoultryVaccinationEntity("vb", farmB, "g1", "chicken", "f1", 1))
        repeat(510) { lifecycle.insertBiosecurity(PoultryBiosecurityEntity("wa$it", farmA, "h1", null, "ok", mixedSpecies = it < 7, occurredEpochDay = it.toLong())) }
        lifecycle.insertBiosecurity(PoultryBiosecurityEntity("wa-farm", farmA, null, "g1", "ok", false, 600))
        lifecycle.insertBiosecurity(PoultryBiosecurityEntity("wb", farmB, "h1", null, "ok", true, 1))

        assertEquals(500, lifecycle.placements(farmA, 500).size)
        assertEquals(
            listOf(PoultryPlacementTotal("g1", "chicken", 5_200, 100, 520), PoultryPlacementTotal("g2", "duck", 40, 50, 1)),
            lifecycle.poultryPlacementTotals(farmA),
        )
        assertEquals(listOf(PoultryPlacementTotal("g1", "chicken", 999, 1, 1)), lifecycle.poultryPlacementTotals(farmB))
        assertEquals(
            listOf(PoultryGroupHouse("g1", "h1"), PoultryGroupHouse("g1", "h2"), PoultryGroupHouse("g2", "h1")),
            lifecycle.poultryGroupHouses(farmA),
        )
        assertEquals(listOf(RecordKeyCount("h1", 261), RecordKeyCount("h2", 260)), lifecycle.poultryPlacementCountsByHouse(farmA))
        assertEquals(listOf(RecordKeyCount("g1", 3)), lifecycle.poultryVaccinationCountsByGroup(farmA))
        assertEquals(listOf(RecordKeyCount("g1", 1)), lifecycle.poultryVaccinationCountsByGroup(farmB))
        assertEquals(500, lifecycle.biosecurityWalks(farmA, 500).size)
        assertEquals(511, lifecycle.poultryWalkCount(farmA))
        assertEquals(7, lifecycle.poultryMixedSpeciesWalkCount(farmA))
        assertEquals(listOf(RecordKeyCount("h1", 510)), lifecycle.poultryWalkCountsByHouse(farmA))
        assertEquals(1, lifecycle.poultryMixedSpeciesWalkCount(farmB))
    }

    @Test
    fun bulkLookupsReturnOnlyTheRequestedFarmsRows() = runBlocking {
        val animals = database.animals()
        animals.insert(AnimalEntity("s1", farmA, "EWE-1", null, "sheep", "FEMALE", "active", null, updatedAtEpochMillis = 1))
        animals.insert(AnimalEntity("s2", farmA, "EWE-2", null, "sheep", "FEMALE", "active", null, updatedAtEpochMillis = 1))
        animals.insert(AnimalEntity("s3", farmB, "EWE-3", null, "sheep", "FEMALE", "active", null, updatedAtEpochMillis = 1))
        val formulary = database.formulary()
        formulary.insert(FormularyItemEntity("f1", farmA, "Marek's vaccine", "poultry", "vaccine", null, null, null, true))
        formulary.insert(FormularyItemEntity("f2", farmB, "Other vaccine", "poultry", "vaccine", null, null, null, true))

        assertEquals(listOf("EWE-1", "EWE-2"), animals.getMany(farmA, listOf("s2", "s1", "s3", "missing")).map { it.tag })
        assertEquals(listOf("EWE-3"), animals.getMany(farmB, listOf("s1", "s3")).map { it.tag })
        assertEquals(listOf("Marek's vaccine"), formulary.getMany(farmA, listOf("f1", "f2")).map { it.productName })
        assertEquals(emptyList<String>(), formulary.getMany(farmB, listOf("f1")).map { it.productName })
    }
}
