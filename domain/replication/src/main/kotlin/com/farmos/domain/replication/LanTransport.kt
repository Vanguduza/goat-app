package com.farmos.domain.replication

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.security.GeneralSecurityException
import java.security.KeyPair
import java.security.MessageDigest
import java.security.PublicKey
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** This device's identity key pair and the known identity keys of farm devices, for device-authenticated sessions. */
class LanIdentity(val own: KeyPair, val peerKey: (deviceId: String) -> PublicKey?)

/** The peer could not prove it belongs to this farm, or is not an authorised farm device. */
class LanPeerRefused(
    message: String,
    internal val mayRetryHistoricalKey: Boolean = false,
) : IOException(message)

/** Existing wire refusal retained for protocol-compatible key negotiation. */
private const val FARM_KEY_CHANGED_REASON = "The farm key has changed; pair this device again"

/**
 * An encrypted, mutually authenticated farm-LAN channel. The handshake exchanges ephemeral P-256 keys
 * and proves on both sides, with HMAC over the whole transcript, possession of the farm's current data
 * key; a device that was never paired, or was revoked before the key rotated, cannot complete it. Each
 * direction then uses its own AES-256-GCM key with a counter nonce, so a replayed, reordered or altered
 * frame is refused.
 */
class LanSecureChannel internal constructor(
    input: InputStream,
    output: OutputStream,
    private val sendKey: ByteArray,
    private val receiveKey: ByteArray,
    val peerDeviceId: String,
) {
    private val input = DataInputStream(input)
    private val output = DataOutputStream(output)
    private var sent = 0L
    private var received = 0L

    fun send(plaintext: ByteArray) {
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(sendKey, "AES"), GCMParameterSpec(TAG_BITS, nonce(sent++)))
        val frame = cipher.doFinal(plaintext)
        output.writeInt(frame.size)
        output.write(frame)
        output.flush()
    }

    fun receive(): ByteArray {
        val size = input.readInt()
        if (size !in 1..MAX_FRAME_BYTES) throw IOException("Frame size $size is outside the protocol limit")
        val frame = ByteArray(size).also(input::readFully)
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(receiveKey, "AES"), GCMParameterSpec(TAG_BITS, nonce(received++)))
        return cipher.doFinal(frame)
    }

    companion object {
        private val MAGIC = "GOATLAN1"

        /** Client side of the handshake; throws [LanPeerRefused] if the server cannot prove farm membership. */
        fun connect(
            input: InputStream,
            output: OutputStream,
            farmId: String,
            deviceId: String,
            keys: FarmKeyRing,
            authorised: (deviceId: String) -> Boolean,
            random: SecureRandom = SecureRandom(),
            identity: LanIdentity? = null,
            /** A retained-key retry must authenticate a known server, never just shared-key possession. */
            requireKnownPeerIdentity: Boolean = false,
        ): LanSecureChannel {
            val din = DataInputStream(input)
            val dout = DataOutputStream(output)
            val ephemeral = DeviceKeys.generate(random)
            val clientNonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
            val key = keys.current
            dout.writeUTF(MAGIC)
            dout.writeUTF(farmId)
            dout.writeUTF(deviceId)
            dout.writeUTF(key.keyId)
            dout.writeBytesField(DeviceKeys.encode(ephemeral.public))
            dout.writeBytesField(clientNonce)
            dout.flush()

            if (!din.readBoolean()) {
                val reason = din.readUTF()
                throw LanPeerRefused(reason, mayRetryHistoricalKey = reason == FARM_KEY_CHANGED_REASON)
            }
            val serverDeviceId = din.readUTF()
            val serverEphemeral = din.readBytesField()
            val serverNonce = din.readBytesField()
            val serverProof = din.readBytesField()
            val serverIdentityProof = din.readBytesField()
            val transcript = transcript(farmId, deviceId, key.keyId, DeviceKeys.encode(ephemeral.public), clientNonce, serverDeviceId, serverEphemeral, serverNonce)
            if (!MessageDigest.isEqual(serverProof, proof(key, SERVER_ROLE, transcript))) throw LanPeerRefused("The peer could not prove it belongs to this farm")
            if (!authorised(serverDeviceId)) throw LanPeerRefused("The peer is not an active device of this farm")
            // When this device knows the server's identity key, the server must prove it holds the private half.
            val serverStatic = identity?.peerKey?.invoke(serverDeviceId)
            if (requireKnownPeerIdentity && serverStatic == null) {
                throw LanPeerRefused("A previous farm key requires a known peer device identity")
            }
            serverStatic?.let {
                val expected = identityProof(ephemeral.private, it, SERVER_ROLE, transcript)
                if (!MessageDigest.isEqual(serverIdentityProof, expected)) throw LanPeerRefused("The peer could not prove it is $serverDeviceId")
            }
            dout.writeBytesField(proof(key, CLIENT_ROLE, transcript))
            dout.writeBytesField(identity?.let { identityProof(it.own.private, DeviceKeys.decode(serverEphemeral), CLIENT_ROLE, transcript) } ?: ByteArray(0))
            dout.flush()
            // The server confirms only after checking this device's proofs, so a refused client knows at once.
            if (!din.readBoolean()) throw LanPeerRefused(din.readUTF())

            val session = sessionKeys(ephemeral.private, serverEphemeral, key, transcript)
            return LanSecureChannel(input, output, session.first, session.second, serverDeviceId)
        }

        /**
         * Server side of the handshake; refuses other farms, unknown or revoked devices and stale keys. A client
         * whose device identity key is known here and proven in the handshake may use a previous farm key this
         * device still holds, so a device that has not yet received a rotated key can connect to receive it.
         */
        fun accept(
            input: InputStream,
            output: OutputStream,
            farmId: String,
            deviceId: String,
            keys: FarmKeyRing,
            authorised: (deviceId: String) -> Boolean,
            random: SecureRandom = SecureRandom(),
            identity: LanIdentity? = null,
        ): LanSecureChannel {
            val din = DataInputStream(input)
            val dout = DataOutputStream(output)
            if (din.readUTF() != MAGIC) throw IOException("Not a GOAT farm LAN peer")
            val peerFarm = din.readUTF()
            val peerDevice = din.readUTF()
            val keyId = din.readUTF()
            val clientEphemeral = din.readBytesField()
            val clientNonce = din.readBytesField()
            val clientStatic = identity?.peerKey?.invoke(peerDevice)
            val key = keys.key(keyId)
            val refusal = when {
                peerFarm != farmId -> "This device serves another farm"
                !authorised(peerDevice) -> "This device is not an active device of the farm"
                key == null -> FARM_KEY_CHANGED_REASON
                keyId != keys.currentKeyId && clientStatic == null -> FARM_KEY_CHANGED_REASON
                else -> null
            }
            if (refusal != null) refuse(dout, refusal)
            val agreed = requireNotNull(key)
            val ephemeral = DeviceKeys.generate(random)
            val serverNonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
            val serverEphemeral = DeviceKeys.encode(ephemeral.public)
            val transcript = transcript(farmId, peerDevice, keyId, clientEphemeral, clientNonce, deviceId, serverEphemeral, serverNonce)
            dout.writeBoolean(true)
            dout.writeUTF(deviceId)
            dout.writeBytesField(serverEphemeral)
            dout.writeBytesField(serverNonce)
            dout.writeBytesField(proof(agreed, SERVER_ROLE, transcript))
            dout.writeBytesField(identity?.let { identityProof(it.own.private, DeviceKeys.decode(clientEphemeral), SERVER_ROLE, transcript) } ?: ByteArray(0))
            dout.flush()
            val clientProof = din.readBytesField()
            val clientIdentityProof = din.readBytesField()
            if (!MessageDigest.isEqual(clientProof, proof(agreed, CLIENT_ROLE, transcript))) refuse(dout, "The peer could not prove it belongs to this farm")
            if (clientStatic != null) {
                val expected = identityProof(ephemeral.private, clientStatic, CLIENT_ROLE, transcript)
                if (!MessageDigest.isEqual(clientIdentityProof, expected)) refuse(dout, "The peer could not prove it is $peerDevice")
            }
            dout.writeBoolean(true)
            dout.flush()

            val session = sessionKeys(ephemeral.private, clientEphemeral, agreed, transcript)
            return LanSecureChannel(input, output, session.second, session.first, peerDevice)
        }

        private fun refuse(out: DataOutputStream, reason: String): Nothing {
            out.writeBoolean(false)
            out.writeUTF(reason)
            out.flush()
            throw LanPeerRefused(reason)
        }

        /**
         * Proves possession of a static device key: ECDH between one side's static key and the other side's
         * ephemeral key, which only the holder of the static private key (or of that ephemeral) can compute.
         */
        private fun identityProof(own: java.security.PrivateKey, peer: java.security.PublicKey, role: String, transcript: ByteArray): ByteArray {
            val shared = KeyAgreement.getInstance("ECDH").apply {
                init(own)
                doPhase(peer, true)
            }.generateSecret()
            val macKey = Hkdf.sha256(shared, salt = transcript, info = "goat-lan-identity-v1|$role".toByteArray(), length = 32)
            return Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(macKey, "HmacSHA256")) }.doFinal(transcript)
        }

        private fun transcript(
            farmId: String,
            clientDevice: String,
            keyId: String,
            clientEphemeral: ByteArray,
            clientNonce: ByteArray,
            serverDevice: String,
            serverEphemeral: ByteArray,
            serverNonce: ByteArray,
        ): ByteArray = MessageDigest.getInstance("SHA-256").digest(
            canonical(listOf(MAGIC, farmId, clientDevice, keyId, hex(clientEphemeral), hex(clientNonce), serverDevice, hex(serverEphemeral), hex(serverNonce))),
        )

        private fun proof(key: FarmDataKey, role: String, transcript: ByteArray): ByteArray =
            Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key.material(), "HmacSHA256")) }.doFinal(role.toByteArray() + transcript)

        /** (client-to-server key, server-to-client key). */
        private fun sessionKeys(own: java.security.PrivateKey, peerEphemeral: ByteArray, key: FarmDataKey, transcript: ByteArray): Pair<ByteArray, ByteArray> {
            val shared = KeyAgreement.getInstance("ECDH").apply {
                init(own)
                doPhase(DeviceKeys.decode(peerEphemeral), true)
            }.generateSecret()
            val okm = Hkdf.sha256(shared, salt = key.material(), info = "goat-lan-session-v1".toByteArray() + transcript, length = 64)
            return okm.copyOfRange(0, 32) to okm.copyOfRange(32, 64)
        }

        private fun nonce(counter: Long): ByteArray = ByteArray(12).also { bytes ->
            for (i in 0 until 8) bytes[11 - i] = (counter ushr (8 * i)).toByte()
        }

        private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

        private const val AES_GCM = "AES/GCM/NoPadding"
        private const val TAG_BITS = 128
        private const val NONCE_BYTES = 32
        private const val CLIENT_ROLE = "client"
        private const val SERVER_ROLE = "server"
        internal const val MAX_FRAME_BYTES = 16 * 1024 * 1024
    }
}

