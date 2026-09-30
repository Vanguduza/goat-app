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
import com.farmos.domain.replication.LocalPeerTransport
import com.farmos.domain.replication.SyncSession
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Owner decision D-013: a genuine conflict is resolved by a recorded decision and correction, never by
 * deleting the losing history, and every farm device ends in the same state.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-rUS")
class ConflictResolutionTest {
    private val farm = "56565656-5656-4565-8565-565656565656"
    private val databases = mutableListOf<FarmOsDatabase>()
    private val day = 20_300L
    private var clock = day * 86_400_000L

    private fun database() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FarmOsDatabase::class.java)
        .allowMainThreadQueries()
        .build()
        .also { databases += it }

    @After
    fun tearDown() = databases.forEach { it.close() }

    private fun context(device: String) = LocalCommandContext(farm, "manager-1", device, UUID.randomUUID().toString(), clock++)

    private suspend fun FarmOsDatabase.goat(id: String) =
        animals().insert(AnimalEntity(id = id, farmId = farm, tag = "T-$id", name = null, speciesCode = "goat", sex = "FEMALE", status = "active", dateOfBirthEpochDay = null, updatedAtEpochMillis = 1L))

    private suspend fun FarmOsDatabase.status(id: String) = animals().get(farm, id)!!.status

    private fun FarmOsDatabase.failed() = runBlocking { replicationApplications().count(farm, ApplicationState.FAILED.name) }

    private fun endpoint(db: FarmOsDatabase, device: String, vararg peers: String) =
        RoomReplicaEndpoint(db, farm, device, replicationAppliers).apply { peers.forEach { registerPairedDevice(it, it) } }

    @Test
    fun settingAConflictingExitAsideReversesItWhereItStoodAndEveryDeviceConverges(): Unit = runBlocking {
        val aDb = database()
        val bDb = database()
        val cDb = database()
        listOf(aDb, bDb, cDb).forEach { it.goat("g1") }
        val a = endpoint(aDb, "A", "B", "C")
        val b = endpoint(bDb, "B", "A", "C")
        val c = endpoint(cDb, "C", "A", "B")

        // Offline, A records a death and B a sale of the same goat.
        AnimalExitCommands(aDb, farm).record(RecordAnimalExit(UUID.randomUUID().toString(), "g1", "DEATH", day, deathCause = "PREDATION"), context("A"))
        val saleId = UUID.randomUUID().toString()
        AnimalExitCommands(bDb, farm).record(RecordAnimalExit(saleId, "g1", "SALE", day, buyer = "Moyo Butchery"), context("B"))
        SyncSession.run(b, LocalPeerTransport(a), remoteDeviceId = "A")
        assertEquals(1L, aDb.failed())
        assertEquals(1L, bDb.failed())

        // Management on A reviews the sale from B, with its provenance and content.
        val review = aDb.loadConflictReview(farm)
        val item = review.waiting.single()
        assertEquals(AnimalExitCommands.RECORD, item.operationType)
        assertEquals("B", item.deviceName)
        val detail = aDb.conflictDetail(farm, item)
        assertTrue(detail.fields.contains("buyer" to "Moyo Butchery"))
        assertTrue(detail.correction != null)

        assertThrows(IllegalArgumentException::class.java) { runBlocking { aDb.setAsideReceivedOperation(farm, item.operationId, " ", context("A")) } }
        aDb.setAsideReceivedOperation(farm, item.operationId, "The goat was taken by a leopard before the sale", context("A"))
        assertEquals(0L, aDb.failed())
        assertEquals("dead", aDb.status("g1"))
        assertEquals(item.operationId, aDb.loadConflictReview(farm).setAside.single().operationId)
        // Nothing is deleted: the sale stays in A's journal, and so do both exit records on B.
        assertTrue(aDb.replication().operation(farm, item.operationId) != null)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { aDb.setAsideReceivedOperation(farm, item.operationId, "again", context("A")) } }

        // B receives the decision and its correction: the sale is reversed there and the death takes effect.
        SyncSession.run(b, LocalPeerTransport(a), remoteDeviceId = "A")
        assertEquals("dead", bDb.status("g1"))
        assertEquals(0L, bDb.failed())
        assertEquals(setOf("SALE", "REVERSAL", "DEATH"), bDb.animalExits().forAnimal(farm, "g1").map { it.kind }.toSet())

        // A third device that receives everything later ends in the same state, with nothing waiting.
        SyncSession.run(c, LocalPeerTransport(a), remoteDeviceId = "A")
        assertEquals("dead", cDb.status("g1"))
        assertEquals(0L, cDb.failed())
        assertEquals(ApplicationState.SET_ASIDE.name, cDb.replicationApplications().get(farm, item.operationId)?.state)
    }
}
