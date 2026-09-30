package com.farmos.domain.replication

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.crypto.AEADBadTagException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The farm-LAN transport over real loopback sockets: two farm devices converge through the same sync
 * session every transport uses; devices without the farm key, revoked devices and altered or replayed
 * frames are refused and change nothing.
 */
class LanTransportTest {
    private val farm = "farm-premier"
    private var nextId = 0
    private val keys = FarmKeyRing(listOf(FarmDataKey.generate("k1")), "k1")
    private val loopback = InetAddress.getLoopbackAddress()
    private val servers = mutableListOf<LanSyncServer>()
    private val transports = mutableListOf<LanPeerTransport>()

    @AfterTest
    fun tearDown() {
        transports.forEach { it.close() }
        servers.forEach { it.close() }
    }

    private fun registry() = DeviceRegistry(listOf("A", "B", "C").map { FarmDevice(it, "Device $it", DeviceStatus.ACTIVE) })

    private fun replica(device: String, registry: DeviceRegistry = registry(), farmId: String = farm) =
        FarmReplica(farmId, device, registry, { "op-$device-${++nextId}" }, { 1_000L })

    private fun FarmReplica.weigh(animal: String, kg: String) = record(
        entityType = "animal",
        entityId = animal,
        actorId = "worker-$deviceId",
        businessTimeEpochMillis = 5_000L + nextId,
        baseVersion = null,
        operationType = "weight.record",
        mergeClass = MergeClass.APPEND_ONLY_EVENT,
        payload = mapOf("kg" to kg, "note" to "x".repeat(70_000)),
    )

    private fun serve(replica: FarmReplica, ring: FarmKeyRing = keys) =
        LanSyncServer(replica, { ring }).start(InetSocketAddress(loopback, 0)).also { servers += it }

    private fun transport(client: FarmReplica, server: LanSyncServer, ring: FarmKeyRing = keys) =
        LanPeerTransport(loopback.hostAddress, server.port, client.farmId, client.deviceId, { ring }, client.registry::maySynchronise).also { transports += it }

    @Test
    fun twoFarmDevicesConvergeOverTheLanWithLargePayloads() {
        val a = replica("A")
        val b = replica("B")
        repeat(3) { a.weigh("goat-$it", "3$it.5") }
        repeat(2) { b.weigh("rabbit-$it", "2.$it") }
        val server = serve(b)

        val outcome = SyncSession.run(a, transport(a, server), remoteDeviceId = "B")

        assertEquals(SyncSessionStatus.COMPLETED, outcome.status)
        assertEquals(2, outcome.pulledOperations)
        assertEquals(3, outcome.pushedOperations)
        assertEquals(3L, outcome.peerHoldsOwnThrough)
        synchronized(b) {
            assertEquals(a.vector().entries, b.vector().entries)
            assertEquals(a.stateDigest(), b.stateDigest())
        }
        val again = SyncSession.run(a, transport(a, server), remoteDeviceId = "B")
        assertEquals(0, again.pulledOperations)
        assertEquals(0, again.pushedOperations)
    }

    @Test
    fun aDeviceWithoutTheFarmKeyCannotConnectAndNothingIsExchanged() {
        val a = replica("A")
        val b = replica("B")
        a.weigh("goat-1", "31.5")
        val server = serve(b)
        val stranger = FarmKeyRing(listOf(FarmDataKey.generate("k1")), "k1")

        val outcome = SyncSession.run(a, transport(a, server, stranger), remoteDeviceId = "B")

        assertEquals(SyncSessionStatus.TRANSPORT_UNAVAILABLE, outcome.status)
        assertTrue(synchronized(b) { b.vector().entries.isEmpty() })
    }

    @Test
    fun aRevokedDeviceIsRefusedEvenWithTheOldKeyAndAfterRotationWithAnyKey() {
        val a = replica("A")
        val serverRegistry = registry()
        val b = replica("B", serverRegistry)
        a.weigh("goat-1", "31.5")
        serverRegistry.markLostOrRevoked("A", SyncVector())
        val server = serve(b)

        assertFalse(transport(a, server).isAvailable())
        val rotated = keys.rotate("k2")
        val rotatedServer = serve(replica("C", registry()), rotated)
        assertFalse(transport(a, rotatedServer, keys).isAvailable())
        assertTrue(synchronized(b) { b.vector().entries.isEmpty() })
    }

