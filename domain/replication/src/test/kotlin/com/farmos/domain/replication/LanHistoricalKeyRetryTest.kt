package com.farmos.domain.replication

import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Real sockets exercise the retained-key negotiation, including its identity and refusal boundaries. */
class LanHistoricalKeyRetryTest {
    private val farm = "farm-retained-key"
    private val loopback = InetAddress.getLoopbackAddress()
    private val first = FarmKeyRing(listOf(FarmDataKey.generate("k1")), "k1")
    private val aKeys = first.rotate("k-A")
    private val bKeys = first.rotate("k-B")
    private val identities = mapOf("A" to DeviceKeys.generate(), "B" to DeviceKeys.generate())
    private val resources = mutableListOf<Closeable>()
    private var nextId = 0

    private fun registry() = DeviceRegistry(listOf("A", "B").map { FarmDevice(it, it, DeviceStatus.ACTIVE) })
    private fun replica(id: String, devices: DeviceRegistry = registry()) =
        FarmReplica(farm, id, devices, { "op-" + id + "-" + ++nextId }, { 1_000L })
    private fun identity(id: String) = LanIdentity(identities.getValue(id)) { identities[it]?.public }
    private fun serve(replica: FarmReplica, serverIdentity: LanIdentity? = identity("B")) =
        LanSyncServer(replica, { bKeys }, identity = serverIdentity)
            .start(InetSocketAddress(loopback, 0)).also { resources += it }
    private fun transport(client: FarmReplica, port: Int, clientIdentity: LanIdentity? = identity("A"), timeout: Int = 5_000) =
        LanPeerTransport(loopback.hostAddress, port, farm, client.deviceId, { aKeys }, client.registry::maySynchronise,
            connectTimeoutMillis = timeout, identity = clientIdentity).also { resources += it }

    private fun FarmReplica.capture() = record(
        entityType = "animal", entityId = "animal-" + deviceId, actorId = "worker-" + deviceId,
        businessTimeEpochMillis = 1_000L, baseVersion = null, operationType = "weight.record",
        mergeClass = MergeClass.APPEND_ONLY_EVENT, payload = mapOf("kg" to "31.5"),
    )

    @AfterTest
    fun close() = resources.reversed().forEach { it.close() }

    @Test
    fun differentCurrentKeysExchangeRecordsUsingTheirSharedHistoryAndKnownIdentities() {
        val a = replica("A")
        val b = replica("B")
        a.capture()
        b.capture()
        assertEquals(null, aKeys.key(bKeys.currentKeyId))
        assertEquals(null, bKeys.key(aKeys.currentKeyId))
        val server = serve(b)
        val outcome = SyncSession.run(a, transport(a, server.port), remoteDeviceId = "B")
        assertEquals(SyncSessionStatus.COMPLETED, outcome.status)
        assertEquals(1, outcome.pulledOperations)
        assertEquals(1, outcome.pushedOperations)
        synchronized(b) {
            assertEquals(a.vector().entries, b.vector().entries)
            assertEquals(a.stateDigest(), b.stateDigest())
        }
    }

    @Test
    fun aFallbackNeverAcceptsAnUnknownOrSpoofedServerIdentity() {
        val unknown = replica("B")
        val unknownServer = serve(unknown)
        val a = replica("A")
        a.capture()
        assertFalse(transport(a, unknownServer.port, LanIdentity(identities.getValue("A")) { null }).isAvailable())
        assertTrue(synchronized(unknown) { unknown.vector().entries.isEmpty() })

        val spoofed = replica("B")
        val impostor = LanIdentity(DeviceKeys.generate()) { identities[it]?.public }
        val spoofedServer = serve(spoofed, impostor)
        assertFalse(transport(a, spoofedServer.port).isAvailable())
        assertTrue(synchronized(spoofed) { spoofed.vector().entries.isEmpty() })
    }

    @Test
    fun aSharedHistoricalKeyCannotReplaceTheClientsDeviceIdentity() {
        val a = replica("A")
        a.capture()
        val b = replica("B")
        val server = serve(b)
        val impostor = LanIdentity(DeviceKeys.generate()) { identities[it]?.public }
        assertFalse(transport(a, server.port, impostor).isAvailable())
        assertFalse(transport(a, server.port, clientIdentity = null).isAvailable())
        assertTrue(synchronized(b) { b.vector().entries.isEmpty() })
    }

    @Test
    fun eitherPeersRevocationStopsNegotiationWithoutExchangingRecords() {
        val revokedClientRegistry = registry().apply { markLostOrRevoked("A", SyncVector()) }
        val b = replica("B", revokedClientRegistry)
        val a = replica("A").also { it.capture() }
        assertFalse(transport(a, serve(b).port).isAvailable())
        assertTrue(synchronized(b) { b.vector().entries.isEmpty() })

        val revokedServerRegistry = registry().apply { markLostOrRevoked("B", SyncVector()) }
        val refusingClient = replica("A", revokedServerRegistry).also { it.capture() }
        val otherwiseActive = replica("B")
        assertFalse(transport(refusingClient, serve(otherwiseActive).port).isAvailable())
        assertTrue(synchronized(otherwiseActive) { otherwiseActive.vector().entries.isEmpty() })
    }

    @Test
    fun aTimeoutDoesNotTryEveryRetainedKey() {
        RawHandshakeServer(loopback, refusal = null).use { server ->
            assertFalse(transport(replica("A"), server.port, timeout = 100).isAvailable())
            assertTrue(server.firstConnection.await(2, TimeUnit.SECONDS))
            assertEquals(1, server.connections.get())
        }
    }

    @Test
    fun anExplicitNonKeyRefusalDoesNotTryEveryRetainedKey() {
        RawHandshakeServer(loopback, refusal = "This device is not an active device of the farm").use { server ->
            assertFalse(transport(replica("A"), server.port, timeout = 100).isAvailable())
            assertTrue(server.refusalSent.await(2, TimeUnit.SECONDS))
            assertEquals(1, server.connections.get())
        }
    }

    /** A silent or explicitly refusing peer counts real TCP attempts without sleeping in the test. */
    private class RawHandshakeServer(address: InetAddress, private val refusal: String?) : Closeable {
        private val listener = ServerSocket().apply { bind(InetSocketAddress(address, 0)) }
        private val peers = CopyOnWriteArrayList<Socket>()
        val connections = AtomicInteger()
        val firstConnection = CountDownLatch(1)
        val refusalSent = CountDownLatch(1)
        val port: Int get() = listener.localPort
        private val thread = Thread({
            while (!listener.isClosed) {
                val peer = try { listener.accept() } catch (_: IOException) { break }
                peers += peer
                connections.incrementAndGet()
                firstConnection.countDown()
                if (refusal != null) {
                    try {
                        peer.use {
                            peer.soTimeout = 2_000
                            val input = DataInputStream(peer.getInputStream())
                            repeat(4) { input.readUTF() }
                            repeat(2) {
                                val length = input.readInt()
                                require(length in 0..4_096)
                                input.readFully(ByteArray(length))
                            }
                            DataOutputStream(peer.getOutputStream()).apply {
                                writeBoolean(false)
                                writeUTF(refusal)
                                flush()
                            }
                            refusalSent.countDown()
                        }
                    } catch (_: IOException) {
                        // A failed client closes its attempt; the listener can still count later attempts.
                    }
                }
            }
        }, "retained-key-test-peer").apply { isDaemon = true; start() }

        override fun close() {
            listener.close()
            peers.forEach { runCatching { it.close() } }
            thread.join(2_000L)
        }
    }
}
