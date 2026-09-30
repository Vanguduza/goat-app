package com.farmos.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.TaskSeriesCommands
import com.farmos.data.herd.WorkerRegisterCommands
import com.farmos.domain.ops.CreateFarmWorker
import com.farmos.domain.ops.CreateTaskSeries
import com.farmos.domain.ops.TaskAssignee
import com.farmos.domain.ops.UpdateFarmWorker
import com.farmos.domain.replication.LocalPeerTransport
import com.farmos.domain.replication.SyncSession
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The worker register (D-016, resolution R1): workers without logins can be given work; none is deleted. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-rUS")
class WorkerRegisterCommandsTest {
    private val farm = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"
    private val databases = mutableListOf<FarmOsDatabase>()

    private fun database() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FarmOsDatabase::class.java)
        .allowMainThreadQueries()
        .build()
        .also { databases += it }

    @After
    fun tearDown() = databases.forEach { it.close() }

    private fun context(device: String = "A", at: Long) = LocalCommandContext(farm, "supervisor-1", device, UUID.randomUUID().toString(), at)

    @Test
    fun workersAreAddedRenamedAndDeactivatedNeverDeleted(): Unit = runBlocking {
        val db = database()
        val workers = WorkerRegisterCommands(db, farm)
        workers.create(CreateFarmWorker("w1", "  Tendai  "), context(at = 1))
        workers.update(UpdateFarmWorker("w1", name = "Tendai M."), context(at = 2))
        workers.update(UpdateFarmWorker("w1", active = false), context(at = 3))

        val worker = db.workers().get(farm, "w1")!!
        assertEquals("Tendai M.", worker.name)
        assertFalse(worker.active)
        assertEquals(1, db.workers().all(farm).size)
        assertEquals(0, db.workers().active(farm).size)
        assertThrows(IllegalStateException::class.java) { runBlocking { workers.create(CreateFarmWorker("w2", " "), context(at = 4)) } }
        assertThrows(IllegalStateException::class.java) { runBlocking { workers.update(UpdateFarmWorker("w1"), context(at = 5)) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { workers.update(UpdateFarmWorker("missing", active = true), context(at = 6)) } }
    }

    @Test
    fun onlyActiveWorkersCanBeGivenWork(): Unit = runBlocking {
        val db = database()
        val workers = WorkerRegisterCommands(db, farm)
        val tasks = TaskSeriesCommands(db, farm)
        workers.create(CreateFarmWorker("w1", "Tendai"), context(at = 1))
        tasks.create(CreateTaskSeries("feed", "ops", "FEED", "Feed calves", "DAILY", startEpochDay = 20_000, assignee = TaskAssignee(workerId = "w1")), context(at = 2))
        assertEquals("w1", db.taskSeries().get(farm, "feed")!!.assigneeWorkerId)

        workers.update(UpdateFarmWorker("w1", active = false), context(at = 3))
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { tasks.create(CreateTaskSeries("water", "ops", "WATER", "Troughs", "DAILY", startEpochDay = 20_000, assignee = TaskAssignee(workerId = "w1")), context(at = 4)) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { tasks.create(CreateTaskSeries("water", "ops", "WATER", "Troughs", "DAILY", startEpochDay = 20_000, assignee = TaskAssignee(workerId = "nobody")), context(at = 5)) }
        }
    }

    @Test
    fun theLaterChangeWinsOnEveryDevice(): Unit = runBlocking {
        val aDb = database()
        val bDb = database()
        WorkerRegisterCommands(aDb, farm).create(CreateFarmWorker("w1", "Tendai"), context("A", 1))
        val a = RoomReplicaEndpoint(aDb, farm, "A", replicationAppliers).apply { registerPairedDevice("B", "B") }
        val b = RoomReplicaEndpoint(bDb, farm, "B", replicationAppliers).apply { registerPairedDevice("A", "A") }
        SyncSession.run(b, LocalPeerTransport(a), remoteDeviceId = "A")

        // Offline on both devices: A renames first, B later; the later rename wins everywhere.
        WorkerRegisterCommands(aDb, farm).update(UpdateFarmWorker("w1", name = "Tendai A"), context("A", 10))
        WorkerRegisterCommands(bDb, farm).update(UpdateFarmWorker("w1", name = "Tendai B"), context("B", 20))
        SyncSession.run(b, LocalPeerTransport(a), remoteDeviceId = "A")

        assertEquals("Tendai B", aDb.workers().get(farm, "w1")!!.name)
        assertEquals("Tendai B", bDb.workers().get(farm, "w1")!!.name)
    }
}
