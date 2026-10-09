package com.farmos.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.LocalAccountEntity
import com.farmos.core.database.LocalFarmEntity
import com.farmos.core.database.ReplicationDeviceEntity
import com.farmos.core.database.recordPeerHolds
import com.farmos.core.database.replicationVector
import com.farmos.core.database.toEnvelope
import com.farmos.core.database.unsharedLocalOperations
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.ops.RecordWater
import com.farmos.domain.replication.DeviceStatus
import com.farmos.domain.replication.FarmDataKey
import com.farmos.domain.replication.FarmKeyRing
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Two devices' real Room journals replicate through the protocol: in process, and over the authenticated
 * farm-LAN socket transport. Operations arrive with their original identity, sequence and business time;
 * bundles from unknown or revoked devices, or with altered operations, change nothing.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-rUS")
class RoomReplicaEndpointTest {
    private val farm = "11111111-1111-4111-8111-111111111111"
    private val tabletId = "device-tablet"
    private val phoneId = "device-phone"
    private lateinit var tabletDb: FarmOsDatabase
    private lateinit var phoneDb: FarmOsDatabase
    private lateinit var tablet: RoomReplicaEndpoint
    private lateinit var phone: RoomReplicaEndpoint

    private fun database() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FarmOsDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    @Before
    fun setUp() {
        tabletDb = database()
        phoneDb = database()
        seedLocalAuthority(tabletDb, tabletId)
        seedLocalAuthority(phoneDb, phoneId)
        tablet = RoomReplicaEndpoint(tabletDb, farm, tabletId).apply { registerPairedDevice(phoneId, "Phone") }
        phone = RoomReplicaEndpoint(phoneDb, farm, phoneId, replicationAppliers).apply { registerPairedDevice(tabletId, "Tablet") }
    }

    private fun seedLocalAuthority(db: FarmOsDatabase, device: String) {
        db.localAccess().insertFarmIfAbsent(LocalFarmEntity(farm, "Review farm", 1_790_000_000_000L))
        for ((actor, role) in listOf("worker-1" to "WORKER", "owner-1" to "OWNER")) {
            db.localAccess().upsertAccount(LocalAccountEntity(
                accountId = actor, farmId = farm, username = actor, displayName = actor,
                role = role, status = "ACTIVE", credentialKind = "PIN", credentialHash = "test-credential-hash",
                failedAttempts = 0, lockedUntilEpochMillis = null, workerId = null, createdAtEpochMillis = 1_790_000_000_000L,
            ))
        }
        db.replicationBlocking().upsertDevice(ReplicationDeviceEntity(
            farmId = farm, deviceId = device, name = device, status = DeviceStatus.ACTIVE.name,
            lastReportedOwnSequence = 0L, revokedAfterSequence = null, isLocal = true,
        ))
    }

    @After
    fun tearDown() {
        tabletDb.close()
        phoneDb.close()
    }

    private fun recordWaterOnTablet(count: Int) = runBlocking {
        val ops = RoomOpsRepository(tabletDb, farm)
        repeat(count) {
            ops.recordWater(RecordWater("water-${UUID.randomUUID()}", "Borehole", 1_000L * (it + 1), 20_700L + it), LocalCommandContext(farm, "worker-1", tabletId, UUID.randomUUID().toString(), 1_790_000_000_000 + it))
        }
    }

    @Test
    fun aPhoneCatchesUpWithTheTabletJournalAndARepeatTransfersNothing() = runBlocking {
        recordWaterOnTablet(3)

        val first = SyncSession.run(phone, LocalPeerTransport(tablet), remoteDeviceId = tabletId)
        assertEquals(SyncSessionStatus.COMPLETED, first.status)
        assertEquals(3, first.pulledOperations)
        assertEquals(tabletDb.replicationVector(farm), phoneDb.replicationVector(farm))
        val original = tabletDb.replication().operationsInRange(farm, tabletId, 1, 3)
        val received = phoneDb.replication().operationsInRange(farm, tabletId, 1, 3)
        assertEquals(original.map { it.operationId to it.businessTimeEpochMillis }, received.map { it.operationId to it.businessTimeEpochMillis })
        received.forEach { assertTrue(it.toEnvelope().checksumValid()) }

        val again = SyncSession.run(phone, LocalPeerTransport(tablet), remoteDeviceId = tabletId)
        assertEquals(0, again.pulledOperations)
        assertEquals(0, again.pushedOperations)
    }

    @Test
    fun theRoomJournalsConvergeOverTheAuthenticatedLanTransport() = runBlocking {
        recordWaterOnTablet(4)
        val keys = FarmKeyRing(listOf(FarmDataKey.generate("k1")), "k1")
        val loopback = InetAddress.getLoopbackAddress()
        LanSyncServer(tablet, { keys }).start(InetSocketAddress(loopback, 0)).use { server ->
            LanPeerTransport(loopback.hostAddress, server.port, farm, phoneId, { keys }, phone::maySynchronise).use { transport ->
                val outcome = SyncSession.run(phone, transport, remoteDeviceId = tabletId)
                assertEquals(SyncSessionStatus.COMPLETED, outcome.status)
                assertEquals(4, outcome.pulledOperations)
            }
        }
        assertEquals(tabletDb.replicationVector(farm), phoneDb.replicationVector(farm))
    }

