package com.farmos.app

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.ApplicationState
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.ReplicationApplicationEntity
import com.farmos.core.database.ReplicationDeviceEntity
import com.farmos.core.database.journalLocalOperation
import com.farmos.core.model.LocalCommandContext
import com.farmos.domain.access.AccessDenied
import com.farmos.domain.access.LocalRole
import com.farmos.domain.ops.GestationPeriod
import com.farmos.domain.ops.GestationSpecies
import com.farmos.domain.replication.DeviceGrant
import com.farmos.domain.replication.DeviceKeys
import com.farmos.domain.replication.DeviceStatus
import com.farmos.domain.replication.FarmDataKey
import com.farmos.domain.replication.FarmKeyWrap
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Direct command calls must enforce the same current authority as their management screens. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-rUS", application = Application::class)
class AppManagementAuthorityTest {
    private val farm = UUID.randomUUID().toString()
    private val device = "local-tablet"
    private val pending = "received-operation"
    private val candidate = DeviceKeys.generate()
    private lateinit var database: FarmOsDatabase

    @Before
    fun setUp(): Unit = runBlocking {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FarmOsDatabase::class.java)
            .allowMainThreadQueries().build()
        seedCommandAuthority(database, farm, "owner", device, LocalRole.OWNER)
        seedCommandAuthority(database, farm, "manager", device, LocalRole.MANAGER)
        seedCommandAuthority(database, farm, "worker", device, LocalRole.WORKER)
        database.replication().upsertDevice(ReplicationDeviceEntity(farm, "phone", "Phone", "ACTIVE", 0, null, false))
        database.withTransaction {
            // A retained received change with no app applier, awaiting an explicit management decision.
            database.journalLocalOperation(pending, farm, "test_record", "test-1", "peer-actor", "peer",
                1L, 1L, null, "test.unsupported.v1", "{}", 1)
            val peer = requireNotNull(database.replication().device(farm, "peer"))
            database.replication().upsertDevice(peer.copy(isLocal = false))
            database.replicationApplications().upsert(
                ReplicationApplicationEntity(pending, farm, ApplicationState.AWAITING_APPLIER.name, "No applier", 0, 1L),
            )
        }
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun lowerRolesCannotChangeSettingsReviewOrDeviceMembership(): Unit = runBlocking {
        for (role in listOf(LocalRole.SUPERVISOR, LocalRole.WORKER, LocalRole.VIEWER)) {
            val actor = requireNotNull(database.localAccess().account(farm, "worker"))
            database.withTransaction { database.localAccess().upsertAccount(actor.copy(role = role.name)) }
            assertEveryCommandDenied("worker", device)
        }
    }

    @Test
    fun disabledMissingOrForeignFarmAccountsCannotUseAnEarlierPermission(): Unit = runBlocking {
        val owner = requireNotNull(database.localAccess().account(farm, "owner"))
        database.withTransaction { database.localAccess().upsertAccount(owner.copy(status = "DISABLED")) }
        assertEveryCommandDenied("owner", device)
        assertEveryCommandDenied("missing", device)
        seedCommandAuthority(database, "other-farm", "other-owner", "other-device", LocalRole.OWNER)
        assertEveryCommandDenied("other-owner", device)
    }

    @Test
    fun missingNonlocalAndRevokedOriginsAreRejectedWithoutJournalOrStateChanges(): Unit = runBlocking {
        assertEveryCommandDenied("owner", "unknown")
        assertEveryCommandDenied("owner", "phone")
        val local = requireNotNull(database.replication().device(farm, device))
        for (invalid in listOf(local.copy(status = DeviceStatus.LOST_REVOKED.name), local.copy(revokedAfterSequence = 0L))) {
            database.withTransaction { database.replication().upsertDevice(invalid) }
            assertEveryCommandDenied("owner", device)
        }
    }

    @Test
    fun ownerCurrencyAndManagerSettingsReviewAndPairingCommitWithTheirJournal(): Unit = runBlocking {
        denied { database.setFarmCurrency(farm, "ZAR", "manager", device, 10L) }
        val before = database.replication().count(farm)
        database.setFarmCurrency(farm, "ZAR", "owner", device, 10L)
        database.setFarmGestation(farm, GestationSpecies.GOAT, GestationPeriod(146, 151, 156), "manager", device, 11L)
        database.setAsideReceivedOperation(farm, pending, "Reviewed and retained as history", context("manager", device))
        database.enrolPairedDevice(grant("manager"), DeviceKeys.encode(candidate.public), device, 13L)
        database.setDeviceStatus(farm, "phone", DeviceStatus.RETIRED, "manager", device, 14L)

        assertEquals("ZAR", database.farmCurrency(farm))
        assertEquals(151, database.gestationPeriod(farm, GestationSpecies.GOAT).typicalDays)
        assertEquals(ApplicationState.SET_ASIDE.name, database.replicationApplications().get(farm, pending)?.state)
        assertNotNull(database.replication().operation(farm, pending))
        assertEquals(false, database.replication().device(farm, "candidate")?.isLocal)
        assertEquals(DeviceStatus.RETIRED.name, database.replication().device(farm, "phone")?.status)
        assertEquals(before + 5, database.replication().count(farm))
    }

    @Test
    fun retiringAnAlreadyLostDeviceNeverDowngradesItsRevocation(): Unit = runBlocking {
        database.setDeviceStatus(farm, "phone", DeviceStatus.LOST_REVOKED, "owner", device, 10L)
        val before = database.replication().count(farm)
        val revoked = requireNotNull(database.replication().device(farm, "phone"))
        database.setDeviceStatus(farm, "phone", DeviceStatus.RETIRED, "owner", device, 11L)
        assertEquals(revoked, database.replication().device(farm, "phone"))
        assertEquals(before, database.replication().count(farm))
    }

    private suspend fun assertEveryCommandDenied(actor: String, origin: String) {
        val before = snapshot()
        for (action in listOf<suspend () -> Unit>(
            { database.setFarmCurrency(farm, "ZAR", actor, origin, 10L) },
            { database.setFarmGestation(farm, GestationSpecies.GOAT, GestationPeriod(146, 151, 156), actor, origin, 11L) },
            { database.setAsideReceivedOperation(farm, pending, "Reviewed", context(actor, origin)) },
            { database.enrolPairedDevice(grant(actor), DeviceKeys.encode(candidate.public), origin, 13L) },
            { database.setDeviceStatus(farm, "phone", DeviceStatus.LOST_REVOKED, actor, origin, 14L) },
        )) {
            denied(action)
            assertEquals(before, snapshot())
        }
    }

    private suspend fun snapshot(): List<Any?> = listOf(
        database.replication().count(farm), database.replication().devices(farm),
        database.farmSettings().get(farm), database.farmGestation().all(farm),
        database.replicationApplications().get(farm, pending),
    )

    private suspend fun denied(action: suspend () -> Unit) {
        try {
            action()
            fail("Current local authority must be required")
        } catch (_: AccessDenied) {
            // The production admission boundary refused the command before mutation.
        }
    }

    private fun context(actor: String, origin: String) =
        LocalCommandContext(farm, actor, origin, UUID.randomUUID().toString(), 12L)

    private fun grant(actor: String): DeviceGrant {
        val key = FarmDataKey.generate("grant-test-key")
        return DeviceGrant(farm, "candidate", "New phone", actor, 13L, key.keyId,
            listOf(FarmKeyWrap.wrap(key, candidate.public, farm, "candidate")), null)
    }
}
