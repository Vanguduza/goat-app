package com.farmos.domain.replication

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException

/** An enrolment request waiting for a person on the farm device to decide. */
class PendingEnrolment(val request: EnrolmentRequest) {
    val deviceName: String get() = request.deviceName
}

/** What the approving person decided for a [PendingEnrolment]. */
sealed interface EnrolmentDecision {
    /** The approver typed [codeShownOnNewDevice], read from the new device's screen. */
    data class Approve(val approverAccountId: String, val approverMayApprove: Boolean, val codeShownOnNewDevice: String) : EnrolmentDecision

    data object Decline : EnrolmentDecision
}

/** The new device's view of the outcome. */
sealed interface EnrolmentOutcome {
    data class Granted(val grant: DeviceGrant) : EnrolmentOutcome

    data class Refused(val reason: String) : EnrolmentOutcome
}

/**
 * Pairing over the farm LAN. The channel carries no secret: the request holds only the new device's
 * public key and nonce, and the grant's farm keys are wrapped to that key. Authority comes from the
 * approver typing the code shown on the new device (a device in the middle would produce a different
 * code) or from a single-use QR invitation, both checked by [PairingAuthority].
 */
class PairingServer(
    private val authority: PairingAuthority,
    private val keys: () -> FarmKeyRing,
    private val driveFolderId: () -> String? = { null },
    private val lock: Any = authority,
    /** Persists an approved enrolment before the grant is sent, so the new device is known when it first syncs. */
    private val onGranted: (EnrolmentRequest, DeviceGrant) -> Unit = { _, _ -> },
    /** Told why a request was refused, so the approver sees the real outcome. */
    private val onRefused: (EnrolmentRequest, String) -> Unit = { _, _ -> },
    /** Asks a person on this device; blocks until they decide. */
    private val decide: (PendingEnrolment) -> EnrolmentDecision,
) : Closeable {
    private var server: ServerSocket? = null

    val port: Int get() = checkNotNull(server) { "Server not started" }.localPort

    fun start(bindAddress: InetSocketAddress = InetSocketAddress(0)): PairingServer {
        val socket = ServerSocket().apply { bind(bindAddress) }
        server = socket
        Thread({
            while (!socket.isClosed) {
                val peer = try { socket.accept() } catch (_: SocketException) { break }
                Thread({ peer.use(::serve) }, "goat-pairing-peer").apply { isDaemon = true }.start()
            }
        }, "goat-pairing-server").apply { isDaemon = true }.start()
        return this
    }

    fun serve(socket: Socket) {
        socket.soTimeout = DECISION_TIMEOUT_MILLIS
        val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
        val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
        val request = try { PairingCodec.readRequest(input) } catch (_: IOException) { return }
        val decision = if (request.invitationToken != null) {
            synchronized(lock) { authority.approveByInvitation(request, keys(), driveFolderId()) }
        } else {
            when (val answer = decide(PendingEnrolment(request))) {
                EnrolmentDecision.Decline -> null
                is EnrolmentDecision.Approve -> synchronized(lock) {
                    authority.approveByCode(request, answer.approverAccountId, answer.approverMayApprove, answer.codeShownOnNewDevice, keys(), driveFolderId())
                }
            }
        }
        when (decision) {
            is PairingDecision.Approved -> {
                onGranted(request, decision.grant)
                PairingCodec.writeGrant(output, decision.grant)
            }
            is PairingDecision.Rejected -> {
                onRefused(request, decision.reason.name)
                PairingCodec.writeRefusal(output, decision.reason.name)
            }
            null -> {
                onRefused(request, "DECLINED")
                PairingCodec.writeRefusal(output, "DECLINED")
            }
        }
        output.flush()
    }

    override fun close() {
        server?.close()
    }

    private companion object {
        const val DECISION_TIMEOUT_MILLIS = 5 * 60 * 1000
    }
}

/** The new device's side: send the request and wait for the farm's decision. */
object PairingClient {
    fun requestEnrolment(host: String, port: Int, request: EnrolmentRequest, timeoutMillis: Int = 5 * 60 * 1000): EnrolmentOutcome {
        val address = InetSocketAddress(host, port)
        Socket().use { socket ->
            socket.connect(address, CONNECT_TIMEOUT_MILLIS)
            socket.soTimeout = timeoutMillis
            val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
            PairingCodec.writeRequest(output, request)
            output.flush()
            return PairingCodec.readOutcome(DataInputStream(BufferedInputStream(socket.getInputStream())))
        }
    }

    private const val CONNECT_TIMEOUT_MILLIS = 5_000
}

internal object PairingCodec {
    private const val MAGIC = "GOATPAIR1"