/** Binary encoding of the LAN protocol's vectors, ranges and bundles. Bundles are re-verified on receipt. */
internal object LanWire {
    fun vector(vector: SyncVector): ByteArray = write {
        writeInt(vector.entries.size)
        vector.entries.forEach { (device, mark) -> writeUTF(device); writeLong(mark) }
    }

    fun vector(bytes: ByteArray): SyncVector = read(bytes) {
        SyncVector((0 until readCount()).associate { readUTF() to readLong() })
    }

    fun ranges(ranges: List<SequenceRange>): ByteArray = write {
        writeInt(ranges.size)
        ranges.forEach { writeUTF(it.deviceId); writeLong(it.from); writeLong(it.to) }
    }

    fun ranges(bytes: ByteArray): List<SequenceRange> = read(bytes) {
        List(readCount()) { SequenceRange(readUTF(), readLong(), readLong()) }
    }

    fun rejections(reasons: List<String>): ByteArray = write {
        writeInt(reasons.size)
        reasons.forEach { writeLongString(it) }
    }

    fun rejections(bytes: ByteArray): List<String> = read(bytes) { List(readCount()) { readLongString() } }

    fun bundles(bundles: List<OperationBundle>): ByteArray = write {
        writeInt(bundles.size)
        bundles.forEach { bundle ->
            writeUTF(bundle.farmId)
            writeUTF(bundle.deviceId)
            writeLong(bundle.fromSequence)
            writeLong(bundle.toSequence)
            writeInt(bundle.protocolVersion)
            writeUTF(bundle.checksum)
            writeInt(bundle.operations.size)
            bundle.operations.forEach { writeOperation(it) }
        }
    }

