package com.farmos.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.ReplicationDeviceEntity
import com.farmos.domain.access.AccountStatus
import com.farmos.domain.access.Credential
import com.farmos.domain.access.CredentialHasher
import com.farmos.domain.access.CredentialKind
import com.farmos.domain.access.LocalAccount
import com.farmos.domain.access.LocalRole
import com.farmos.domain.access.SignInResult
import com.farmos.domain.replication.LocalPeerTransport
import com.farmos.domain.replication.SyncSession
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The farm's local accounts replicate like farm records: a paired device learns the farm, its accounts
 * and access history, a worker can sign in there, and concurrent changes to different fields of one
 * account both survive on every device.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-rUS")
class AccessReplicationTest {
    private val databases = mutableListOf<FarmOsDatabase>()
    private var now = 1_790_000_000_000L

    private fun database() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FarmOsDatabase::class.java)
        .allowMainThreadQueries()
        .build()
        .also { databases += it }

    private fun directory(db: FarmOsDatabase, device: String) = LocalFarmDirectory(db, device, CredentialHasher(iterations = 1_000), clock = { now }, initialKeys = localAccessTestVault())

    @After
    fun tearDown() = databases.forEach { it.close() }

    private class Device(val db: FarmOsDatabase, val directory: LocalFarmDirectory, val endpoint: RoomReplicaEndpoint)

    private fun device(id: String, farmId: String, vararg peers: String): Device {
        val db = database()
        // Authenticated pairing installs this local row before the received accounts can sign in.
        db.replicationBlocking().upsertDevice(ReplicationDeviceEntity(farmId, id, id, "ACTIVE", 0L, null, true))
        return Device(db, directory(db, id), RoomReplicaEndpoint(db, farmId, id, replicationAppliers).apply { peers.forEach { registerPairedDevice(it, it) } })
    }

    private fun farmOnTablet(): Pair<String, LocalAccount> = runBlocking {
        val db = database()
        val directory = directory(db, "tablet")
        lateinit var owner: LocalAccount
        val farm = directory.createFarm("Premier Farm") { farmId ->
            owner = directory.access.setUpFarm(farmId, "tendai", "Tendai Moyo", Credential(CredentialKind.PIN, "482913")).owner
        }
        tabletDb = db
        tabletDirectory = directory
        farm.farmId to owner
    }

    private lateinit var tabletDb: FarmOsDatabase
    private lateinit var tabletDirectory: LocalFarmDirectory

    @Test
    fun aPairedDeviceLearnsTheFarmItsAccountsAndHistoryAndAWorkerCanSignInThere() = runBlocking {
        val (farmId, owner) = farmOnTablet()
        now += 1_000
        val rudo = tabletDirectory.transact { it.createAccount(owner, "rudo", "Rudo Chari", LocalRole.WORKER, Credential(CredentialKind.PIN, "730418")) }
        val tablet = RoomReplicaEndpoint(tabletDb, farmId, "tablet", replicationAppliers).apply { registerPairedDevice("phone", "Phone") }
        val phone = device("phone", farmId, "tablet")

        SyncSession.run(phone.endpoint, LocalPeerTransport(tablet), remoteDeviceId = "tablet")

        assertEquals(listOf("Premier Farm"), phone.directory.farms().map { it.name })
        assertEquals(setOf("tendai", "rudo"), phone.directory.accounts(farmId).map { it.username }.toSet())
        assertTrue(phone.directory.transact { it.signIn(farmId, "rudo", "730418") } is SignInResult.SignedIn)
        assertEquals(tabletDb.localAccess().recoveryHash(farmId), phone.db.localAccess().recoveryHash(farmId))
        assertTrue(phone.db.localAccess().audit(farmId, 50).map { it.eventId }.containsAll(tabletDb.localAccess().audit(farmId, 50).map { it.eventId }))
        assertEquals(rudo.credentialHash, phone.directory.account(farmId, rudo.accountId)?.credentialHash)
    }

    @Test
    fun concurrentChangesToDifferentFieldsOfOneAccountBothSurviveOnEveryDevice() = runBlocking {
        val (farmId, owner) = farmOnTablet()
        now += 1_000
        val rudo = tabletDirectory.transact { it.createAccount(owner, "rudo", "Rudo Chari", LocalRole.WORKER, Credential(CredentialKind.PIN, "730418")) }
        val tablet = RoomReplicaEndpoint(tabletDb, farmId, "tablet", replicationAppliers).apply { registerPairedDevice("phone", "Phone") }
        val phone = device("phone", farmId, "tablet")
        SyncSession.run(phone.endpoint, LocalPeerTransport(tablet), remoteDeviceId = "tablet")

        // Offline from each other: the tablet disables Rudo, the phone promotes Rudo a moment later.
        now += 1_000
        tabletDirectory.transact { it.setStatus(owner, rudo.accountId, AccountStatus.DISABLED) }
        now += 1_000
        val ownerOnPhone = phone.directory.account(farmId, owner.accountId)!!
        phone.directory.transact { it.changeRole(ownerOnPhone, rudo.accountId, LocalRole.SUPERVISOR) }

        SyncSession.run(phone.endpoint, LocalPeerTransport(tablet), remoteDeviceId = "tablet")

        listOf(tabletDirectory, phone.directory).forEach { directory ->
            val merged = directory.account(farmId, rudo.accountId)!!
            assertEquals(AccountStatus.DISABLED, merged.status)
            assertEquals(LocalRole.SUPERVISOR, merged.role)
        }
        assertEquals(SignInResult.Disabled, phone.directory.transact { it.signIn(farmId, "rudo", "730418") })
    }
}
