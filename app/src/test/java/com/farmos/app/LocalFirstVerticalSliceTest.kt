package com.farmos.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.toEnvelope
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.goat.RoomGoatRepository
import com.farmos.domain.access.LocalRole
import com.farmos.domain.goat.GoatSex
import com.farmos.domain.goat.RecordGoatWeight
import com.farmos.domain.goat.RegisterGoat
import com.farmos.domain.replication.DeviceStatus
import com.farmos.domain.replication.FarmDataKey
import com.farmos.domain.replication.FarmKeyRing
import com.farmos.domain.replication.GoogleDriveTransport
import com.farmos.domain.replication.ImmutableBundleStore
import com.farmos.domain.replication.LanPeerTransport
import com.farmos.domain.replication.LanSyncServer
import com.farmos.domain.replication.LocalPeerTransport
import com.farmos.domain.replication.OperationBundle
import com.farmos.domain.replication.SyncSession
import com.farmos.domain.replication.SyncSessionStatus
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Designated local-first slice: register goat, record weight, survive in-process restart of the Room
 * files are covered by instrumentation; this suite proves LAN/Drive second-device visibility, local
 * search, farm isolation, lost-device refusal and that a down transport never rolls back a local save.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-rUS")
class LocalFirstVerticalSliceTest {
    private val farm = "11111111-1111-4111-8111-111111111111"
    private val otherFarm = "22222222-2222-4222-8222-222222222222"
    private val tabletId = "device-tablet"
    private val phoneId = "device-phone"
    private val nalaId = "33333333-3333-4333-8333-333333333333"
    private val weightId = "66666666-6666-4666-8666-666666666666"
    private val databases = mutableListOf<FarmOsDatabase>()

    private fun database(): FarmOsDatabase =
        Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FarmOsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also { databases += it }

    private fun endpoint(db: FarmOsDatabase, device: String, vararg peers: String) =
        RoomReplicaEndpoint(db, farm, device, replicationAppliers).apply { peers.forEach { registerPairedDevice(it, it) } }

    private fun context(device: String, at: Long, mutationId: String = UUID.randomUUID().toString()) =
        LocalCommandContext(farm, "worker-$device", device, mutationId, at)

    private fun registerNala(db: FarmOsDatabase, device: String) = runBlocking {
        seedCommandAuthority(db, farm, "worker-$device", device, LocalRole.WORKER)
        val goats = RoomGoatRepository(db, farm, localDeviceId = device)
        goats.registerGoat(
            RegisterGoat(nalaId, "GT-024", "Nala", GoatSex.FEMALE, dateOfBirthEpochDay = 20_150),
            context(device, 1_790_000_100_000),
        )
        goats.recordWeight(
            RecordGoatWeight(nalaId, weightId, 32_450L, 1_790_000_150_000),
            context(device, 1_790_000_150_000),
        )
        goats
    }

    @Before
    fun setUp() {
        databases.clear()
    }

    @After
    fun tearDown() {
        databases.forEach { it.close() }
    }

    @Test
    fun aSecondDeviceSeesNalaAndHerWeightOverTheFarmLanAndFindsHerInLocalSearch() = runBlocking {
        val tabletDb = database()
        val phoneDb = database()
        val tablet = endpoint(tabletDb, tabletId, phoneId)
        val phone = endpoint(phoneDb, phoneId, tabletId)
        registerNala(tabletDb, tabletId)
        assertNull(RoomGoatRepository(phoneDb, farm, localDeviceId = phoneId).getGoat(nalaId))

        val keys = FarmKeyRing(listOf(FarmDataKey.generate("k1")), "k1")
        val loopback = InetAddress.getLoopbackAddress()
        LanSyncServer(tablet, { keys }).start(InetSocketAddress(loopback, 0)).use { server ->
            LanPeerTransport(loopback.hostAddress ?: "127.0.0.1", server.port, farm, phoneId, { keys }, phone::maySynchronise).use { transport ->
                val outcome = SyncSession.run(phone, transport, remoteDeviceId = tabletId)
                assertEquals(SyncSessionStatus.COMPLETED, outcome.status)
                assertEquals(2, outcome.pulledOperations)
            }
        }

        val goats = RoomGoatRepository(phoneDb, farm, localDeviceId = phoneId)
        val nala = goats.getGoat(nalaId)
        assertNotNull(nala)
        assertEquals("GT-024", nala?.tag)
        assertEquals("Nala", nala?.name)
        assertEquals(32_450L, nala?.latestWeightGrams)
        val found = goats.searchGoats("Nala", limit = 20)
        assertEquals(listOf(nalaId), found.map { it.animalId })
        assertEquals(0L, phoneDb.outbox().countUnacknowledgedForFarm(farm))

        val again = SyncSession.run(phone, LocalPeerTransport(tablet), remoteDeviceId = tabletId)
        assertEquals(0, again.pulledOperations)
    }

