package com.farmos.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.FarmOsDatabase
import com.farmos.domain.access.Credential
import com.farmos.domain.access.CredentialHasher
import com.farmos.domain.access.CredentialKind
import com.farmos.domain.access.LocalAccount
import com.farmos.domain.access.LocalRole
import com.farmos.domain.access.SignInResult
import com.farmos.domain.replication.DeviceKeys
import com.farmos.domain.replication.DeviceRegistry
import com.farmos.domain.replication.DeviceStatus
import com.farmos.domain.replication.EnrolmentDecision
import com.farmos.domain.replication.EnrolmentOutcome
import com.farmos.domain.replication.EnrolmentRequest
import com.farmos.domain.replication.LanPeerTransport
import com.farmos.domain.replication.LanSyncServer
import com.farmos.domain.replication.PairingAuthority
import com.farmos.domain.replication.PairingClient
import com.farmos.domain.replication.PairingCode
import com.farmos.domain.replication.PairingServer
import com.farmos.domain.replication.SyncSession
import com.farmos.domain.replication.SyncSessionStatus
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.Files
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * A new device joins a farm end to end over real sockets: it asks to enrol, the owner types the code it
 * shows, it installs the grant, synchronises the farm journal over the encrypted LAN transport, and a
 * worker signs in on it. Once revoked it can no longer synchronise.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-rUS")
class DevicePairingTest {
    private val loopback = InetAddress.getLoopbackAddress()
    private val closeables = mutableListOf<java.io.Closeable>()
    private val databases = mutableListOf<FarmOsDatabase>()
    private val folders = mutableListOf<File>()

    private class SoftwareSealer : DeviceSealer {
        private val key = ByteArray(32).also(SecureRandom()::nextBytes)

        override fun seal(plaintext: ByteArray): ByteArray {
            val iv = ByteArray(12).also(SecureRandom()::nextBytes)
            return iv + Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv)) }.doFinal(plaintext)
        }

        override fun open(sealed: ByteArray): ByteArray =
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, sealed.copyOfRange(0, 12))) }
                .doFinal(sealed.copyOfRange(12, sealed.size))
    }

    private fun database() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FarmOsDatabase::class.java)
        .allowMainThreadQueries()
        .build()
        .also { databases += it }

    private fun vault() = FarmKeyVault(Files.createTempDirectory("vault").toFile().also { folders += it }, SoftwareSealer())

    @After
    fun tearDown() {
        closeables.reversed().forEach { it.close() }
        databases.forEach { it.close() }
        folders.forEach { it.deleteRecursively() }
    }

    @Test
    fun aNewDevicePairsSynchronisesTheFarmAndAWorkerSignsInThereUntilItIsRevoked() = runBlocking {
        // The owner's tablet created the farm and a worker account.
        val tabletDb = database()
        val tabletVault = vault()
        val tabletDirectory = LocalFarmDirectory(tabletDb, "tablet", CredentialHasher(iterations = 1_000))
        lateinit var owner: LocalAccount
        val farmId = tabletDirectory.createFarm("Premier Farm") { id ->
            owner = tabletDirectory.access.setUpFarm(id, "tendai", "Tendai Moyo", Credential(CredentialKind.PIN, "482913")).owner
        }.farmId
        tabletDirectory.transact { it.createAccount(owner, "rudo", "Rudo Chari", LocalRole.WORKER, Credential(CredentialKind.PIN, "730418")) }
        val tabletKeys = tabletVault.secretsForLocalFarm(farmId).keys

        // The phone asks to join, showing a code; the owner types it on the tablet.
        val phoneDb = database()
        val phoneVault = vault()
        val identity = phoneVault.joiningIdentity(farmId)
        val fingerprint = DeviceKeys.farmPairingFingerprint(farmId)
        val request = EnrolmentRequest(
            UUID.randomUUID().toString(), farmId, "phone", "Worker phone", DeviceKeys.encode(identity.public),
            ByteArray(16).also(SecureRandom()::nextBytes), System.currentTimeMillis(),
        )
        val codeOnPhone = PairingCode.of(fingerprint, request.devicePublicKey, request.nonce)
        val authority = PairingAuthority(farmId, fingerprint, DeviceRegistry(tabletDb.farmDevices(farmId)), System::currentTimeMillis)
        val pairing = PairingServer(
            authority,
            { tabletKeys },
            onGranted = { req, grant -> runBlocking { tabletDb.enrolPairedDevice(grant, req.devicePublicKey, "tablet") } },
            decide = { EnrolmentDecision.Approve(owner.accountId, approverMayApprove = true, codeShownOnNewDevice = codeOnPhone) },
        ).start(InetSocketAddress(loopback, 0)).also { closeables += it }
        val grant = (PairingClient.requestEnrolment(loopback.hostAddress, pairing.port, request) as EnrolmentOutcome.Granted).grant
        phoneDb.installGrant(grant, identity, "phone", phoneVault)
        assertEquals(DeviceStatus.ACTIVE.name, tabletDb.replication().device(farmId, "phone")?.status)

        // The phone synchronises the whole farm over the authenticated, encrypted LAN transport.
        val tablet = RoomReplicaEndpoint(tabletDb, farmId, "tablet", replicationAppliers)
        val phone = RoomReplicaEndpoint(phoneDb, farmId, "phone", replicationAppliers)
        val server = LanSyncServer(tablet, { tabletVault.secrets(farmId)!!.keys }).start(InetSocketAddress(loopback, 0)).also { closeables += it }
        val transport = LanPeerTransport(loopback.hostAddress, server.port, farmId, "phone", { phoneVault.secrets(farmId)!!.keys }, phone::maySynchronise)
            .also { closeables += it }
        assertEquals(SyncSessionStatus.COMPLETED, SyncSession.run(phone, transport, remoteDeviceId = "tablet").status)

        val phoneDirectory = LocalFarmDirectory(phoneDb, "phone", CredentialHasher(iterations = 1_000))
        assertEquals(listOf("Premier Farm"), phoneDirectory.farms().map { it.name })
        assertTrue(phoneDirectory.transact { it.signIn(farmId, "rudo", "730418") } is SignInResult.SignedIn)

        // Reported lost: the tablet refuses the phone from now on.
        tabletDb.setDeviceStatus(farmId, "phone", DeviceStatus.LOST_REVOKED, owner.accountId, "tablet")
        transport.close()
        val again = LanPeerTransport(loopback.hostAddress, server.port, farmId, "phone", { phoneVault.secrets(farmId)!!.keys }, phone::maySynchronise)
            .also { closeables += it }
        assertFalse(again.isAvailable())
    }
}
