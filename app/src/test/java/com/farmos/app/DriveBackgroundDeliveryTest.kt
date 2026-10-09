package com.farmos.app

import android.app.Application
import android.content.Context
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.domain.access.AccountStatus
import com.farmos.domain.access.LocalRole
import com.farmos.domain.replication.DeviceStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-rUS", application = Application::class)
class DriveBackgroundDeliveryTest {
    private lateinit var fixture: DriveDeliveryTestFixture

    @Before
    fun setUp() {
        fixture = DriveDeliveryTestFixture(ApplicationProvider.getApplicationContext<Context>())
    }

    @After
    fun tearDown() {
        fixture.close()
    }

    @Test
    fun legacyAndForeignDeviceConnectionsCannotUseGoogleOrPublish(): Unit = runBlocking {
        val before = fixture.localOperationCount()
        val approved = requireNotNull(fixture.store.get(fixture.farmId))
        for (config in listOf(
            fixture.config(),
            approved.copy(approvedDeviceId = "another-device"),
            approved.copy(approvedByAccountId = "another-farm-account"),
        )) {
            fixture.store.save(fixture.farmId, config)
            assertEquals(DriveDeliveryStatus.APPROVAL_REQUIRED, fixture.runtime().synchroniseOnce())
        }
        assertEquals(0, fixture.authorizer.calls.get())
        assertEquals(0, fixture.carrier.calls.get())
        assertEquals(before, fixture.localOperationCount())
    }

    @Test
    fun disabledOrDemotedApproverCannotKeepBackingUp(): Unit = runBlocking {
        val dao = fixture.database.localAccess()
        val owner = requireNotNull(dao.account(fixture.farmId, fixture.owner.accountId))
        for (invalid in listOf(owner.copy(status = AccountStatus.DISABLED.name), owner.copy(role = LocalRole.WORKER.name))) {
            fixture.database.withTransaction { dao.upsertAccount(invalid) }
            assertEquals(DriveDeliveryStatus.APPROVAL_REQUIRED, fixture.runtime().synchroniseOnce())
        }
        assertEquals(0, fixture.authorizer.calls.get())
        assertEquals(0, fixture.carrier.writes.get())
    }

    @Test
    fun restoredRoleStillRequiresExplicitApprovalAfterTheGatewayWasBlocked(): Unit = runBlocking {
        val dao = fixture.database.localAccess()
        val owner = requireNotNull(dao.account(fixture.farmId, fixture.owner.accountId))
        fixture.database.withTransaction { dao.upsertAccount(owner.copy(role = LocalRole.WORKER.name)) }
        assertEquals(DriveDeliveryStatus.APPROVAL_REQUIRED, fixture.runtime().synchroniseOnce())

        fixture.database.withTransaction { dao.upsertAccount(owner) }
        assertEquals(DriveDeliveryStatus.APPROVAL_REQUIRED, fixture.runtime(DriveFarmCoordinator()).synchroniseOnce())
        assertEquals(0, fixture.authorizer.calls.get())
        assertEquals(0, fixture.carrier.calls.get())

        fixture.approve()
        assertEquals(DriveDeliveryStatus.COMPLETED, fixture.runtime().synchroniseOnce())
    }

    @Test
    fun revokedOrNonlocalGatewayCannotSynchronise(): Unit = runBlocking {
        val dao = fixture.database.replication()
        val device = requireNotNull(dao.device(fixture.farmId, fixture.deviceId))
        for (invalid in listOf(device.copy(status = DeviceStatus.LOST_REVOKED.name), device.copy(isLocal = false), device.copy(revokedAfterSequence = 0L))) {
            fixture.database.withTransaction { dao.upsertDevice(invalid) }
            assertEquals(DriveDeliveryStatus.APPROVAL_REQUIRED, fixture.runtime().synchroniseOnce())
        }
        assertEquals(0, fixture.authorizer.calls.get())
        assertEquals(0, fixture.carrier.calls.get())
    }

    @Test
    fun missingVaultDoesNotProvisionKeysOrAlterCommittedRecords(): Unit = runBlocking {
        val before = fixture.localOperationCount()
        fixture.vaultDirectory.deleteRecursively()
        assertEquals(DriveDeliveryStatus.KEYS_UNAVAILABLE, fixture.runtime().synchroniseOnce())
        assertFalse(fixture.vaultDirectory.exists())
        assertEquals(0, fixture.authorizer.calls.get())
        assertEquals(before, fixture.localOperationCount())
        assertEquals(DriveDeliveryStatus.KEYS_UNAVAILABLE, fixture.runtime(DriveFarmCoordinator()).state.value.deliveryStatus)
    }