    fun bundles(bytes: ByteArray): List<OperationBundle> = read(bytes) {
        List(readCount()) {
            val farmId = readUTF()
            val deviceId = readUTF()
            val from = readLong()
            val to = readLong()
            val protocol = readInt()
            val checksum = readUTF()
            val operations = List(readCount()) { readOperation() }
            OperationBundle(farmId, deviceId, from, to, operations, protocol, checksum)
        }
    }

    fun blobRequest(contentSha256: String, offset: Long): ByteArray = write {
        writeLongString(contentSha256)
        writeLong(offset)
    }

    fun blobRequest(bytes: ByteArray): Pair<String, Long> = read(bytes) { readLongString() to readLong() }

    /** A chunk of attachment bytes with the content's total size; a negative size means the bytes are not held. */
    fun blobChunk(totalBytes: Long, chunk: ByteArray): ByteArray = write {
        writeLong(totalBytes)
        writeBytesField(chunk)
    }

    fun blobChunk(bytes: ByteArray): Pair<Long, ByteArray> = read(bytes) { readLong() to readBytesField() }

    private fun DataOutputStream.writeOperation(op: OperationEnvelope) {
        listOf(op.operationId, op.farmId, op.entityType, op.entityId, op.actorId, op.deviceId).forEach { writeLongString(it) }
        writeLong(op.deviceSequence)
        writeLong(op.businessTimeEpochMillis)
        writeLong(op.createdAtEpochMillis)
        writeBoolean(op.baseVersion != null)
        op.baseVersion?.let(::writeLong)
        writeLongString(op.operationType)
        writeLongString(op.mergeClass.name)
        writeInt(op.payload.size)
        op.payload.toSortedMap().forEach { (k, v) -> writeLongString(k); writeLongString(v) }
        writeInt(op.protocolVersion)
        writeInt(op.schemaVersion)
        writeLongString(op.provenance)
        writeLongString(op.checksum)
    }

