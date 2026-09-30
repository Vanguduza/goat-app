package com.farmos.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.AnimalEntity
import com.farmos.core.database.ApplicationState
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.AnimalExitCommands
import com.farmos.domain.ops.RecordAnimalExit
import com.farmos.domain.ops.ReverseAnimalExit
import com.farmos.domain.replication.LocalPeerTransport
import com.farmos.domain.replication.SyncSession
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Owner decision D-022 (resolution R6): exits are append-only events; the animal's status follows its
 * standing exit; a mistake is corrected by a reversal, and nothing is deleted.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-rUS")
class AnimalExitCommandsTest {
    private val farm = "cccccccc-cccc-4ccc-8ccc-cccccccccccc"
    private val databases = mutableListOf<FarmOsDatabase>()
    private val day = 20_300L
    private var clock = day * 86_400_000L

    private fun database() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FarmOsDatabase::class.java)
        .allowMainThreadQueries()
        .build()
        .also { databases += it }

    @After
    fun tearDown() = databases.forEach { it.close() }

    private fun context(device: String = "A") = LocalCommandContext(farm, "manager-1", device, UUID.randomUUID().toString(), clock++)

    private suspend fun FarmOsDatabase.animal(id: String) =
        animals().insert(AnimalEntity(id = id, farmId = farm, tag = "T-$id", name = null, speciesCode = "goat", sex = "FEMALE", status = "active", dateOfBirthEpochDay = null, updatedAtEpochMillis = 1L))

    private suspend fun FarmOsDatabase.status(id: String) = animals().get(farm, id)!!.status

    private fun id() = UUID.randomUUID().toString()

    @Test
    fun eachExitSetsTheStatusAndAReversalRestoresItKeepingBoth(): Unit = runBlocking {
        val db = database()
        listOf("a", "b", "c").forEach { db.animal(it) }
        val exits = AnimalExitCommands(db, farm)

        exits.record(RecordAnimalExit(id(), "a", "DEATH", day, deathCause = "PREDATION", reason = "Found at the fence"), context())
        exits.record(RecordAnimalExit(id(), "b", "CULL", day, reason = "Chronic lameness"), context())
        val saleId = id()
        exits.record(RecordAnimalExit(saleId, "c", "SALE", day, buyer = "Moyo Butchery", priceMinor = 12_000, currency = "USD"), context())
        assertEquals(listOf("dead", "culled", "sold"), listOf("a", "b", "c").map { db.status(it) })

        // An animal that has left cannot leave again.
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { exits.record(RecordAnimalExit(id(), "c", "DEATH", day, deathCause = "UNKNOWN"), context()) }
        }

        // The sale was recorded against the wrong goat: reverse it, keeping both records.
        exits.reverse(ReverseAnimalExit(id(), "c", saleId, "Wrong goat recorded", day), context())
        assertEquals("active", db.status("c"))
        assertEquals(listOf("SALE", "REVERSAL"), db.animalExits().forAnimal(farm, "c").map { it.kind })
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { exits.reverse(ReverseAnimalExit(id(), "c", saleId, "Again", day), context()) }
        }
        exits.record(RecordAnimalExit(id(), "c", "DEATH", day, deathCause = "ILLNESS"), context())
        assertEquals("dead", db.status("c"))
    }

    @Test
    fun invalidExitsAreRefused(): Unit = runBlocking {
        val db = database()
        db.animal("a")
        val exits = AnimalExitCommands(db, farm)
        assertThrows(IllegalStateException::class.java) { runBlocking { exits.record(RecordAnimalExit(id(), "a", "DEATH", day), context()) } }
        assertThrows(IllegalStateException::class.java) { runBlocking { exits.record(RecordAnimalExit(id(), "a", "CULL", day + 5, reason = "Lame"), context()) } }
        assertThrows(IllegalStateException::class.java) { runBlocking { exits.record(RecordAnimalExit(id(), "a", "SALE", day, buyer = " "), context()) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { exits.record(RecordAnimalExit(id(), "a", "SALE", day, buyer = "Moyo", priceMinor = 100), context()) } }
        assertThrows(IllegalStateException::class.java) { runBlocking { exits.reverse(ReverseAnimalExit(id(), "a", "none", " ", day), context()) } }
        assertEquals("active", db.status("a"))
        assertEquals(0, db.animalExits().forAnimal(farm, "a").size)
    }

    @Test
    fun exitsReplayAndAConflictingExitWaitsForReview(): Unit = runBlocking {
        val aDb = database()
        val bDb = database()
        listOf(aDb, bDb).forEach { it.animal("g1") }
        val a = RoomReplicaEndpoint(aDb, farm, "A", replicationAppliers).apply { registerPairedDevice("B", "B") }
        val b = RoomReplicaEndpoint(bDb, farm, "B", replicationAppliers).apply { registerPairedDevice("A", "A") }

        // Offline, A records a death and B a sale of the same goat.
        AnimalExitCommands(aDb, farm).record(RecordAnimalExit(id(), "g1", "DEATH", day, deathCause = "UNKNOWN"), context("A"))
        AnimalExitCommands(bDb, farm).record(RecordAnimalExit(id(), "g1", "SALE", day, buyer = "Moyo"), context("B"))
        SyncSession.run(b, LocalPeerTransport(a), remoteDeviceId = "A")

        // Each device keeps what it recorded; the other device's exit waits for review instead of overwriting it.
        assertEquals("dead", aDb.status("g1"))
        assertEquals("sold", bDb.status("g1"))
        assertEquals(1L, aDb.replicationApplications().count(farm, ApplicationState.FAILED.name))
        assertEquals(1L, bDb.replicationApplications().count(farm, ApplicationState.FAILED.name))
    }
}
