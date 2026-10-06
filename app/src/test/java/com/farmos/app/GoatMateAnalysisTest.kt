package com.farmos.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.AnimalEntity
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.MeasurementEntity
import com.farmos.core.database.PedigreeRelationEntity
import com.farmos.data.herd.PedigreeQueries
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Owner decision D-023 (R7): the kids' coefficient of inbreeding from the recorded pedigree, read locally. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-rUS")
class GoatMateAnalysisTest {
    private val farm = "dddddddd-dddd-4ddd-8ddd-dddddddddddd"
    private val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FarmOsDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    @After
    fun tearDown() = database.close()

    private suspend fun goat(id: String, sex: String, farmId: String = farm) =
        database.animals().insert(AnimalEntity(id = id, farmId = farmId, tag = "T-$id", name = null, speciesCode = "goat", sex = sex, status = "active", dateOfBirthEpochDay = null, updatedAtEpochMillis = 1L))

    private var link = 0

    private suspend fun parent(child: String, parent: String, type: String, farmId: String = farm) =
        database.lifecycle().insertPedigree(PedigreeRelationEntity("link-${link++}", farmId, child, parent, type))

    @Test
    fun halfSiblingsThroughARecordedSireGiveOneEighth(): Unit = runBlocking {
        listOf("buck" to "MALE", "doe" to "FEMALE", "A" to "MALE", "B" to "FEMALE", "C" to "FEMALE").forEach { (id, sex) -> goat(id, sex) }
        parent("buck", "A", "sire"); parent("buck", "B", "dam")
        parent("doe", "A", "sire"); parent("doe", "C", "dam")
        database.measurements().insert(MeasurementEntity("m1", farm, "buck", "weight", 61_500, "g", 5L))

        val candidate = goatMateAnalysis(database, farm).candidate("doe", "buck")
        assertEquals(0.125, candidate.coefficient, 1e-12)
        assertEquals(listOf("T-A"), candidate.commonAncestors)
        assertEquals(2, candidate.generationsKnown)
        assertEquals(61_500L, candidate.latestWeightGrams)
        assertEquals(0, candidate.conflictingParentage)
    }

    @Test
    fun theGeneticDamOutranksTheRecipientAndConflictingSiresAreLeftOutAndReported(): Unit = runBlocking {
        listOf("buck" to "MALE", "doe" to "FEMALE", "A" to "MALE", "X" to "MALE", "G" to "FEMALE", "R" to "FEMALE").forEach { (id, sex) -> goat(id, sex) }
        // The doe was carried by R but is genetically G's; the buck is G's too, so they are half siblings through G.
        parent("doe", "R", "dam"); parent("doe", "G", "genetic_dam")
        parent("buck", "G", "dam")
        assertEquals(0.125, goatMateAnalysis(database, farm).candidate("doe", "buck").coefficient, 1e-12)

        // Two different sires recorded for the buck: his sire is left unknown and the conflict reported.
        parent("buck", "A", "sire"); parent("buck", "X", "sire")
        val graph = PedigreeQueries(database, farm).graph(listOf("buck"))
        assertEquals(setOf("buck"), graph.conflicting)
        assertEquals(null, graph.parents["buck"]?.sireId)
        assertEquals(1, goatMateAnalysis(database, farm).candidate("doe", "buck").conflictingParentage)
    }

    @Test
    fun onlyABuckOnThisFarmCanBeCompared(): Unit = runBlocking {
        goat("doe", "FEMALE")
        goat("doe2", "FEMALE")
        goat("other-farm-buck", "MALE", farmId = "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee")
        val analysis = goatMateAnalysis(database, farm)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { analysis.candidate("doe", "doe2") } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { analysis.candidate("doe", "other-farm-buck") } }
    }
}
