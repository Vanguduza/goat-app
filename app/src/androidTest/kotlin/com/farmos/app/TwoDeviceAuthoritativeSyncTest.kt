package com.farmos.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.goat.RoomGoatRepository
import com.farmos.domain.goat.GoatSex
import com.farmos.domain.goat.RecordGoatWeight
import com.farmos.domain.goat.RegisterGoat
import com.farmos.domain.replication.FarmDataKey
import com.farmos.domain.replication.FarmKeyRing
import com.farmos.domain.replication.LanPeerTransport
import com.farmos.domain.replication.LanSyncServer
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

/**
 * Designated slice on two file-backed Room databases: register goat, record weight, close and reopen
 * the origin database, then replicate over the authenticated farm-LAN socket. The second device finds
 * the goat with local search. No application server.
 */
@RunWith(AndroidJUnit4::class)
class TwoDeviceAuthoritativeSyncTest {
    private lateinit var context: Context
    private val deviceADatabaseName = "farm-os-e2e-device-a.db"
    private val deviceBDatabaseName = "farm-os-e2e-device-b.db"
    private val farmId = "11111111-1111-4111-8111-111111111111"
    private val tabletId = "android-e2e-device-a"
    private val phoneId = "android-e2e-device-b"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(deviceADatabaseName)
        context.deleteDatabase(deviceBDatabaseName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(deviceADatabaseName)
        context.deleteDatabase(deviceBDatabaseName)
    }

    @Test
    fun secondIndependentDeviceReceivesAuthoritativeGoatAndWeightChanges() = runBlocking {
        val animalId = UUID.randomUUID().toString()
        val firstWeightId = UUID.randomUUID().toString()

        val databaseA = openDatabase(deviceADatabaseName)
        seedAndroidCommandWorker(databaseA, farmId, "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", tabletId)
        val goatsA = RoomGoatRepository(databaseA, farmId, localDeviceId = tabletId)
        goatsA.registerGoat(
            RegisterGoat(animalId, "GT-024", "Nala", GoatSex.FEMALE, dateOfBirthEpochDay = 20_150),
            context(tabletId, 1_700_000_000_000L),
        )
        goatsA.recordWeight(
            RecordGoatWeight(animalId, firstWeightId, 32_450L, 1_700_000_001_000L),
            context(tabletId, 1_700_000_001_000L),
        )
        assertEquals("Nala", goatsA.getGoat(animalId)?.name)
        assertEquals(32_450L, goatsA.getGoat(animalId)?.latestWeightGrams)
        databaseA.close()

        val reopenedA = openDatabase(deviceADatabaseName)
        val goatsReopened = RoomGoatRepository(reopenedA, farmId, localDeviceId = tabletId)
        assertEquals("Nala", goatsReopened.getGoat(animalId)?.name)
        assertEquals(32_450L, goatsReopened.getGoat(animalId)?.latestWeightGrams)

        val databaseB = openDatabase(deviceBDatabaseName)
        try {
            assertNull(RoomGoatRepository(databaseB, farmId, localDeviceId = phoneId).getGoat(animalId))
            val tablet = RoomReplicaEndpoint(reopenedA, farmId, tabletId, replicationAppliers).apply {
                registerPairedDevice(phoneId, "Phone")
            }
            val phone = RoomReplicaEndpoint(databaseB, farmId, phoneId, replicationAppliers).apply {
                registerPairedDevice(tabletId, "Tablet")
            }
            val keys = FarmKeyRing(listOf(FarmDataKey.generate("k1")), "k1")
            val loopback = InetAddress.getLoopbackAddress()
            LanSyncServer(tablet, { keys }).start(InetSocketAddress(loopback, 0)).use { server ->
                LanPeerTransport(
                    loopback.hostAddress ?: "127.0.0.1",
                    server.port,
                    farmId,
                    phoneId,
                    { keys },
                    phone::maySynchronise,
                ).use { transport ->
                    val first = SyncSession.run(phone, transport, remoteDeviceId = tabletId)
                    assertEquals(SyncSessionStatus.COMPLETED, first.status)
                    assertEquals(2, first.pulledOperations)
                }
            }

            val goatsB = RoomGoatRepository(databaseB, farmId, localDeviceId = phoneId)
            val firstSnapshot = goatsB.getGoat(animalId)
            assertNotNull(firstSnapshot)
            assertEquals("GT-024", firstSnapshot?.tag)
            assertEquals("Nala", firstSnapshot?.name)
            assertEquals(32_450L, firstSnapshot?.latestWeightGrams)
            assertEquals(listOf(animalId), goatsB.searchGoats("Nala", 20).map { it.animalId })
            assertEquals(0L, databaseB.outbox().countUnacknowledgedForFarm(farmId))

            val secondWeightId = UUID.randomUUID().toString()
            goatsReopened.recordWeight(
                RecordGoatWeight(animalId, secondWeightId, 33_125L, 1_700_000_002_000L),
                context(tabletId, 1_700_000_002_000L),
            )
            LanSyncServer(tablet, { keys }).start(InetSocketAddress(loopback, 0)).use { server ->
                LanPeerTransport(
                    loopback.hostAddress ?: "127.0.0.1",
                    server.port,
                    farmId,
                    phoneId,
                    { keys },
                    phone::maySynchronise,
                ).use { transport ->
                    val second = SyncSession.run(phone, transport, remoteDeviceId = tabletId)
                    assertEquals(SyncSessionStatus.COMPLETED, second.status)
                    assertTrue(second.pulledOperations >= 1)
                }
            }
            assertEquals(33_125L, goatsB.getGoat(animalId)?.latestWeightGrams)
        } finally {
            reopenedA.close()
            databaseB.close()
        }
    }

    private fun openDatabase(name: String): FarmOsDatabase = Room.databaseBuilder(
        context,
        FarmOsDatabase::class.java,
        name,
    )
        .addMigrations(*FarmOsDatabase.ALL_MIGRATIONS)
        .build()

    private fun context(deviceId: String, occurredAt: Long) = LocalCommandContext(
        farmId = farmId,
        actorId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
        deviceId = deviceId,
        mutationId = UUID.randomUUID().toString(),
        occurredAtEpochMillis = occurredAt,
    )
}
