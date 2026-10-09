package com.farmos.app

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.farmos.core.database.AnimalEntity
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.ReplicationDeviceEntity
import com.farmos.core.database.toEnvelope
import com.farmos.domain.access.AccountStatus
import com.farmos.domain.access.Credential
import com.farmos.domain.access.CredentialHasher
import com.farmos.domain.access.CredentialKind
import com.farmos.domain.access.LocalAccount
import com.farmos.domain.access.LocalRole
import com.farmos.domain.access.SignInResult
import com.farmos.domain.replication.DeviceStatus
import com.farmos.domain.replication.LocalPeerTransport
import com.farmos.domain.replication.OperationBundle
import com.farmos.domain.replication.SyncSession
import java.io.Closeable
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse

/** Real Room accounts, two admitted farm devices and a synthetic PIN; never requires a vault or provider. */
internal class LocalSessionAuthorityFixture(private val context: Context) : Closeable {
    val farmId = UUID.randomUUID().toString()
    val deviceId = "session-device"
    private val peerId = "management-device"
    private val ownerId = "session-owner"
    val pin = "482913"
    val nextPin = "591047"
    private val now = AtomicLong(1_800_000_000_000L)
    private val hasher = CredentialHasher(iterations = 1_000)
    val database = Room.inMemoryDatabaseBuilder(context, FarmOsDatabase::class.java).build()
    val directory = LocalFarmDirectory(database, deviceId, hasher, clock = { now.incrementAndGet() })
    val account: LocalAccount
    private val endpoint: RoomReplicaEndpoint
    private var peerDatabase: FarmOsDatabase? = null
    private lateinit var peerDirectory: LocalFarmDirectory
    private lateinit var peerEndpoint: RoomReplicaEndpoint

    init {
        seedCommandAuthority(database, farmId, ownerId, deviceId, LocalRole.OWNER)
        account = runBlocking(Dispatchers.IO) {
            directory.transact {
                it.createAccount(
                    requireNotNull(directory.account(farmId, ownerId)), "session-manager", "Session manager",
                    LocalRole.MANAGER, Credential(CredentialKind.PIN, pin),
                )
            }.also {
                database.animals().insert(
                    AnimalEntity("session-goat", farmId, "SESSION-001", "Session goat", "goat", "FEMALE", "active", null, updatedAtEpochMillis = 1),
                )
            }
        }
        endpoint = RoomReplicaEndpoint(database, farmId, deviceId, replicationAppliers)
    }

    fun signIn(secret: String = pin): LocalAccount = runBlocking(Dispatchers.IO) {
        (directory.transact { it.signIn(farmId, account.username, secret) } as SignInResult.SignedIn).account
    }

    fun authority(authenticated: LocalAccount = signIn()) =
        LocalSessionAuthority(database, directory, authenticated, deviceId)

    fun currentAccount(): LocalAccount = runBlocking(Dispatchers.IO) {
        requireNotNull(directory.account(farmId, account.accountId))
    }

    fun changeRoleRemotely(role: LocalRole) = remoteAccess {
        it.changeRole(requireNotNull(peerDirectory.account(farmId, ownerId)), account.accountId, role)
    }

    fun resetCredentialRemotely() = remoteAccess {
        it.resetCredential(
            requireNotNull(peerDirectory.account(farmId, ownerId)), account.accountId,
            Credential(CredentialKind.PIN, nextPin),
        )
    }

    fun disableRemotely() = remoteAccess {
        it.setStatus(requireNotNull(peerDirectory.account(farmId, ownerId)), account.accountId, AccountStatus.DISABLED)
    }

    private fun remoteAccess(change: (com.farmos.domain.access.LocalAccessService) -> Unit): Unit = runBlocking(Dispatchers.IO) {
        ensurePeer()
        peerDirectory.transact(change)
        SyncSession.run(endpoint, LocalPeerTransport(peerEndpoint), remoteDeviceId = peerId)
        assertEquals(peerDirectory.account(farmId, account.accountId)?.role, directory.account(farmId, account.accountId)?.role)
        assertEquals(peerDirectory.account(farmId, account.accountId)?.credentialHash, directory.account(farmId, account.accountId)?.credentialHash)
        assertEquals(peerDirectory.account(farmId, account.accountId)?.status, directory.account(farmId, account.accountId)?.status)
    }

    fun revokeDeviceRemotely(): Unit = runBlocking(Dispatchers.IO) {
        ensurePeer()
        val remote = requireNotNull(peerDatabase)
        remote.setDeviceStatus(farmId, deviceId, DeviceStatus.LOST_REVOKED, ownerId, peerId, now.incrementAndGet())
        // A received revocation is admitted through the normal journal endpoint. The revoked device
        // cannot request a new peer session; an already delivered receipt must still terminate its UI.
        val operation = remote.replication().operationsForEntity(farmId, "farm_device", deviceId)
            .single { it.operationType == DEVICE_STATUS_COMMAND }
        assertFalse(endpoint.ingest(OperationBundle.seal(farmId, peerId, listOf(operation.toEnvelope()))).rejected)
        assertEquals(DeviceStatus.LOST_REVOKED.name, database.replication().device(farmId, deviceId)?.status)
    }

    fun updateDevice(transform: (ReplicationDeviceEntity) -> ReplicationDeviceEntity): Unit = runBlocking(Dispatchers.IO) {
        database.withTransaction {
            val previous = requireNotNull(database.replication().device(farmId, deviceId))
            database.replication().upsertDevice(transform(previous))
        }
    }

    private fun ensurePeer() {
        if (peerDatabase != null) return
        val peer = Room.inMemoryDatabaseBuilder(context, FarmOsDatabase::class.java).build()
        peerDatabase = peer
        seedCommandAuthority(peer, farmId, ownerId, peerId, LocalRole.OWNER)
        peerDirectory = LocalFarmDirectory(peer, peerId, hasher, clock = { now.incrementAndGet() })
        peerEndpoint = RoomReplicaEndpoint(peer, farmId, peerId, replicationAppliers)
        peerEndpoint.registerPairedDevice(deviceId, deviceId)
        endpoint.registerPairedDevice(peerId, peerId)
        SyncSession.run(peerEndpoint, LocalPeerTransport(endpoint), remoteDeviceId = deviceId)
        requireNotNull(peerDirectory.account(farmId, account.accountId))
    }

    override fun close() {
        peerDatabase?.close()
        database.close()
    }
}
