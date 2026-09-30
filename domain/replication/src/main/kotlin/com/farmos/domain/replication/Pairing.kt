package com.farmos.domain.replication

import java.security.SecureRandom
import java.util.Base64

/**
 * What a farm device advertises on the farm LAN as `_goatfarm._tcp`. It says only that a GOAT farm is
 * here and how to reach it; it carries no token, password, private key or credential, and cannot grant
 * authority. The record keys are a closed set.
 */
data class FarmDiscoveryDescriptor(
    val farmId: String,
    val farmName: String,
    val protocolVersion: Int,
    val syncGeneration: Long,
    val currentKeyId: String,
    /** Fingerprint of the farm's public pairing identity, checked again during pairing. */
    val pairingFingerprint: String,
    val driveFolderId: String? = null,
) {
    fun toTxtRecord(): Map<String, String> {
        val record = buildMap {
            put(FARM_ID, farmId)
            put(FARM_NAME, farmName.take(MAX_NAME))
            put(PROTOCOL, protocolVersion.toString())
            put(GENERATION, syncGeneration.toString())
            put(KEY_ID, currentKeyId)
            put(PAIRING, pairingFingerprint)
            driveFolderId?.let { put(DRIVE, it) }
        }
        record.forEach { (key, value) -> require((key.length + 1 + value.toByteArray().size) <= MAX_ENTRY_BYTES) { "TXT entry $key is too long" } }
        return record
    }

    companion object {
        const val SERVICE_TYPE = "_goatfarm._tcp"
        private const val FARM_ID = "fid"
        private const val FARM_NAME = "fn"
        private const val PROTOCOL = "pv"
        private const val GENERATION = "gen"
        private const val KEY_ID = "kid"
        private const val PAIRING = "pid"
        private const val DRIVE = "drv"
        val ALLOWED_KEYS = setOf(FARM_ID, FARM_NAME, PROTOCOL, GENERATION, KEY_ID, PAIRING, DRIVE)
        private const val MAX_ENTRY_BYTES = 255
        private const val MAX_NAME = 60

        /**
         * Reads an advertised record. A record with keys outside the closed set is refused as
         * non-conforming, so a device that tries to broadcast secrets is not treated as a farm peer.
         */
        fun fromTxtRecord(record: Map<String, String>): FarmDiscoveryDescriptor? {
            if (!ALLOWED_KEYS.containsAll(record.keys)) return null
            return FarmDiscoveryDescriptor(
                farmId = record[FARM_ID]?.takeIf { it.isNotBlank() } ?: return null,
                farmName = record[FARM_NAME].orEmpty(),
                protocolVersion = record[PROTOCOL]?.toIntOrNull() ?: return null,
                syncGeneration = record[GENERATION]?.toLongOrNull() ?: return null,
                currentKeyId = record[KEY_ID]?.takeIf { it.isNotBlank() } ?: return null,
                pairingFingerprint = record[PAIRING]?.takeIf { it.isNotBlank() } ?: return null,
                driveFolderId = record[DRIVE]?.takeIf { it.isNotBlank() },
            )
        }
    }
}

/** A new device asking to join a farm. Its public key is what the farm key will be wrapped to. */
class EnrolmentRequest(
    val requestId: String,
    val farmId: String,
    val deviceId: String,
    val deviceName: String,
    devicePublicKey: ByteArray,
    nonce: ByteArray,
    val requestedAtEpochMillis: Long,
    /** Present when the device scanned a pairing QR code instead of comparing codes. */
    val invitationToken: String? = null,
) {
    val devicePublicKey: ByteArray = devicePublicKey.copyOf()
    val nonce: ByteArray = nonce.copyOf()
}

/**
 * The six-digit code both screens show during pairing. It binds the farm's pairing identity, the new
 * device's key and the request nonce, so a device in the middle on the LAN produces a different code.
 */
object PairingCode {
    fun of(farmPairingFingerprint: String, devicePublicKey: ByteArray, nonce: ByteArray): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(
            canonical(listOf("goat-pairing-code-v1", farmPairingFingerprint, B64.encodeToString(devicePublicKey), B64.encodeToString(nonce))),
        )
        val value = ((digest[0].toLong() and 0xff) shl 24) or ((digest[1].toLong() and 0xff) shl 16) or
            ((digest[2].toLong() and 0xff) shl 8) or (digest[3].toLong() and 0xff)
        return (value % 1_000_000).toString().padStart(6, '0')
    }
}

/** A single-use QR pairing invitation issued by an account allowed to approve devices. */
data class PairingInvitation(
    val farmId: String,
    val token: String,
    val issuedByAccountId: String,
    val expiresAtEpochMillis: Long,
) {
    /** QR payload: identifies the farm and carries the one-time token; no key or credential. */
    fun qrPayload(pairingFingerprint: String): String = "goatfarm-pair:v1:$farmId:$pairingFingerprint:$token"
}

/** What an approved device receives: farm identity, keys wrapped to it alone, and sync configuration. */
class DeviceGrant(
    val farmId: String,
    val deviceId: String,
    val deviceName: String,
    val approvedByAccountId: String,
    val approvedAtEpochMillis: Long,
    val currentKeyId: String,
    val wrappedKeys: List<WrappedFarmKey>,
    val driveFolderId: String?,
)

sealed interface PairingDecision {
    data class Approved(val grant: DeviceGrant) : PairingDecision

    data class Rejected(val reason: PairingRejection) : PairingDecision
}

