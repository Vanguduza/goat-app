package com.farmos.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.ReplicationDeviceEntity
import com.farmos.domain.access.LocalRole
import com.farmos.domain.replication.DeviceKeys
import com.farmos.domain.replication.DeviceStatus
import com.farmos.domain.replication.FarmCipher
import com.farmos.domain.replication.FarmKeyUnavailable
import com.farmos.domain.replication.LanPeerTransport
import com.farmos.domain.replication.LanSyncServer
import com.farmos.domain.replication.SyncSession
import com.farmos.domain.replication.SyncSessionStatus
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Reporting a device lost rotates the farm key: remaining devices still holding the old key prove their
 * identity on the LAN, receive the new key wrapped to them alone and make it current; the lost device is
 * refused and cannot open anything sealed afterwards.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-rUS")
class KeyRotationTest {
    private val farm = UUID.randomUUID().toString()
    private val loopback = InetAddress.getLoopbackAddress()
    private val databases = mutableListOf<FarmOsDatabase>()
    private val folders = mutableListOf<File>()
    private val closeables = mutableListOf<java.io.Closeable>()

    private fun database() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FarmOsDatabase::class.java)
        .allowMainThreadQueries()
        .build()
        .also { databases += it }

    private fun vault() = testFarmKeyVault(Files.createTempDirectory("vault").toFile().also { folders += it }, TestSoftwareSealer())

    @After
    fun tearDown() {
        closeables.reversed().forEach { it.close() }
        databases.forEach { it.close() }
        folders.forEach { it.deleteRecursively() }
    }

    private fun b64(key: java.security.PublicKey) = Base64.getEncoder().encodeToString(DeviceKeys.encode(key))

    private suspend fun FarmOsDatabase.knows(deviceId: String, publicKey: java.security.PublicKey) =
        replication().upsertDevice(ReplicationDeviceEntity(farm, deviceId, deviceId, DeviceStatus.ACTIVE.name, 0, null, isLocal = false, publicKey = b64(publicKey)))

    @Test
    fun aLostDeviceIsCutOffWhileTheRemainingDevicesReceiveTheRotatedKey(): Unit = runBlocking {
        // Three paired devices sharing the first farm key, each with its own identity.
        val tabletDb = database()
        val laptopDb = database()
        seedCommandAuthority(tabletDb, farm, "owner-1", "tablet", LocalRole.OWNER)
        seedCommandAuthority(laptopDb, farm, "owner-1", "laptop", LocalRole.OWNER)
        val tabletVault = vault()
        val laptopVault = vault()
        val phoneVault = vault()
        val first = tabletVault.provisionNewFarm(farm).keys
        val laptopIdentity = DeviceKeys.generate()
        val phoneIdentity = DeviceKeys.generate()
        laptopVault.save(farm, FarmSecrets(first, laptopIdentity))
        phoneVault.save(farm, FarmSecrets(first, phoneIdentity))
        val tabletIdentity = tabletVault.secrets(farm)!!.device
        tabletDb.announceIdentity(farm, "tablet", tabletIdentity)
        tabletDb.knows("laptop", laptopIdentity.public)
        tabletDb.knows("phone", phoneIdentity.public)
        laptopDb.knows("tablet", tabletIdentity.public)
        laptopDb.knows("phone", phoneIdentity.public)

        // The phone is reported lost on the tablet; the farm key rotates.
        tabletDb.setDeviceStatus(farm, "phone", DeviceStatus.LOST_REVOKED, "owner-1", "tablet")
        tabletDb.rotateFarmKey(farm, "tablet", "owner-1", tabletVault)
        val rotated = tabletVault.secrets(farm)!!.keys
        assertNotEquals(first.currentKeyId, rotated.currentKeyId)

        val tablet = RoomReplicaEndpoint(tabletDb, farm, "tablet", farmAppliers(tabletVault, "tablet"))
        val server = LanSyncServer(tablet, { tabletVault.secrets(farm)!!.keys }, identity = farmIdentity(tabletDb, tabletVault, farm))
            .start(InetSocketAddress(loopback, 0)).also { closeables += it }

        // The laptop still holds only the first key; it proves it is the laptop and receives the new key.
        val laptop = RoomReplicaEndpoint(laptopDb, farm, "laptop", farmAppliers(laptopVault, "laptop"))
        val laptopTransport = LanPeerTransport(
            loopback.hostAddress!!, server.port, farm, "laptop", { laptopVault.secrets(farm)!!.keys }, laptop::maySynchronise,
            identity = farmIdentity(laptopDb, laptopVault, farm),
        ).also { closeables += it }
        assertEquals(SyncSessionStatus.COMPLETED, SyncSession.run(laptop, laptopTransport, remoteDeviceId = "tablet").status)
        val laptopKeys = laptopVault.secrets(farm)!!.keys
        assertEquals(rotated.currentKeyId, laptopKeys.currentKeyId)
        assertArrayEquals(rotated.current.materialForVault(), laptopKeys.current.materialForVault())

        // The lost phone is refused, and cannot open what is sealed under the new key.
        val phoneTransport = LanPeerTransport(
            loopback.hostAddress!!, server.port, farm, "phone", { phoneVault.secrets(farm)!!.keys }, { true },
            identity = com.farmos.domain.replication.LanIdentity(phoneIdentity) { null },
        ).also { closeables += it }
        assertFalse(phoneTransport.isAvailable())
        val sealed = FarmCipher.seal(rotated.current, "weights".toByteArray(), byteArrayOf(1))
        assertThrows(FarmKeyUnavailable::class.java) { FarmCipher.open(phoneVault.secrets(farm)!!.keys, sealed, byteArrayOf(1)) }
    }
}