    @Test
    fun aDeviceOfAnotherFarmIsRefused() {
        val other = replica("A", farmId = "farm-other")
        val server = serve(replica("B"))
        assertFalse(transport(other, server).isAvailable())
    }

    @Test
    fun aClientRefusesAServerThatCannotProveFarmMembership() {
        val a = replica("A")
        val impostor = serve(replica("B"), FarmKeyRing(listOf(FarmDataKey.generate("k1")), "k1"))
        assertFalse(transport(a, impostor).isAvailable())
    }

    @Test
    fun alteredOrReplayedFramesAreRejected() {
        val (client, server) = handshakePair()
        val captured = ByteArrayOutputStream()
        val sender = LanSecureChannel(ByteArrayInputStream(ByteArray(0)), captured, sendKeyOf(client), ByteArray(32), "B")
        sender.send("first".toByteArray())
        sender.send("second".toByteArray())
        val frames = captured.toByteArray()

        val reader = LanSecureChannel(ByteArrayInputStream(frames), ByteArrayOutputStream(), ByteArray(32), receiveKeyOf(server), "A")
        assertEquals("first", String(reader.receive()))
        assertEquals("second", String(reader.receive()))

        val tampered = frames.copyOf().also { it[6] = (it[6] + 1).toByte() }
        assertFailsWith<AEADBadTagException> {
            LanSecureChannel(ByteArrayInputStream(tampered), ByteArrayOutputStream(), ByteArray(32), receiveKeyOf(server), "A").receive()
        }
        val firstFrameLength = 4 + ByteArrayInputStream(frames).let { java.io.DataInputStream(it).readInt() }
        val replayed = frames.copyOfRange(0, firstFrameLength) + frames.copyOfRange(0, firstFrameLength)
        val replayReader = LanSecureChannel(ByteArrayInputStream(replayed), ByteArrayOutputStream(), ByteArray(32), receiveKeyOf(server), "A")
        assertEquals("first", String(replayReader.receive()))
        assertFailsWith<AEADBadTagException> { replayReader.receive() }
    }

    @Test
    fun bundlesSurviveTheWireEncodingAndStillVerify() {
        val a = replica("A")
        repeat(4) { a.weigh("goat-$it", "1$it") }
        val bundles = a.bundlesFor(listOf(SequenceRange("A", 1, 4)), maxOperationsPerBundle = 3)
        val decoded = LanWire.bundles(LanWire.bundles(bundles))
        assertEquals(bundles, decoded)
        decoded.forEach { assertEquals(BundleVerdict.Valid, it.verify(farm)) }
        assertEquals(listOf("a", "b"), LanWire.rejections(LanWire.rejections(listOf("a", "b"))))
    }

    /** Runs a real handshake over pipes and returns the two established channels. */
    private fun handshakePair(): Pair<LanSecureChannel, LanSecureChannel> {
        val clientToServer = PipedOutputStream()
        val serverIn = PipedInputStream(clientToServer, 1 shl 16)
        val serverToClient = PipedOutputStream()
        val clientIn = PipedInputStream(serverToClient, 1 shl 16)
        val pool = Executors.newSingleThreadExecutor()
        val server = pool.submit<LanSecureChannel> { LanSecureChannel.accept(serverIn, serverToClient, farm, "B", keys, { true }) }
        val client = LanSecureChannel.connect(clientIn, clientToServer, farm, "A", keys, { true })
        return (client to server.get(10, TimeUnit.SECONDS)).also { pool.shutdown() }
    }

    private fun sendKeyOf(channel: LanSecureChannel): ByteArray = channel.field("sendKey")

    private fun receiveKeyOf(channel: LanSecureChannel): ByteArray = channel.field("receiveKey")

    private fun LanSecureChannel.field(name: String): ByteArray =
        LanSecureChannel::class.java.getDeclaredField(name).apply { isAccessible = true }.get(this) as ByteArray
}