    fun writeRequest(out: DataOutputStream, request: EnrolmentRequest) {
        out.writeUTF(MAGIC)
        out.writeUTF(request.requestId)
        out.writeUTF(request.farmId)
        out.writeUTF(request.deviceId)
        out.writeUTF(request.deviceName.take(MAX_NAME))
        out.bytes(request.devicePublicKey)
        out.bytes(request.nonce)
        out.writeLong(request.requestedAtEpochMillis)
        out.writeBoolean(request.invitationToken != null)
        request.invitationToken?.let(out::writeUTF)
    }

    fun readRequest(input: DataInputStream): EnrolmentRequest {
        if (input.readUTF() != MAGIC) throw IOException("Not a GOAT pairing request")
        return EnrolmentRequest(
            requestId = input.readUTF(),
            farmId = input.readUTF(),
            deviceId = input.readUTF(),
            deviceName = input.readUTF().take(MAX_NAME),
            devicePublicKey = input.bytes(),
            nonce = input.bytes(),
            requestedAtEpochMillis = input.readLong(),
            invitationToken = if (input.readBoolean()) input.readUTF() else null,
        )
    }

    fun writeGrant(out: DataOutputStream, grant: DeviceGrant) {
        out.writeBoolean(true)
        out.writeUTF(grant.farmId)
        out.writeUTF(grant.deviceId)
        out.writeUTF(grant.deviceName)
        out.writeUTF(grant.approvedByAccountId)
        out.writeLong(grant.approvedAtEpochMillis)
        out.writeUTF(grant.currentKeyId)
        out.writeInt(grant.wrappedKeys.size)
        grant.wrappedKeys.forEach { key ->
            out.writeUTF(key.keyId)
            out.bytes(key.ephemeralPublicKey)
            out.bytes(key.nonce)
            out.bytes(key.ciphertext)
        }
        out.writeBoolean(grant.driveFolderId != null)
        grant.driveFolderId?.let(out::writeUTF)
        out.writeInt(grant.devices.size)
        grant.devices.forEach { device ->
            out.writeUTF(device.deviceId)
            out.writeUTF(device.name.take(MAX_NAME))
            out.writeUTF(device.status.name)
            out.writeLong(device.lastReportedOwnSequence)
            out.writeBoolean(device.revokedAfterSequence != null)
            device.revokedAfterSequence?.let(out::writeLong)
            out.writeBoolean(device.publicKey != null)
            device.publicKey?.let(out::writeUTF)
        }
    }

    fun writeRefusal(out: DataOutputStream, reason: String) {
        out.writeBoolean(false)
        out.writeUTF(reason)
    }

    fun readOutcome(input: DataInputStream): EnrolmentOutcome {
        if (!input.readBoolean()) return EnrolmentOutcome.Refused(input.readUTF())
        val farmId = input.readUTF()
        val deviceId = input.readUTF()
        val deviceName = input.readUTF()
        val approvedBy = input.readUTF()
        val approvedAt = input.readLong()
        val currentKeyId = input.readUTF()
        val count = input.readInt()
        if (count !in 0..MAX_KEYS) throw IOException("Key count $count is outside the protocol limit")
        val wrapped = List(count) { WrappedFarmKey(input.readUTF(), input.bytes(), input.bytes(), input.bytes()) }
        val drive = if (input.readBoolean()) input.readUTF() else null
        val deviceCount = input.readInt()
        if (deviceCount !in 0..MAX_KEYS) throw IOException("Device count $deviceCount is outside the protocol limit")
        val devices = List(deviceCount) {
            FarmDevice(
                deviceId = input.readUTF(),
                name = input.readUTF(),
                status = DeviceStatus.valueOf(input.readUTF()),
                lastReportedOwnSequence = input.readLong(),
                revokedAfterSequence = if (input.readBoolean()) input.readLong() else null,
                publicKey = if (input.readBoolean()) input.readUTF() else null,
            )
        }
        return EnrolmentOutcome.Granted(DeviceGrant(farmId, deviceId, deviceName, approvedBy, approvedAt, currentKeyId, wrapped, drive, devices))
    }

    private fun DataOutputStream.bytes(value: ByteArray) {
        writeInt(value.size)
        write(value)
    }

    private fun DataInputStream.bytes(): ByteArray {
        val size = readInt()
        if (size !in 0..MAX_FIELD_BYTES) throw IOException("Field size $size is outside the protocol limit")
        return ByteArray(size).also(::readFully)
    }

    private const val MAX_NAME = 60
    private const val MAX_KEYS = 1_000
    private const val MAX_FIELD_BYTES = 64 * 1024
}
