package com.farmos.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.ApplicationState
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.toEnvelope
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.goat.GoatReplicationAppliers
import com.farmos.data.goat.RoomGoatRepository
import com.farmos.data.herd.HerdReplicationAppliers
import com.farmos.data.herd.OpsReplicationAppliers
import com.farmos.data.herd.RoomHerdRepository
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.goat.GoatSex
import com.farmos.domain.goat.RecordGoatWeight
import com.farmos.domain.goat.RegisterGoat
import com.farmos.domain.ops.CreateInventoryItem
import com.farmos.domain.ops.MoveInventory
import com.farmos.domain.ops.RecordSale
import com.farmos.domain.ops.RecordWater
import com.farmos.domain.replication.LocalPeerTransport
import com.farmos.domain.replication.OperationBundle
import com.farmos.domain.replication.SyncSession
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Operations received from another device take effect in this device's domain tables through the same
 * command handlers, without being journalled twice. An operation whose dependency has not arrived yet
 * waits, and is applied when it does; it never blocks synchronisation.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-rUS")
class OpsReplicationApplierTest {
    private val farm = "11111111-1111-4111-8111-111111111111"
    private val databases = mutableListOf<FarmOsDatabase>()

    private fun database() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FarmOsDatabase::class.java)
        .allowMainThreadQueries()
        .build()
        .also { databases += it }

    private fun endpoint(db: FarmOsDatabase, device: String, vararg peers: String) =
        RoomReplicaEndpoint(db, farm, device, replicationAppliers).apply { peers.forEach { registerPairedDevice(it, it) } }

    private fun context(device: String, at: Long) = LocalCommandContext(farm, "worker-$device", device, UUID.randomUUID().toString(), at)

    @Before
    fun setUp() {
        databases.clear()
    }

    @After
    fun tearDown() {
        databases.forEach { it.close() }
    }

    @Test
    fun receivedOperationsChangeTheDomainTablesExactlyAsOnTheOriginDevice() = runBlocking {
        val tabletDb = database()
        val phoneDb = database()
        val tablet = endpoint(tabletDb, "tablet", "phone")
        val phone = endpoint(phoneDb, "phone", "tablet")
        val ops = RoomOpsRepository(tabletDb, farm)
        ops.recordWater(RecordWater("water-1", "Borehole", 250_000, 20_700), context("tablet", 1_790_000_100_000))
        ops.createItem(CreateInventoryItem("item-mash", "MASH-20", "Layer mash"), context("tablet", 1_790_000_200_000))
        ops.move(MoveInventory("move-1", "item-mash", "receive", 12_500, 1_790_000_300_000), context("tablet", 1_790_000_300_000))
        ops.move(MoveInventory("move-2", "item-mash", "issue", 2_500, 1_790_000_400_000), context("tablet", 1_790_000_400_000))
        ops.recordSale(RecordSale("sale-1", "live_goat", 1_000, 9_500, "USD", 20_701), context("tablet", 1_790_000_500_000))

        SyncSession.run(phone, LocalPeerTransport(tablet), remoteDeviceId = "tablet")

        assertEquals(tabletDb.water().recent(farm, 10), phoneDb.water().recent(farm, 10))
        assertEquals(10_000L, phoneDb.inventory().item(farm, "item-mash")?.quantityMilli)
        assertEquals(tabletDb.sales().recent(farm, 10), phoneDb.sales().recent(farm, 10))
        assertEquals(5L, phoneDb.replicationApplications().count(farm, ApplicationState.APPLIED.name))
        // Replay writes domain rows only: nothing new is queued or journalled on the receiving device.
        assertEquals(0L, phoneDb.outbox().countUnacknowledgedForFarm(farm))
        assertEquals(5L, phoneDb.replication().count(farm))
    }

    @Test
    fun anOperationArrivingBeforeItsDependencyWaitsAndIsAppliedWhenTheDependencyArrives() = runBlocking {
        val aDb = database()
        val bDb = database()
        val cDb = database()
        val a = endpoint(aDb, "A", "B", "C")
        val b = endpoint(bDb, "B", "A", "C")
        val c = endpoint(cDb, "C", "A", "B")
        RoomOpsRepository(aDb, farm).createItem(CreateInventoryItem("item-mash", "MASH-20", "Layer mash"), context("A", 1_790_000_100_000))
        SyncSession.run(b, LocalPeerTransport(a), remoteDeviceId = "A")
        RoomOpsRepository(bDb, farm).move(MoveInventory("move-1", "item-mash", "receive", 4_000, 1_790_000_200_000), context("B", 1_790_000_200_000))

        // C hears from B first: B's receipt refers to an item C does not know yet.
        val fromB = OperationBundle.seal(farm, "B", bDb.replication().operationsInRange(farm, "B", 1, 1).map { it.toEnvelope() })
        val first = c.ingest(fromB)
        assertNull(first.rejectedReason)
        assertEquals(1L, cDb.replicationApplications().count(farm, ApplicationState.FAILED.name))
        assertNull(cDb.inventory().item(farm, "item-mash"))

        SyncSession.run(c, LocalPeerTransport(a), remoteDeviceId = "A")
        assertEquals(0L, cDb.replicationApplications().count(farm, ApplicationState.FAILED.name))
        assertEquals(4_000L, cDb.inventory().item(farm, "item-mash")?.quantityMilli)
    }

    @Test
    fun everyCommandTheOpsRepositoryJournalsHasAnApplier() {
        fun source(name: String) = java.io.File("../data/herd/src/main/kotlin/com/farmos/data/herd/$name").takeIf { it.exists() }
            ?: java.io.File("data/herd/src/main/kotlin/com/farmos/data/herd/$name")
        val text = listOf("RoomOpsRepository.kt", "BreedingDueCommands.kt", "TaskSeriesCommands.kt", "StockCountCommands.kt", "WorkerRegisterCommands.kt", "AnimalExitCommands.kt").joinToString("\n") { source(it).readText() }
        val journalled = Regex("\"([a-z_]+\\.[a-z_]+\\.v\\d)\"").findAll(text).map { it.groupValues[1] }.toSet()
        assertTrue(journalled.size > 80)
        assertEquals(journalled, journalled.intersect(OpsReplicationAppliers.all.keys))
    }

    @Test
    fun goatsAndOtherSpeciesRegisteredOnOneDeviceAppearWithTheirWeightsOnAnother() = runBlocking {
        val tabletDb = database()
        val phoneDb = database()
        val tablet = endpoint(tabletDb, "tablet", "phone")
        val phone = endpoint(phoneDb, "phone", "tablet")
        val goats = RoomGoatRepository(tabletDb, farm)
        goats.registerGoat(RegisterGoat("goat-1", "G-001", "Nandi", GoatSex.FEMALE), context("tablet", 1_790_000_100_000))
        goats.recordWeight(RecordGoatWeight("goat-1", "w-1", 31_500, 1_790_000_150_000), context("tablet", 1_790_000_150_000))
        val sheep = RoomHerdRepository(tabletDb, farm, "sheep")
        sheep.register("sheep-1", "S-001", null, "FEMALE", null, context("tablet", 1_790_000_200_000))
        sheep.recordWeight("sheep-1", "w-2", 48_000, 1_790_000_250_000, context("tablet", 1_790_000_250_000))
        sheep.setStatus("sheep-1", "sold", context("tablet", 1_790_000_300_000))

        SyncSession.run(phone, LocalPeerTransport(tablet), remoteDeviceId = "tablet")

        assertEquals(tabletDb.animals().get(farm, "goat-1"), phoneDb.animals().get(farm, "goat-1"))
        assertEquals(31_500L, phoneDb.measurements().latest(farm, "goat-1", "weight")?.valueLong)
        assertEquals("sold", phoneDb.animals().get(farm, "sheep-1")?.status)
        assertEquals(48_000L, phoneDb.measurements().latest(farm, "sheep-1", "weight")?.valueLong)
        assertEquals(5L, phoneDb.replicationApplications().count(farm, ApplicationState.APPLIED.name))
        assertEquals(0L, phoneDb.outbox().countUnacknowledgedForFarm(farm))
    }

    @Test
    fun everyGoatCommandHasAnApplier() {
        val source = java.io.File("../data/goat/src/main/kotlin/com/farmos/data/goat/RoomGoatRepository.kt").takeIf { it.exists() }
            ?: java.io.File("data/goat/src/main/kotlin/com/farmos/data/goat/RoomGoatRepository.kt")
        val journalled = Regex("\"(goat\\.[a-z_]+\\.v\\d)\"").findAll(source.readText()).map { it.groupValues[1] }.toSet()
        assertEquals(13, journalled.size)
        assertEquals(journalled, journalled.intersect(GoatReplicationAppliers.all.keys))
        HerdReplicationAppliers.SPECIES.forEach { species ->
            listOf("register", "record_weight", "set_status").forEach { assertTrue("$species.$it.v1" in HerdReplicationAppliers.all) }
        }
    }
}