    private fun DataInputStream.readOperation(): OperationEnvelope {
        val operationId = readLongString()
        val farmId = readLongString()
        val entityType = readLongString()
        val entityId = readLongString()
        val actorId = readLongString()
        val deviceId = readLongString()
        val sequence = readLong()
        val business = readLong()
        val created = readLong()
        val baseVersion = if (readBoolean()) readLong() else null
        val operationType = readLongString()
        val mergeClass = MergeClass.valueOf(readLongString())
        val payload = (0 until readCount()).associate { readLongString() to readLongString() }
        return OperationEnvelope(
            operationId, farmId, entityType, entityId, actorId, deviceId, sequence, business, created, baseVersion,
            operationType, mergeClass, payload, readInt(), readInt(), readLongString(), readLongString(),
        )
    }

    /** Payloads can exceed writeUTF's 64 KB limit, so strings carry an int length. */
    private fun DataOutputStream.writeLongString(value: String) = writeBytesField(value.toByteArray(Charsets.UTF_8))

    private fun DataInputStream.readLongString(): String = String(readBytesField(), Charsets.UTF_8)

    private fun DataInputStream.readCount(): Int = readInt().also { if (it !in 0..MAX_ITEMS) throw IOException("Count $it is outside the protocol limit") }

    private fun write(block: DataOutputStream.() -> Unit): ByteArray =
        ByteArrayOutputStream().also { DataOutputStream(it).apply(block).flush() }.toByteArray()

    private fun <T> read(bytes: ByteArray, block: DataInputStream.() -> T): T = DataInputStream(ByteArrayInputStream(bytes)).block()

    private const val MAX_ITEMS = 1_000_000
}

private fun DataOutputStream.writeBytesField(bytes: ByteArray) {
    writeInt(bytes.size)
    write(bytes)
}

private fun DataInputStream.readBytesField(): ByteArray {
    val size = readInt()
    if (size !in 0..LanSecureChannel.MAX_FRAME_BYTES) throw IOException("Field size $size is outside the protocol limit")
    return ByteArray(size).also(::readFully)
}

private object LanRequest {
    const val VECTOR: Byte = 1
    const val FETCH: Byte = 2
    const val PUBLISH: Byte = 3
    const val BLOB: Byte = 4
}

/** Attachment bytes this device can serve to its farm's peers (D-015), addressed by content hash. */
interface AttachmentBlobSource {
    /** The size of the held content, or null when its bytes are not on this device. */
    fun size(contentSha256: String): Long?

    /** [length] bytes of the held content from [offset], or null when they are not on this device. */
    fun read(contentSha256: String, offset: Long, length: Int): ByteArray?
}

