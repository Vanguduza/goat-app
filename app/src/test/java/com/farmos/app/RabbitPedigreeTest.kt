package com.farmos.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.AnimalEntity
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.access.LocalRole
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Rabbit pedigree (FOS-RABBIT-031) and the kits' inbreeding (FOS-GEN-006), owner decision D-023. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-rUS")
class RabbitPedigreeTest {
    private val farm = "9a9a9a9a-9a9a-49a9-89a9-9a9a9a9a9a9a"
    private val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FarmOsDatabase::class.java)
        .allowMainThreadQueries()
        .build().also { seedCommandAuthority(it, farm, "manager-1", "A", LocalRole.MANAGER) }

    @After
    fun tearDown() = database.close()

    private suspend fun rabbit(id: String, sex: String) =
        database.animals().insert(AnimalEntity(id = id, farmId = farm, tag = "RB-$id", name = null, speciesCode = "rabbit", sex = sex, status = "active", dateOfBirthEpochDay = null, updatedAtEpochMillis = 1L))

    private fun context() = LocalCommandContext(farm, "manager-1", "A", UUID.randomUUID().toString(), 1_790_000_000_000)

    @Test
    fun parentsAreLinkedBySexAndHalfSiblingKitsShowOneEighth(): Unit = runBlocking {
        listOf("buck" to "MALE", "sire" to "MALE", "doe" to "FEMALE", "d1" to "FEMALE", "d2" to "FEMALE").forEach { (id, sex) -> rabbit(id, sex) }
        var linked = 0
        val ports = rabbitPedigreePorts(database, farm, RoomOpsRepository(database, farm), ::context) { linked++ }

        ports.link("buck", "sire", "sire")
        ports.link("buck", "d1", "dam")
        ports.link("doe", "sire", "sire")
        ports.link("doe", "d2", "dam")
        assertEquals(4, linked)
        assertEquals(setOf("sire" to "RB-sire", "dam" to "RB-d1"), ports.parents("buck").map { it.relation to it.label }.toSet())

        // A doe cannot be a sire, nor a buck a dam.
        assertThrows(IllegalArgumentException::class.java) { runBlocking { ports.link("buck", "d2", "sire") } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { ports.link("doe", "sire", "dam") } }

        val coi = ports.coi("buck", "doe")
        assertEquals(0.125, coi.coefficient, 1e-12)
        assertEquals(listOf("RB-sire"), coi.commonAncestors)
    }
}
