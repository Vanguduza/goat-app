package com.farmos.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.replicationVector
import com.farmos.core.database.toEnvelope
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.ops.CreateInventoryItem
import com.farmos.domain.ops.MoveInventory
import com.farmos.domain.ops.RecordWater
import com.farmos.domain.replication.DeviceRegistry
import com.farmos.domain.replication.DeviceStatus
import com.farmos.domain.replication.FarmDevice
import com.farmos.domain.replication.FarmReplica
import com.farmos.domain.replication.MergeClass
import com.farmos.domain.replication.OperationBundle
import com.farmos.domain.replication.SyncVector
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Every local command commits its domain change, its outbox row and its immutable replication operation
 * in one Room transaction. The journal it produces is accepted by the replication protocol as-is.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-rUS")
class ReplicationJournalOwnerTest {
    private val farm = "11111111-1111-4111-8111-111111111111"
    private val otherFarm = "22222222-2222-4222-8222-222222222222"
    private val device = "device-tablet"
    private lateinit var database: FarmOsDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FarmOsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun context(farmId: String, at: Long) = LocalCommandContext(farmId, "worker-1", device, UUID.randomUUID().toString(), at)

    @Test
    fun eachLocalCommandJournalsOneSealedOperationInDeviceSequence() = runBlocking {
        val ops = RoomOpsRepository(database, farm)
        val water = context(farm, 1_790_000_100_000)
        val item = context(farm, 1_790_000_200_000)
        val receipt = context(farm, 1_790_000_300_000)
        ops.recordWater(RecordWater("water-1", "Borehole", 250_000, 20_700), water)
        ops.createItem(CreateInventoryItem("item-mash", "MASH-20", "Layer mash"), item)
        ops.move(MoveInventory("move-1", "item-mash", "receive", 12_500, 1_790_000_300_000), receipt)

        val journal = database.replication().operationsInRange(farm, device, 1, 10)
        assertEquals(listOf(1L, 2L, 3L), journal.map { it.deviceSequence })
        assertEquals(listOf(water, item, receipt).map { it.mutationId }, journal.map { it.operationId })
        assertEquals(
            listOf(MergeClass.APPEND_ONLY_EVENT, MergeClass.APPEND_ONLY_EVENT, MergeClass.POSTING).map { it.name },
            journal.map { it.mergeClass },
        )
        assertEquals(listOf(water, item, receipt).map { it.occurredAtEpochMillis }, journal.map { it.businessTimeEpochMillis })
        journal.forEach { assertTrue(it.toEnvelope().checksumValid()) }
        assertEquals(SyncVector(mapOf(device to 3)), database.replicationVector(farm))

        val local = database.replication().device(farm, device)!!
        assertTrue(local.isLocal)
        assertEquals(3L, local.lastReportedOwnSequence)
        assertEquals(DeviceStatus.ACTIVE.name, local.status)
    }

    @Test
    fun aRejectedCommandLeavesNoJournalEntryAndSequencesStayPerFarm() = runBlocking {
        val ops = RoomOpsRepository(database, farm)
        ops.createItem(CreateInventoryItem("item-mash", "MASH-20", "Layer mash"), context(farm, 1_790_000_000_000))
        // Issuing more than is on hand is refused before anything is written.
        runCatching { ops.move(MoveInventory("move-x", "item-mash", "issue", 99_000, 1_790_000_100_000), context(farm, 1_790_000_100_000)) }
        assertEquals(1L, database.replication().count(farm))

        RoomOpsRepository(database, otherFarm).recordWater(RecordWater("water-9", "River", 1_000, 20_700), context(otherFarm, 1_790_000_200_000))
        assertEquals(1L, database.replication().operationsInRange(otherFarm, device, 1, 1).single().deviceSequence)
        assertEquals(SyncVector(mapOf(device to 1)), database.replicationVector(farm))
        assertEquals(SyncVector(mapOf(device to 1)), database.replicationVector(otherFarm))
    }

    @Test
    fun theRoomJournalReplicatesThroughTheProtocolWithoutTranslation() = runBlocking {
        val ops = RoomOpsRepository(database, farm)
        repeat(3) { ops.recordWater(RecordWater("water-$it", "Borehole", 1_000L * (it + 1), 20_700L + it), context(farm, 1_790_000_000_000 + it)) }
        val operations = database.replication().operationsInRange(farm, device, 1, 3).map { it.toEnvelope() }

        var ids = 0
        val peer = FarmReplica(
            farmId = farm,
            deviceId = "device-phone",
            registry = DeviceRegistry(listOf(FarmDevice(device, "Tablet", DeviceStatus.ACTIVE), FarmDevice("device-phone", "Phone", DeviceStatus.ACTIVE))),
            newOperationId = { "peer-${++ids}" },
            clock = { 1_790_000_900_000 },
        )
        val result = peer.ingest(OperationBundle.seal(farm, device, operations))
        assertEquals(3, result.applied)
        assertEquals(database.replicationVector(farm), peer.vector())
    }
}