/** Attachment bytes travel in chunks well inside the frame limit. */
internal const val BLOB_CHUNK_BYTES = 1024 * 1024
private val CONTENT_SHA256 = Regex("^[0-9a-f]{64}$")

/**
 * Serves this device's replica to authenticated farm peers on the LAN. Incoming bundles go through the
 * same verification and registry checks as any other transport; a rejected bundle changes nothing.
 * Calls into the replica are serialised on [lock].
 */
class LanSyncServer(
    private val replica: ReplicaEndpoint,
    private val keys: () -> FarmKeyRing,
    private val lock: Any = replica,
    private val random: SecureRandom = SecureRandom(),
    /** When set, peers whose identity key is known must prove it, and may then use a previous farm key. */
    private val identity: LanIdentity? = null,
    /** This device's attachment bytes for the farm; none are served when null. */
    private val blobs: AttachmentBlobSource? = null,
) : Closeable {
    private var server: ServerSocket? = null

    /** Port the server listens on after [start]. */
    val port: Int get() = checkNotNull(server) { "Server not started" }.localPort

    /** Listens on [bindAddress]:[port] (0 picks a free port) and serves each peer on its own thread. */
    fun start(bindAddress: InetSocketAddress = InetSocketAddress(0)): LanSyncServer {
        val socket = ServerSocket().apply { bind(bindAddress) }
        server = socket
        Thread({
            while (!socket.isClosed) {
                val peer = try { socket.accept() } catch (_: SocketException) { break }
                Thread({ peer.use(::serve) }, "goat-lan-peer").apply { isDaemon = true }.start()
            }
        }, "goat-lan-server").apply { isDaemon = true }.start()
        return this
    }

    /** Serves one connected peer until it disconnects. Handshake failures close the connection. */
    fun serve(socket: Socket) {
        socket.soTimeout = PEER_TIMEOUT_MILLIS
        val input = BufferedInputStream(socket.getInputStream())
        val output = BufferedOutputStream(socket.getOutputStream())
        val ring = synchronized(lock) { keys() }
        val channel = try {
            LanSecureChannel.accept(input, output, replica.farmId, replica.deviceId, ring, ::authorised, random, identity)
        } catch (_: IOException) {
            return
        }
        while (true) {
            val request = try {
                channel.receive()
            } catch (_: IOException) {
                return
            } catch (_: GeneralSecurityException) {
                // An altered, replayed or reordered frame ends the session; nothing from it is applied.
                return
            }
            if (!authorised(channel.peerDeviceId)) return
            val body = request.copyOfRange(1, request.size)
            // Attachment bytes are read outside the replica lock; they never change the replica.
            val response = if (request.first() == LanRequest.BLOB) blobResponse(body) else synchronized(lock) {
                when (request.first()) {
                    LanRequest.VECTOR -> LanWire.vector(replica.vector())
                    LanRequest.FETCH -> LanWire.bundles(replica.bundlesFor(LanWire.ranges(body)))
                    LanRequest.PUBLISH -> LanWire.rejections(LanWire.bundles(body).mapNotNull { replica.ingest(it).rejectedReason })
                    else -> return
                }
            }
            channel.send(response)
        }
    }

    private fun authorised(deviceId: String): Boolean = synchronized(lock) { replica.maySynchronise(deviceId) }

    private fun blobResponse(body: ByteArray): ByteArray {
        val (sha, offset) = LanWire.blobRequest(body)
        val source = blobs
        val total = if (source != null && CONTENT_SHA256.matches(sha)) source.size(sha) else null
        if (source == null || total == null || offset !in 0..total) return LanWire.blobChunk(-1, ByteArray(0))
        val chunk = source.read(sha, offset, minOf(BLOB_CHUNK_BYTES.toLong(), total - offset).toInt()) ?: return LanWire.blobChunk(-1, ByteArray(0))
        return LanWire.blobChunk(total, chunk)
    }

    override fun close() {
        server?.close()
    }

    private companion object {
        const val PEER_TIMEOUT_MILLIS = 30_000
    }
}

/**
 * The farm-LAN transport: the same vector, fetch and publish exchange as every other transport, carried
 * over an authenticated, encrypted socket to a farm peer found by discovery.
 */