    @Test
    fun aDriveJournalCatchUpShowsTheSameGoatAndADownLanLeavesTheLocalSaveStanding() = runBlocking {
        val tabletDb = database()
        val phoneDb = database()
        val tablet = endpoint(tabletDb, tabletId, phoneId)
        val phone = endpoint(phoneDb, phoneId, tabletId)
        registerNala(tabletDb, tabletId)

        val down = SyncSession.run(phone, LocalPeerTransport(tablet, reachable = { false }), remoteDeviceId = tabletId)
        assertEquals(SyncSessionStatus.TRANSPORT_UNAVAILABLE, down.status)
        assertNotNull(RoomGoatRepository(tabletDb, farm, localDeviceId = tabletId).getGoat(nalaId))
        assertNull(RoomGoatRepository(phoneDb, farm, localDeviceId = phoneId).getGoat(nalaId))

        val drive = ImmutableBundleStore()
        assertEquals(SyncSessionStatus.COMPLETED, SyncSession.run(tablet, GoogleDriveTransport(drive)).status)
        val catchUp = SyncSession.run(phone, GoogleDriveTransport(drive), remoteDeviceId = tabletId)
        assertEquals(SyncSessionStatus.COMPLETED, catchUp.status)
        assertEquals(2, catchUp.pulledOperations)
        assertEquals(32_450L, RoomGoatRepository(phoneDb, farm, localDeviceId = phoneId).getGoat(nalaId)?.latestWeightGrams)
    }

    @Test
    fun anotherFarmCannotTakeTheGoatAndALostDeviceCannotSynchronise() = runBlocking {
        val tabletDb = database()
        val phoneDb = database()
        val strangerDb = database()
        val tablet = endpoint(tabletDb, tabletId, phoneId)
        val phone = endpoint(phoneDb, phoneId, tabletId)
        registerNala(tabletDb, tabletId)
        val operations = tabletDb.replication().operationsInRange(farm, tabletId, 1, 2).map { it.toEnvelope() }
        val bundle = OperationBundle.seal(farm, tabletId, operations)

        val stranger = RoomReplicaEndpoint(strangerDb, otherFarm, "device-stranger", replicationAppliers)
        assertNotNull(stranger.ingest(bundle).rejectedReason)
        assertNull(RoomGoatRepository(strangerDb, otherFarm).getGoat(nalaId))

        val first = phone.ingest(bundle)
        assertEquals(2, first.applied)
        val duplicate = phone.ingest(bundle)
        assertTrue(duplicate.rejectedReason == null)
        assertEquals(2, duplicate.duplicates)
        assertEquals(1, RoomGoatRepository(phoneDb, farm).searchGoats("GT-024", 10).size)

        val tabletRow = phoneDb.replication().device(farm, tabletId)!!
        phoneDb.replication().upsertDevice(tabletRow.copy(status = DeviceStatus.LOST_REVOKED.name, revokedAfterSequence = 1))
        assertEquals(false, phone.maySynchronise(tabletId))
        assertNotNull(phone.ingest(bundle).rejectedReason)
        assertEquals("Nala", RoomGoatRepository(phoneDb, farm).getGoat(nalaId)?.name)
    }
}