    @Test
    fun bundlesFromUnknownOrRevokedDevicesOrWithAlteredOperationsChangeNothing() = runBlocking {
        recordWaterOnTablet(2)
        val operations = tabletDb.replication().operationsInRange(farm, tabletId, 1, 2).map { it.toEnvelope() }
        val bundle = OperationBundle.seal(farm, tabletId, operations)

        val strangerDb = database()
        val stranger = RoomReplicaEndpoint(strangerDb, farm, "device-stranger")
        assertNotNull(stranger.ingest(bundle).rejectedReason)
        assertEquals(0L, stranger.vector().watermark(tabletId))
        strangerDb.close()

        val altered = operations.first().copy(checksum = operations.last().checksum)
        assertNotNull(phone.ingest(OperationBundle(farm, tabletId, 1, 2, listOf(altered, operations.last()), bundle.protocolVersion, bundle.checksum)).rejectedReason)
        assertEquals(0L, phoneDb.replication().count(farm))

        val tabletRow = phoneDb.replication().device(farm, tabletId)!!
        phoneDb.replication().upsertDevice(tabletRow.copy(status = DeviceStatus.LOST_REVOKED.name, revokedAfterSequence = 1))
        assertNotNull(phone.ingest(bundle).rejectedReason)
        assertEquals(0L, phoneDb.replication().count(farm))
        assertEquals(false, phone.maySynchronise(tabletId))
    }

    @Test
    fun aCutoffRefusesSessionsEvenWithAnActiveStatusButKeepsPermittedHistory(): Unit = runBlocking {
        recordWaterOnTablet(2)
        val row = requireNotNull(phoneDb.replication().device(farm, tabletId))
        for (status in listOf(DeviceStatus.ACTIVE, DeviceStatus.TEMPORARILY_OFFLINE)) {
            phoneDb.replication().upsertDevice(row.copy(status = status.name, revokedAfterSequence = 1))
            assertEquals(false, phone.maySynchronise(tabletId))
        }
        val operations = tabletDb.replication().operationsInRange(farm, tabletId, 1, 2).map { it.toEnvelope() }
        assertEquals(false, phone.ingest(OperationBundle.seal(farm, tabletId, listOf(operations.first()))).rejected)
        assertEquals(true, phone.ingest(OperationBundle.seal(farm, tabletId, listOf(operations.last()))).rejected)
        assertEquals(1L, phoneDb.replication().count(farm))
        assertEquals(1L, phone.vector().watermark(tabletId))
    }

    @Test
    fun aCurrencyChangeTakesEffectOnTheReceivingDeviceAndAnOlderChangeNeverOverridesANewerOne() = runBlocking {
        tabletDb.setFarmCurrency(farm, "ZAR", actorId = "owner-1", deviceId = tabletId, nowEpochMillis = 1_790_000_500_000)
        phoneDb.setFarmCurrency(farm, "KES", actorId = "owner-1", deviceId = phoneId, nowEpochMillis = 1_790_000_900_000)

        SyncSession.run(phone, LocalPeerTransport(tablet), remoteDeviceId = tabletId)

        // The phone's own change is newer, so the tablet's older one is journalled but does not win.
        assertEquals("KES", phoneDb.farmCurrency(farm))
        assertEquals(1L, phoneDb.replication().operationsInRange(farm, tabletId, 1, 1).size.toLong())

        tabletDb.setFarmCurrency(farm, "USD", actorId = "owner-1", deviceId = tabletId, nowEpochMillis = 1_790_001_000_000)
        SyncSession.run(phone, LocalPeerTransport(tablet), remoteDeviceId = tabletId)
        assertEquals("USD", phoneDb.farmCurrency(farm))
    }

    @Test
    fun localChangesCountAsWaitingToSyncUntilAPeerConfirmsHoldingThem() = runBlocking {
        recordWaterOnTablet(3)
        assertEquals(3L, tabletDb.unsharedLocalOperations(farm, tabletId))

        val outcome = SyncSession.run(tablet, LocalPeerTransport(phone), remoteDeviceId = phoneId)
        assertEquals(3L, outcome.peerHoldsOwnThrough)
        tabletDb.recordPeerHolds(farm, phoneId, outcome.peerHoldsOwnThrough!!, 1_790_000_900_000)
        assertEquals(0L, tabletDb.unsharedLocalOperations(farm, tabletId))

        // A later, lower report never moves the mark backwards.
        tabletDb.recordPeerHolds(farm, phoneId, 1, 1_790_000_950_000)
        recordWaterOnTablet(1)
        assertEquals(1L, tabletDb.unsharedLocalOperations(farm, tabletId))
    }
}