class LanPeerTransport(
    private val host: String,
    private val port: Int,
    private val farmId: String,
    private val deviceId: String,
    private val keys: () -> FarmKeyRing,
    private val authorised: (deviceId: String) -> Boolean,
    private val connectTimeoutMillis: Int = 5_000,
    private val random: SecureRandom = SecureRandom(),
    private val identity: LanIdentity? = null,
) : ReplicationTransport, Closeable {
    override val kind = TransportKind.FARM_LAN_PEER
    private var socket: Socket? = null
    private var channel: LanSecureChannel? = null

    /** Connects and completes the handshake; false when the peer is unreachable or refuses this device. */
    override fun isAvailable(): Boolean = try {
        open()
        true
    } catch (_: IOException) {
        close()
        false
    }

    /** The farm device on the other side, known once the handshake has succeeded. */
    val peerDeviceId: String? get() = channel?.peerDeviceId

    override fun remoteVector(farmId: String): SyncVector {
        check(farmId == this.farmId) { "Peer serves another farm" }
        return LanWire.vector(call(LanRequest.VECTOR, ByteArray(0)))
    }

    override fun fetch(farmId: String, ranges: List<SequenceRange>): List<OperationBundle> {
        check(farmId == this.farmId) { "Peer serves another farm" }
        return LanWire.bundles(call(LanRequest.FETCH, LanWire.ranges(ranges)))
    }

    override fun publish(farmId: String, bundles: List<OperationBundle>) {
        check(farmId == this.farmId) { "Peer serves another farm" }
        lastRejections = LanWire.rejections(call(LanRequest.PUBLISH, LanWire.bundles(bundles)))
    }

    /**
     * The attachment bytes with [contentSha256] from the peer, fetched in chunks (D-015). Null when the peer
     * does not hold them, their size changes mid-transfer or exceeds [maxBytes]. The caller verifies the hash
     * before keeping them.
     */
    fun fetchAttachment(contentSha256: String, maxBytes: Long): ByteArray? {
        val received = java.io.ByteArrayOutputStream()
        var offset = 0L
        var expected = -1L
        do {
            val (total, chunk) = LanWire.blobChunk(call(LanRequest.BLOB, LanWire.blobRequest(contentSha256, offset)))
            if (total < 0 || total > maxBytes || (expected >= 0 && total != expected)) return null
            expected = total
            if (chunk.isEmpty() && offset < total) return null
            received.write(chunk)
            offset += chunk.size
        } while (offset < total)
        return received.toByteArray().takeIf { it.size.toLong() == expected }
    }

    /** Reasons the peer gave for refusing bundles in the last publish; empty when it accepted all. */
    var lastRejections: List<String> = emptyList()
        private set

    private fun call(request: Byte, body: ByteArray): ByteArray {
        val open = open()
        open.send(byteArrayOf(request) + body)
        return open.receive()
    }

    private fun open(): LanSecureChannel {
        channel?.let { return it }
        val available = keys()
        val candidates = listOf(available.currentKeyId) + if (identity == null) emptyList() else
            available.keyIds.filter { it != available.currentKeyId }.sorted()
        var keyRefusal: LanPeerRefused? = null
        for (keyId in candidates) {
            try {
                return openWithKey(
                    FarmKeyRing(available.all(), keyId),
                    requireKnownPeerIdentity = keyId != available.currentKeyId,
                )
            } catch (refused: LanPeerRefused) {
                // Only the first, explicit key-negotiation refusal allows a new handshake. Timeouts,
                // bad identity proofs, revocation and every later refusal propagate without fallback.
                if (!refused.mayRetryHistoricalKey) throw refused
                keyRefusal = refused
            }
        }
        throw requireNotNull(keyRefusal)
    }

    private fun openWithKey(ring: FarmKeyRing, requireKnownPeerIdentity: Boolean): LanSecureChannel {
        val connected = Socket()
        socket = connected
        try {
            connected.connect(InetSocketAddress(host, port), connectTimeoutMillis)
            connected.soTimeout = connectTimeoutMillis * 6
            return LanSecureChannel.connect(
                BufferedInputStream(connected.getInputStream()),
                BufferedOutputStream(connected.getOutputStream()),
                farmId, deviceId, ring, authorised, random, identity, requireKnownPeerIdentity,
            ).also { channel = it }
        } catch (failure: Throwable) {
            // Every key attempt has fresh nonces, ephemeral keys and a new socket.
            runCatching { connected.close() }
            socket = null
            throw failure
        }
    }

    override fun close() {
        channel = null
        socket?.close()
        socket = null
    }
}