    @Test
    fun consentNeededSurvivesRecreationAndDoesNotRetryUntilExplicitApproval(): Unit = runBlocking {
        fixture.authorizer.token = null
        assertEquals(DriveDeliveryStatus.NEEDS_CONSENT, fixture.runtime().synchroniseOnce())
        fixture.authorizer.token = "test-only-bearer"
        val recreated = fixture.runtime(DriveFarmCoordinator())
        assertTrue(recreated.state.value.authNeeded)
        assertEquals(DriveDeliveryStatus.NEEDS_CONSENT, recreated.synchroniseOnce())
        assertEquals(1, fixture.authorizer.calls.get())
        assertEquals(0, fixture.carrier.calls.get())

        fixture.approve()
        assertEquals(DriveDeliveryStatus.COMPLETED, recreated.synchroniseOnce())
        val preferences = fixture.context.getSharedPreferences("farm_drive", Context.MODE_PRIVATE).all.values
        assertFalse(preferences.any { it == "test-only-bearer" || it == "482913" })
    }

    @Test
    fun offlineCommittedJournalIsDeliveredByARecreatedRuntimeWhenOnline(): Unit = runBlocking {
        val before = fixture.localOperationCount()
        fixture.online = false
        assertEquals(DriveDeliveryStatus.OFFLINE, fixture.runtime().synchroniseOnce())
        assertEquals(0, fixture.authorizer.calls.get())
        assertEquals(0, fixture.carrier.calls.get())
        assertEquals(before, fixture.localOperationCount())

        fixture.online = true
        val restarted = fixture.runtime(DriveFarmCoordinator())
        assertEquals(DriveDeliveryStatus.OFFLINE, restarted.state.value.deliveryStatus)
        assertEquals(DriveDeliveryStatus.COMPLETED, restarted.synchroniseOnce())
        assertEquals(before, restarted.stateCounts().backedUp)
        assertEquals(before, fixture.localOperationCount())
        assertTrue(fixture.carrier.objects.isNotEmpty())
    }

    @Test
    fun revocationDuringTheFirstRemoteReadStopsBeforePublishing(): Unit = runBlocking {
        val before = fixture.localOperationCount()
        fixture.carrier.onList = {
            fixture.carrier.onList = null
            fixture.database.withTransaction {
                val dao = fixture.database.replication()
                val device = requireNotNull(dao.device(fixture.farmId, fixture.deviceId))
                dao.upsertDevice(device.copy(status = DeviceStatus.LOST_REVOKED.name))
            }
        }
        assertEquals(DriveDeliveryStatus.APPROVAL_REQUIRED, fixture.runtime().synchroniseOnce())
        assertEquals(0, fixture.carrier.writes.get())
        assertEquals(before, fixture.localOperationCount())
    }

    @Test
    fun removedConnectionStopsAnAlreadyStartedPassWithoutDeletingLocalWork(): Unit = runBlocking {
        val before = fixture.localOperationCount()
        fixture.carrier.onList = {
            fixture.carrier.onList = null
            fixture.store.clear(fixture.farmId)
        }
        assertEquals(DriveDeliveryStatus.APPROVAL_REQUIRED, fixture.runtime().synchroniseOnce())
        assertEquals(0, fixture.carrier.writes.get())
        assertEquals(before, fixture.localOperationCount())
        assertEquals(null, fixture.store.get(fixture.farmId))
    }
    @Test
    fun revocationAfterEncryptionChecksForExistingBytesStillPreventsTheWrite(): Unit = runBlocking {
        fixture.carrier.onRead = {
            fixture.carrier.onRead = null
            fixture.database.withTransaction {
                val dao = fixture.database.replication()
                val device = requireNotNull(dao.device(fixture.farmId, fixture.deviceId))
                dao.upsertDevice(device.copy(status = DeviceStatus.LOST_REVOKED.name))
            }
        }
        assertEquals(DriveDeliveryStatus.APPROVAL_REQUIRED, fixture.runtime().synchroniseOnce())
        assertEquals(0, fixture.carrier.writes.get())
        assertTrue(fixture.carrier.objects.isEmpty())
    }

}
