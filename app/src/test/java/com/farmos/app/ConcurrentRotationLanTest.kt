package com.farmos.app

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.ops.RecordMoney
import com.farmos.domain.replication.LanPeerTransport
import com.farmos.domain.replication.LanSyncServer
import com.farmos.domain.replication.SyncSession
import com.farmos.domain.replication.SyncSessionStatus
import java.io.Closeable
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Concurrent committed rotations must reach each other's real Room appliers through the LAN carrier. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-rUS", application = Application::class)
class ConcurrentRotationLanTest {
    private val fixtures = mutableListOf<KeyRotationTestFixture>()
    private val connections = mutableListOf<Closeable>()

    @After
    fun close() {
        connections.reversed().forEach { it.close() }
        fixtures.forEach { it.close() }
    }

    @Test
    fun twoIndependentRotationsConvergeAndExchangeFarmRecordsOverRealSockets(): Unit = runBlocking {
        val farm = UUID.randomUUID().toString()
        val a = KeyRotationTestFixture(farm, "A").also { fixtures += it }
        val b = KeyRotationTestFixture(farm, "B").also { fixtures += it }
        val initial = requireNotNull(a.vault.secrets(farm))
        val bIdentity = requireNotNull(b.vault.secrets(farm)).device
        b.vault.save(farm, FarmSecrets(initial.keys, bIdentity))
        a.knows(b)
        b.knows(a)
        val at = 1_800_000_000_100L
        a.rotate(at)
        b.rotate(at)
        val aId = requireNotNull(a.vault.secrets(farm)).keys.currentKeyId
        val bId = requireNotNull(b.vault.secrets(farm)).keys.currentKeyId
        assertNotEquals(aId, bId)
        assertNull(a.vault.secrets(farm)?.keys?.key(bId))
        assertNull(b.vault.secrets(farm)?.keys?.key(aId))

        for (f in listOf(a, b)) {
            RoomOpsRepository(f.database, farm).recordMoney(
                RecordMoney("money-" + f.device, "expense", "feed", 1_000L, "USD", 20_700L),
                LocalCommandContext(farm, "owner", f.device, UUID.randomUUID().toString(), at + 1L),
            )
        }
        val total = a.database.replication().count(farm) + b.database.replication().count(farm)
        val aEndpoint = RoomReplicaEndpoint(a.database, farm, "A", farmAppliers(a.vault, "A"))
        val bEndpoint = RoomReplicaEndpoint(b.database, farm, "B", farmAppliers(b.vault, "B"))
        val loopback = InetAddress.getLoopbackAddress()
        val server = LanSyncServer(bEndpoint, { requireNotNull(b.vault.secrets(farm)).keys },
            identity = farmIdentity(b.database, b.vault, farm))
            .start(InetSocketAddress(loopback, 0)).also { connections += it }
        val transport = LanPeerTransport(loopback.hostAddress, server.port, farm, "A",
            { requireNotNull(a.vault.secrets(farm)).keys }, aEndpoint::maySynchronise,
            identity = farmIdentity(a.database, a.vault, farm)).also { connections += it }

        val outcome = SyncSession.run(aEndpoint, transport, remoteDeviceId = "B")
        assertEquals(SyncSessionStatus.COMPLETED, outcome.status)
        assertEquals(total, a.database.replication().count(farm))
        assertEquals(total, b.database.replication().count(farm))
        assertEquals(a.database.money().recent(farm, 10).associateBy { it.id }, b.database.money().recent(farm, 10).associateBy { it.id })
        assertEquals(2, a.database.money().recent(farm, 10).size)
        val aKeys = requireNotNull(a.vault.secrets(farm)).keys
        val bKeys = requireNotNull(b.vault.secrets(farm)).keys
        assertEquals(maxOf(aId, bId), aKeys.currentKeyId)
        assertEquals(aKeys.currentKeyId, bKeys.currentKeyId)
        assertEquals(setOf("k1", aId, bId), aKeys.keyIds)
        assertEquals(aKeys.keyIds, bKeys.keyIds)
        assertArrayEquals(aKeys.current.materialForVault(), bKeys.current.materialForVault())
        assertArrayEquals(initial.keys.current.materialForVault(), aKeys.key("k1")!!.materialForVault())
    }
}
