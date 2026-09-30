package com.farmos.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.data.herd.StockCountCommands
import com.farmos.domain.ops.CreateInventoryItem
import com.farmos.domain.ops.MoveInventory
import com.farmos.domain.ops.PostStockCount
import com.farmos.domain.ops.RecordStockCountLine
import com.farmos.domain.ops.RejectStockCount
import com.farmos.domain.ops.StartStockCount
import com.farmos.domain.ops.StockCountAdjustment
import com.farmos.domain.ops.StockCountLine
import com.farmos.domain.ops.StockCountRules
import com.farmos.domain.ops.SubmitStockCount
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
 * Owner decision D-021: a count records what was found; its variances become stock adjustments only when
 * management posts it, once, on every device; a rejected count adjusts nothing and is kept.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-rUS")
class StockCountCommandsTest {
    private val farm = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
    private val databases = mutableListOf<FarmOsDatabase>()

    private fun database() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FarmOsDatabase::class.java)
        .allowMainThreadQueries()
        .build()
        .also { databases += it }

    @After
    fun tearDown() = databases.forEach { it.close() }

    private var clock = 1_790_000_000_000
    private fun context(device: String = "A", actor: String = "worker-1") = LocalCommandContext(farm, actor, device, UUID.randomUUID().toString(), clock++)

    private suspend fun FarmOsDatabase.onHand(itemId: String) = inventory().item(farm, itemId)!!.quantityMilli

    private suspend fun FarmOsDatabase.stock(itemId: String, quantity: Long) {
        val ops = RoomOpsRepository(this, farm)
        ops.createItem(CreateInventoryItem(itemId, itemId.uppercase(), itemId), context())
        ops.move(MoveInventory(UUID.randomUUID().toString(), itemId, "receive", quantity, clock), context())
    }

    private suspend fun FarmOsDatabase.adjustments(countId: String) = StockCountRules.adjustments(
        stockCounts().lines(farm, countId).map { StockCountLine(it.itemId, it.onHandAtCountMilli, it.countedMilli) },
    ).map { StockCountAdjustment(it.itemId, it.varianceMilli) }

    @Test
    fun postingAppliesEachVarianceOnceAndKeepsLaterMovements(): Unit = runBlocking {
        val aDb = database()
        val bDb = database()
        aDb.stock("mash", 10_000)
        aDb.stock("salt", 4_000)
        aDb.stock("wire", 7_000)
        val counts = StockCountCommands(aDb, farm)

        counts.start(StartStockCount("c1"), context())
        counts.recordLine(RecordStockCountLine("c1", "mash", 12_500, aDb.onHand("mash")), context())
        counts.recordLine(RecordStockCountLine("c1", "salt", 3_000, aDb.onHand("salt")), context())
        counts.recordLine(RecordStockCountLine("c1", "wire", 7_000, aDb.onHand("wire")), context())
        // Counting an item again replaces its line.
        counts.recordLine(RecordStockCountLine("c1", "mash", 12_000, aDb.onHand("mash")), context())
        counts.submit(SubmitStockCount("c1"), context())
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { counts.recordLine(RecordStockCountLine("c1", "salt", 1_000, 4_000), context()) }
        }

        // Stock moves after the count and before it is posted; the posted variance does not undo it.
        RoomOpsRepository(aDb, farm).move(MoveInventory(UUID.randomUUID().toString(), "mash", "issue", 1_000, clock), context())
        val adjustments = aDb.adjustments("c1")
        assertEquals(listOf(StockCountAdjustment("mash", 2_000), StockCountAdjustment("salt", -1_000)), adjustments)
        counts.post(PostStockCount("c1", adjustments), context(actor = "manager-1"))

        assertEquals(11_000L, aDb.onHand("mash"))
        assertEquals(3_000L, aDb.onHand("salt"))
        assertEquals(7_000L, aDb.onHand("wire"))
        assertEquals("POSTED", aDb.stockCounts().get(farm, "c1")!!.status)
        assertEquals("manager-1", aDb.stockCounts().get(farm, "c1")!!.decidedByActorId)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { counts.post(PostStockCount("c1", adjustments), context(actor = "manager-1")) }
        }

        // Another device receives everything and ends with the same stock; syncing again changes nothing.
        val a = RoomReplicaEndpoint(aDb, farm, "A", replicationAppliers).apply { registerPairedDevice("B", "B") }
        val b = RoomReplicaEndpoint(bDb, farm, "B", replicationAppliers).apply { registerPairedDevice("A", "A") }
        SyncSession.run(b, LocalPeerTransport(a), remoteDeviceId = "A")
        SyncSession.run(b, LocalPeerTransport(a), remoteDeviceId = "A")
        assertEquals(listOf(11_000L, 3_000L, 7_000L), listOf("mash", "salt", "wire").map { bDb.onHand(it) })
        assertEquals("POSTED", bDb.stockCounts().get(farm, "c1")!!.status)
    }

    @Test
    fun aRejectedCountAdjustsNothingAndIsKept(): Unit = runBlocking {
        val db = database()
        db.stock("mash", 10_000)
        val counts = StockCountCommands(db, farm)
        counts.start(StartStockCount("c2"), context())
        counts.recordLine(RecordStockCountLine("c2", "mash", 8_000, 10_000), context())
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { counts.post(PostStockCount("c2", listOf(StockCountAdjustment("mash", -2_000))), context(actor = "manager-1")) }
        }
        counts.submit(SubmitStockCount("c2"), context())
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { counts.reject(RejectStockCount("c2", " "), context(actor = "manager-1")) }
        }
        counts.reject(RejectStockCount("c2", "Bags in the second shed were not counted"), context(actor = "manager-1"))

        assertEquals(10_000L, db.onHand("mash"))
        val count = db.stockCounts().get(farm, "c2")!!
        assertEquals("REJECTED", count.status)
        assertEquals("Bags in the second shed were not counted", count.rejectionReason)
        assertEquals(1, db.stockCounts().lines(farm, "c2").size)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { counts.post(PostStockCount("c2", listOf(StockCountAdjustment("mash", -2_000))), context(actor = "manager-1")) }
        }
        assertThrows(IllegalStateException::class.java) {
            runBlocking { counts.recordLine(RecordStockCountLine("c2", "mash", -1, 10_000), context()) }
        }
    }
}
