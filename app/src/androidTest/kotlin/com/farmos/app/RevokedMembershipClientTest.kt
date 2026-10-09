package com.farmos.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.goat.RoomGoatRepository
import com.farmos.domain.goat.GoatSex
import com.farmos.domain.goat.RegisterGoat
import com.farmos.domain.replication.DeviceStatus
import com.farmos.domain.replication.LocalPeerTransport
import com.farmos.domain.replication.SyncSession
import com.farmos.domain.replication.SyncSessionStatus
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A lost or revoked farm device cannot synchronise. The goat already saved on this device stays;
 * transport refusal never deletes local records.
 */
@RunWith(AndroidJUnit4::class)
class RevokedMembershipClientTest {
    private lateinit var context: Context
    private val tabletDbName = "farm-os-e2e-revoked-tablet.db"
    private val phoneDbName = "farm-os-e2e-revoked-phone.db"
    private val farmId = "11111111-1111-4111-8111-111111111111"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(tabletDbName)
        context.deleteDatabase(phoneDbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(tabletDbName)
        context.deleteDatabase(phoneDbName)
    }

    @Test
    fun revokedMembershipStopsSyncAndClearsRememberedFarmAuthority() = runBlocking {
        val tabletDb = open(tabletDbName)
        val phoneDb = open(phoneDbName)
        try {
            seedAndroidCommandWorker(tabletDb, farmId, "worker-1", "tablet")
            val goats = RoomGoatRepository(tabletDb, farmId, localDeviceId = "tablet")
            val animalId = UUID.randomUUID().toString()
            goats.registerGoat(
                RegisterGoat(animalId, "REV-E2E-01", "Nala", GoatSex.FEMALE, dateOfBirthEpochDay = 20_150),
                LocalCommandContext(farmId, "worker-1", "tablet", UUID.randomUUID().toString(), 1_700_000_000_000L),
            )
            val tablet = RoomReplicaEndpoint(tabletDb, farmId, "tablet", replicationAppliers).apply {
                registerPairedDevice("phone", "Phone")
            }
            val phone = RoomReplicaEndpoint(phoneDb, farmId, "phone", replicationAppliers).apply {
                registerPairedDevice("tablet", "Tablet")
            }
            val tabletRow = phoneDb.replication().device(farmId, "tablet")!!
            phoneDb.replication().upsertDevice(
                tabletRow.copy(status = DeviceStatus.LOST_REVOKED.name, revokedAfterSequence = 0),
            )
            assertEquals(false, phone.maySynchronise("tablet"))
            val outcome = SyncSession.run(phone, LocalPeerTransport(tablet), remoteDeviceId = "tablet")
            assertEquals(SyncSessionStatus.PEER_NOT_AUTHORISED, outcome.status)
            assertEquals(0, outcome.pulledOperations)
            assertTrue(RoomGoatRepository(phoneDb, farmId).getGoat(animalId) == null)
            assertNotNull(goats.getGoat(animalId))
            assertEquals("Nala", goats.getGoat(animalId)?.name)
        } finally {
            tabletDb.close()
            phoneDb.close()
        }
    }

    private fun open(name: String): FarmOsDatabase = Room.databaseBuilder(
        context,
        FarmOsDatabase::class.java,
        name,
    )
        .addMigrations(*FarmOsDatabase.ALL_MIGRATIONS)
        .build()
}
