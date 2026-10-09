package com.farmos.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.AnimalEntity
import com.farmos.core.database.ApplicationState
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.model.LocalCommandContext
import com.farmos.domain.access.LocalRole
import com.farmos.data.herd.AnimalExitCommands
import com.farmos.data.herd.CustomerCommands
import com.farmos.domain.ops.RecordAnimalExit
import com.farmos.domain.ops.RecordExitSale
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

/** Owner decision D-022 (R6): a sale exit posts no money; its sale money is recorded once, linked to the exit. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-rUS")
class AnimalSaleTest {
    private val farm = "bcbcbcbc-bcbc-4bcb-8bcb-bcbcbcbcbcbc"
    private val databases = mutableListOf<FarmOsDatabase>()
    private val day = 20_300L
    private var clock = day * 86_400_000L

    private fun database(device: String = "A") = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FarmOsDatabase::class.java)
        .allowMainThreadQueries()
        .build()
        .also { db ->
            databases += db
            seedCommandAuthority(db, farm, "manager-1", device, LocalRole.MANAGER)
        }

    @After
    fun tearDown() = databases.forEach { it.close() }

    private fun context(device: String = "A") = LocalCommandContext(farm, "manager-1", device, UUID.randomUUID().toString(), clock++)

    private suspend fun FarmOsDatabase.goat(id: String) =
        animals().insert(AnimalEntity(id = id, farmId = farm, tag = "GT-$id", name = null, speciesCode = "goat", sex = "MALE", status = "active", dateOfBirthEpochDay = null, updatedAtEpochMillis = 1L))

    private fun sale(exitId: String, animalId: String, amount: Long = 15_000) = RecordExitSale(UUID.randomUUID().toString(), exitId, animalId, "GT-$animalId", amount, "USD", day)

    @Test
    fun aSaleExitIsSettledOnceAndAReversedOneIsNotOffered(): Unit = runBlocking {
        val db = database()
        db.goat("g1")
        db.goat("g2")
        val exits = AnimalExitCommands(db, farm)
        val sold = UUID.randomUUID().toString()
        exits.record(RecordAnimalExit(sold, "g1", "SALE", day, buyer = "Moyo Butchery", priceMinor = 15_000, currency = "USD"), context())
        val mistaken = UUID.randomUUID().toString()
        exits.record(RecordAnimalExit(mistaken, "g2", "SALE", day, buyer = "Wrong"), context())
        exits.reverse(ReverseAnimalExit(UUID.randomUUID().toString(), "g2", mistaken, "Wrong goat", day), context())

        // The exit posted no money; only the unreversed sale waits for its money.
        assertEquals(0, db.money().recent(farm, 10).size)
        val waiting = loadUnsettledSaleExits(db, farm)
        assertEquals(listOf(sold), waiting.map { it.exitId })
        assertEquals("150.00", waiting.single().price)

        val sales = CustomerCommands(db, farm)
        sales.recordExitSale(sale(sold, "g1"), context())
        assertEquals(sold, db.sales().forExit(farm, sold)?.exitId)
        assertEquals(listOf("Sale of GT-g1" to 15_000L), db.money().recent(farm, 10).map { it.note to it.amountMinor })
        assertEquals(0, loadUnsettledSaleExits(db, farm).size)

        assertThrows(IllegalArgumentException::class.java) { runBlocking { sales.recordExitSale(sale(sold, "g1"), context()) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { sales.recordExitSale(sale(mistaken, "g2"), context()) } }
        assertEquals(1, db.money().recent(farm, 10).size)
    }

    @Test
    fun theMoneyRecordedTwiceOnTwoDevicesWaitsForReviewInsteadOfPostingTwice(): Unit = runBlocking {
        val aDb = database()
        val bDb = database("B")
        listOf(aDb, bDb).forEach { it.goat("g1") }
        val a = RoomReplicaEndpoint(aDb, farm, "A", replicationAppliers).apply { registerPairedDevice("B", "B") }
        val b = RoomReplicaEndpoint(bDb, farm, "B", replicationAppliers).apply { registerPairedDevice("A", "A") }
        val sold = UUID.randomUUID().toString()
        AnimalExitCommands(aDb, farm).record(RecordAnimalExit(sold, "g1", "SALE", day, buyer = "Moyo"), context("A"))
        SyncSession.run(b, LocalPeerTransport(a), remoteDeviceId = "A")

        // Offline, both devices record the money for the same sale.
        CustomerCommands(aDb, farm).recordExitSale(sale(sold, "g1"), context("A"))
        CustomerCommands(bDb, farm).recordExitSale(sale(sold, "g1", amount = 14_000), context("B"))
        SyncSession.run(b, LocalPeerTransport(a), remoteDeviceId = "A")

        listOf(aDb, bDb).forEach { db ->
            assertEquals(1, db.money().recent(farm, 10).size)
            assertEquals(1L, db.replicationApplications().count(farm, ApplicationState.FAILED.name))
        }
    }

    @Test
    fun anAcceptedSaleRetriesAfterItsExitIsReversedWithoutPostingAgain(): Unit = runBlocking {
        val db = database()
        db.goat("g1")
        val exits = AnimalExitCommands(db, farm)
        val exitId = UUID.randomUUID().toString()
        exits.record(RecordAnimalExit(exitId, "g1", "SALE", day, buyer = "Moyo"), context())
        val acceptedSale = sale(exitId, "g1")
        val acceptedContext = context()
        val sales = CustomerCommands(db, farm)
        sales.recordExitSale(acceptedSale, acceptedContext)
        exits.reverse(ReverseAnimalExit(UUID.randomUUID().toString(), "g1", exitId, "Wrong goat", day), context())
        val before = db.replication().count(farm)

        sales.recordExitSale(acceptedSale, acceptedContext)

        assertEquals("active", db.animals().get(farm, "g1")?.status)
        assertEquals(1, db.money().recent(farm, 10).size)
        assertEquals(15_000L, db.money().recent(farm, 10).single().amountMinor)
        assertEquals(before, db.replication().count(farm))
        assertEquals(acceptedContext.mutationId, db.replication().operation(farm, acceptedContext.mutationId)?.operationId)
    }
}