enum class PairingRejection {
    WRONG_FARM,
    APPROVER_NOT_AUTHORISED,
    CODE_MISMATCH,
    REQUEST_EXPIRED,
    REQUEST_REPLAYED,
    INVITATION_INVALID,
    DEVICE_REVOKED,
    DEVICE_ALREADY_ENROLLED,
}

/**
 * The farm side of pairing. Discovery only says a farm exists; this decides whether a device is
 * authorised. Approval needs either an authorised account confirming the code shown on the new device,
 * or a valid, unexpired, single-use QR invitation issued by such an account. Approval registers the
 * device and wraps every farm key to the device's own public key.
 */
class PairingAuthority(
    private val farmId: String,
    private val pairingFingerprint: String,
    private val registry: DeviceRegistry,
    private val clock: () -> Long,
    private val random: SecureRandom = SecureRandom(),
) {
    private val seenRequests = mutableSetOf<String>()
    private val invitations = mutableMapOf<String, PairingInvitation>()

    fun issueInvitation(approverAccountId: String, approverMayApprove: Boolean): PairingInvitation {
        check(approverMayApprove) { "This account cannot approve new devices" }
        val token = B64.encodeToString(ByteArray(INVITATION_TOKEN_BYTES).also(random::nextBytes))
        return PairingInvitation(farmId, token, approverAccountId, clock() + INVITATION_LIFETIME_MILLIS).also { invitations[token] = it }
    }

    /** Approval by an account confirming, on the approving device, the code shown on the new device. */
    fun approveByCode(
        request: EnrolmentRequest,
        approverAccountId: String,
        approverMayApprove: Boolean,
        confirmedCode: String,
        keys: FarmKeyRing,
        driveFolderId: String? = null,
    ): PairingDecision {
        screen(request)?.let { return PairingDecision.Rejected(it) }
        if (!approverMayApprove) return PairingDecision.Rejected(PairingRejection.APPROVER_NOT_AUTHORISED)
        if (confirmedCode != PairingCode.of(pairingFingerprint, request.devicePublicKey, request.nonce)) {
            return PairingDecision.Rejected(PairingRejection.CODE_MISMATCH)
        }
        return grant(request, approverAccountId, keys, driveFolderId)
    }

    /** Approval through a scanned QR invitation. The token is consumed whether or not the rest succeeds. */
    fun approveByInvitation(request: EnrolmentRequest, keys: FarmKeyRing, driveFolderId: String? = null): PairingDecision {
        val invitation = request.invitationToken?.let { invitations.remove(it) }
        if (invitation == null || invitation.expiresAtEpochMillis < clock()) return PairingDecision.Rejected(PairingRejection.INVITATION_INVALID)
        screen(request)?.let { return PairingDecision.Rejected(it) }
        return grant(request, invitation.issuedByAccountId, keys, driveFolderId)
    }

    private fun screen(request: EnrolmentRequest): PairingRejection? {
        if (request.farmId != farmId) return PairingRejection.WRONG_FARM
        if (!seenRequests.add(request.requestId)) return PairingRejection.REQUEST_REPLAYED
        val age = clock() - request.requestedAtEpochMillis
        if (age > REQUEST_LIFETIME_MILLIS || age < -CLOCK_SKEW_MILLIS) return PairingRejection.REQUEST_EXPIRED
        val existing = registry.device(request.deviceId)
        if (existing?.status == DeviceStatus.LOST_REVOKED || existing?.status == DeviceStatus.RETIRED) return PairingRejection.DEVICE_REVOKED
        if (existing != null) return PairingRejection.DEVICE_ALREADY_ENROLLED
        return null
    }

    private fun grant(request: EnrolmentRequest, approverAccountId: String, keys: FarmKeyRing, driveFolderId: String?): PairingDecision {
        val devicePublic = DeviceKeys.decode(request.devicePublicKey)
        registry.register(FarmDevice(request.deviceId, request.deviceName, DeviceStatus.ACTIVE))
        return PairingDecision.Approved(
            DeviceGrant(
                farmId = farmId,
                deviceId = request.deviceId,
                deviceName = request.deviceName,
                approvedByAccountId = approverAccountId,
                approvedAtEpochMillis = clock(),
                currentKeyId = keys.currentKeyId,
                wrappedKeys = keys.all().map { FarmKeyWrap.wrap(it, devicePublic, farmId, request.deviceId, random) },
                driveFolderId = driveFolderId,
            ),
        )
    }

    /**
     * Revokes a lost device: operations above what the farm holds are refused from now on, and the farm
     * key is rotated so the device cannot read anything sealed afterwards. The caller distributes the
     * new key to the remaining devices with [rewrapFor].
     */
    fun revoke(deviceId: String, farmVector: SyncVector, keys: FarmKeyRing, newKeyId: String): FarmKeyRing {
        registry.markLostOrRevoked(deviceId, farmVector)
        return keys.rotate(newKeyId, random)
    }

    /** Wraps [key] to a remaining active device after rotation. Revoked devices are refused. */
    fun rewrapFor(deviceId: String, devicePublicKey: ByteArray, key: FarmDataKey): WrappedFarmKey {
        check(registry.maySynchronise(deviceId)) { "Keys are not provisioned to a revoked or retired device" }
        return FarmKeyWrap.wrap(key, DeviceKeys.decode(devicePublicKey), farmId, deviceId, random)
    }

    companion object {
        const val REQUEST_LIFETIME_MILLIS = 10 * 60 * 1000L
        const val INVITATION_LIFETIME_MILLIS = 15 * 60 * 1000L
        private const val CLOCK_SKEW_MILLIS = 2 * 60 * 1000L
        private const val INVITATION_TOKEN_BYTES = 18
    }
}

private val B64: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
