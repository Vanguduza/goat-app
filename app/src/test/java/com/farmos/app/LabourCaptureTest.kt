package com.farmos.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.model.LocalCommandContext
import com.farmos.domain.access.LocalRole
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.data.herd.WorkerRegisterCommands
import com.farmos.domain.ops.CreateFarmWorker
import com.farmos.domain.ops.UpdateFarmWorker
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

/** Resolution R1: labour is recorded against a registered worker, and totals follow the worker, not the name. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-rUS")
class LabourCaptureTest {
    private val farm = "12121212-1212-4121-8121-121212121212"
    private val databases = mutableListOf<FarmOsDatabase>()
    private var clock = 1_790_000_000_000L

    private fun database(device: String = "A") = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FarmOsDatabase::class.java)
        .allowMainThreadQueries()
        .build()
        .also { db ->
            databases += db
            seedCommandAuthority(db, farm, "supervisor-1", device, LocalRole.SUPERVISOR)
        }

    @After
    fun tearDown() = databases.forEach { it.close() }

    private fun context(device: String = "A") = LocalCommandContext(farm, "supervisor-1", device, UUID.randomUUID().toString(), clock++)

    private fun capture(db: FarmOsDatabase) = LabourCapture(db, farm, RoomOpsRepository(db, farm))

    @Test
    fun beforeWorkersAreRegisteredATypedLabelIsKept(): Unit = runBlocking {
        val db = database()
        val capture = capture(db)
        capture.record(capture.activeWorkers(), null, "Farai", "CHECK", "30", "2026-09-29", context())
        assertEquals(listOf("Farai" to 30L), db.labour().totalsByWorkerName(farm).map { it.workerName to it.minutes })
    }

    @Test
    fun registeredWorkersAreChosenAndTotalsSurviveARenameAndReplicate(): Unit = runBlocking {
        val aDb = database()
        val bDb = database("B")
        val register = WorkerRegisterCommands(aDb, farm)
        register.create(CreateFarmWorker("w1", "Tendai"), context())
        register.create(CreateFarmWorker("w2", "Rudo"), context())
        val capture = capture(aDb)
        val workers = capture.activeWorkers()

        // With a register, a worker must be chosen; a typed name is not enough.
        assertThrows(IllegalStateException::class.java) { runBlocking { capture.record(workers, null, "Tendai", "MILK", "60", "2026-09-29", context()) } }
        capture.record(workers, "w1", "", "MILK", "60", "2026-09-29", context())
        register.update(UpdateFarmWorker("w1", name = "Tendai Moyo"), context())
        capture.record(capture.activeWorkers(), "w1", "", "MILK", "45", "2026-09-30", context())
        assertEquals(listOf("Tendai Moyo" to 105L), aDb.labour().totalsByWorkerName(farm).map { it.workerName to it.minutes })

        // An inactive worker cannot be chosen on this device.
        register.update(UpdateFarmWorker("w2", active = false), context())
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { com.farmos.data.herd.LabourCommands(aDb, farm).record(com.farmos.domain.ops.RecordWorkerLabour(UUID.randomUUID().toString(), "w2", "Rudo", "MILK", 10, 20_000), context()) }
        }

        // The register and the entries replicate; the other device totals by worker the same way.
        val a = RoomReplicaEndpoint(aDb, farm, "A", replicationAppliers).apply { registerPairedDevice("B", "B") }
        val b = RoomReplicaEndpoint(bDb, farm, "B", replicationAppliers).apply { registerPairedDevice("A", "A") }
        SyncSession.run(b, LocalPeerTransport(a), remoteDeviceId = "A")
        assertEquals(listOf("Tendai Moyo" to 105L), bDb.labour().totalsByWorkerName(farm).map { it.workerName to it.minutes })
        assertEquals(listOf("w1", "w1"), bDb.labour().recent(farm, 10).map { it.workerId })
    }
}
