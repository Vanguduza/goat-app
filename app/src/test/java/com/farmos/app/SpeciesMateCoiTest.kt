package com.farmos.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.AnimalEntity
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.PedigreeRelationEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Owner decision D-023 (R7): sheep and cattle offspring inbreeding from the recorded pedigree. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-rUS")
class SpeciesMateCoiTest {
    private val farm = "78787878-7878-4787-8787-787878787878"
    private val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FarmOsDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private var link = 0

    @After
    fun tearDown() = database.close()

    private suspend fun animal(id: String, species: String, sex: String) =
        database.animals().insert(AnimalEntity(id = id, farmId = farm, tag = "T-$id", name = null, speciesCode = species, sex = sex, status = "active", dateOfBirthEpochDay = null, updatedAtEpochMillis = 1L))

    private suspend fun parent(child: String, parent: String, type: String) =
        database.lifecycle().insertPedigree(PedigreeRelationEntity("l${link++}", farm, child, parent, type))

    @Test
    fun halfSiblingSheepGiveOneEighthAndOtherSpeciesOrSexesAreRefused(): Unit = runBlocking {
        animal("ram", "sheep", "MALE"); animal("ewe", "sheep", "FEMALE"); animal("sire", "sheep", "MALE")
        animal("d1", "sheep", "FEMALE"); animal("d2", "sheep", "FEMALE"); animal("cow", "cattle", "FEMALE")
        parent("ram", "sire", "sire"); parent("ram", "d1", "dam")
        parent("ewe", "sire", "sire"); parent("ewe", "d2", "dam")

        val coi = speciesMateCoi(database, farm, "sheep").analyse("ram", "ewe")
        assertEquals(0.125, coi.coefficient, 1e-12)
        assertEquals(2, coi.generationsKnown)
        assertEquals(listOf("T-sire"), coi.commonAncestors)

        assertThrows(IllegalArgumentException::class.java) { runBlocking { speciesMateCoi(database, farm, "sheep").analyse("ewe", "ram") } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { speciesMateCoi(database, farm, "sheep").analyse("ram", "cow") } }
    }
}
