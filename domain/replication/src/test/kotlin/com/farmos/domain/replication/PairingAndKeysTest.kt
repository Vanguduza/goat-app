package com.farmos.domain.replication

import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PairingAndKeysTest {
    private val farmId = "farm-1"
    private var now = 1_790_000_000_000L
    private val farmIdentity = DeviceKeys.generate()
    private val fingerprint = DeviceKeys.fingerprint(DeviceKeys.encode(farmIdentity.public))
    private val registry = DeviceRegistry(listOf(FarmDevice("owner-phone", "Owner phone", DeviceStatus.ACTIVE)))
    private val authority = PairingAuthority(farmId, fingerprint, registry, { now })
    private var keys = FarmKeyRing(listOf(FarmDataKey.generate("k1")), "k1")

    private fun request(deviceId: String, device: java.security.KeyPair, requestId: String = "req-$deviceId", token: String? = null) =
        EnrolmentRequest(requestId, farmId, deviceId, "Tablet", DeviceKeys.encode(device.public), ByteArray(16).also(SecureRandom()::nextBytes), now, token)

    private fun codeFor(request: EnrolmentRequest) = PairingCode.of(fingerprint, request.devicePublicKey, request.nonce)

    @Test
    fun discoveryAdvertisesOnlyTheClosedSetOfNonSecretFields() {
        val descriptor = FarmDiscoveryDescriptor(farmId, "Premier Farm", 1, 7, "k1", fingerprint, "drive-folder")
        val record = descriptor.toTxtRecord()
        assertTrue(FarmDiscoveryDescriptor.ALLOWED_KEYS.containsAll(record.keys))
        assertEquals(descriptor, FarmDiscoveryDescriptor.fromTxtRecord(record))
        assertNull(FarmDiscoveryDescriptor.fromTxtRecord(record + ("token" to "secret")))
        assertNull(FarmDiscoveryDescriptor.fromTxtRecord(record - "fid"))
        assertEquals("_goatfarm._tcp", FarmDiscoveryDescriptor.SERVICE_TYPE)
    }

    @Test
    fun theComparisonCodeIsSixDigitsAndChangesWhenAnyBoundValueChanges() {
        val device = DeviceKeys.generate()
        val req = request("tablet", device)
        val code = codeFor(req)
        assertTrue(Regex("[0-9]{6}").matches(code))
        assertEquals(code, PairingCode.of(fingerprint, req.devicePublicKey, req.nonce))
        val intruder = DeviceKeys.generate()
        assertNotEquals(code, PairingCode.of(fingerprint, DeviceKeys.encode(intruder.public), req.nonce))
        assertNotEquals(code, PairingCode.of(DeviceKeys.fingerprint(DeviceKeys.encode(intruder.public)), req.devicePublicKey, req.nonce))
    }

    @Test
    fun anAuthorisedApproverConfirmingTheCodeEnrolsTheDeviceAndOnlyThatDeviceCanUnwrapTheKey() {
        val device = DeviceKeys.generate()
        val req = request("tablet", device)
        val decision = authority.approveByCode(req, "owner-1", approverMayApprove = true, confirmedCode = codeFor(req), keys = keys, driveFolderId = "drive-folder")
        val grant = assertIs<PairingDecision.Approved>(decision).grant
        assertEquals(DeviceStatus.ACTIVE, registry.device("tablet")?.status)
        assertEquals("owner-1", grant.approvedByAccountId)
        assertEquals("drive-folder", grant.driveFolderId)

        val unwrapped = FarmKeyWrap.unwrap(grant.wrappedKeys.single(), device, farmId, "tablet")
        assertContentEquals(keys.current.material(), unwrapped.material())
        assertFailsWith<Exception> { FarmKeyWrap.unwrap(grant.wrappedKeys.single(), DeviceKeys.generate(), farmId, "tablet") }
        assertFailsWith<AEADBadTagException> { FarmKeyWrap.unwrap(grant.wrappedKeys.single(), device, farmId, "other-device") }
    }

    @Test
    fun aWrongCodeAnUnauthorisedApproverAWrongFarmOrAReplayIsRejected() {
        val device = DeviceKeys.generate()
        val wrongCode = request("t1", device)
        val notTheCode = if (codeFor(wrongCode) == "000000") "000001" else "000000"
        assertEquals(PairingDecision.Rejected(PairingRejection.CODE_MISMATCH), authority.approveByCode(wrongCode, "owner-1", true, notTheCode, keys))
        assertNull(registry.device("t1"))

        val worker = request("t2", device)
        assertEquals(PairingDecision.Rejected(PairingRejection.APPROVER_NOT_AUTHORISED), authority.approveByCode(worker, "worker-1", false, codeFor(worker), keys))

        val otherFarm = EnrolmentRequest("req-t3", "farm-2", "t3", "Tablet", DeviceKeys.encode(device.public), ByteArray(16), now)
        assertEquals(PairingDecision.Rejected(PairingRejection.WRONG_FARM), authority.approveByCode(otherFarm, "owner-1", true, codeFor(otherFarm), keys))

        // A failed attempt burns the request, so the code cannot be guessed repeatedly against one request.
        assertEquals(PairingDecision.Rejected(PairingRejection.REQUEST_REPLAYED), authority.approveByCode(wrongCode, "owner-1", true, codeFor(wrongCode), keys))
    }

    @Test
    fun anExpiredRequestOrAnAlreadyEnrolledOrRevokedDeviceIsRejected() {
        val device = DeviceKeys.generate()
        val old = request("t1", device)
        now += PairingAuthority.REQUEST_LIFETIME_MILLIS + 1
        assertEquals(PairingDecision.Rejected(PairingRejection.REQUEST_EXPIRED), authority.approveByCode(old, "owner-1", true, codeFor(old), keys))

        val existing = request("owner-phone", device)
        assertEquals(PairingDecision.Rejected(PairingRejection.DEVICE_ALREADY_ENROLLED), authority.approveByCode(existing, "owner-1", true, codeFor(existing), keys))

        registry.markLostOrRevoked("owner-phone", SyncVector())
        val again = request("owner-phone", device, requestId = "req-again")
        assertEquals(PairingDecision.Rejected(PairingRejection.DEVICE_REVOKED), authority.approveByCode(again, "owner-1", true, codeFor(again), keys))
    }

    @Test
    fun aQrInvitationIsSingleUseExpiresAndNeedsAnAuthorisedIssuer() {
        assertFailsWith<IllegalStateException> { authority.issueInvitation("worker-1", approverMayApprove = false) }
        val invitation = authority.issueInvitation("owner-1", approverMayApprove = true)
        assertTrue(invitation.qrPayload(fingerprint).startsWith("goatfarm-pair:v1:$farmId:$fingerprint:"))

        val device = DeviceKeys.generate()
        val approved = authority.approveByInvitation(request("t1", device, token = invitation.token), keys)
        assertEquals("owner-1", assertIs<PairingDecision.Approved>(approved).grant.approvedByAccountId)
        assertEquals(
            PairingDecision.Rejected(PairingRejection.INVITATION_INVALID),
            authority.approveByInvitation(request("t2", device, token = invitation.token), keys),
        )

        val late = authority.issueInvitation("owner-1", approverMayApprove = true)
        now += PairingAuthority.INVITATION_LIFETIME_MILLIS + 1
        assertEquals(PairingDecision.Rejected(PairingRejection.INVITATION_INVALID), authority.approveByInvitation(request("t3", device, token = late.token), keys))
        assertEquals(PairingDecision.Rejected(PairingRejection.INVITATION_INVALID), authority.approveByInvitation(request("t4", device, token = null), keys))
    }

    @Test
    fun sealedPayloadsAreBoundToTheirContextAndTamperingIsDetected() {
        val aad = "farm-1|bundle-9".toByteArray()
        val sealed = FarmCipher.seal(keys.current, "weights".toByteArray(), aad)
        assertEquals("weights", String(FarmCipher.open(keys, sealed, aad)))
        assertFailsWith<AEADBadTagException> { FarmCipher.open(keys, sealed, "farm-1|bundle-10".toByteArray()) }
        val tampered = SealedPayload(sealed.keyId, sealed.nonce, sealed.ciphertext.also { it[0] = (it[0] + 1).toByte() })
        assertFailsWith<AEADBadTagException> { FarmCipher.open(keys, tampered, aad) }
    }

    @Test
    fun revokingALostDeviceRotatesTheKeySoItCannotReadLaterBundlesButOthersCan() {
        val lostKeys = DeviceKeys.generate()
        val keptKeys = DeviceKeys.generate()
        val lostReq = request("lost", lostKeys)
        val keptReq = request("kept", keptKeys)
        val lostGrant = assertIs<PairingDecision.Approved>(authority.approveByCode(lostReq, "owner-1", true, codeFor(lostReq), keys)).grant
        assertIs<PairingDecision.Approved>(authority.approveByCode(keptReq, "owner-1", true, codeFor(keptReq), keys))
        val lostRing = FarmKeyRing(lostGrant.wrappedKeys.map { FarmKeyWrap.unwrap(it, lostKeys, farmId, "lost") }, lostGrant.currentKeyId)

        val before = FarmCipher.seal(keys.current, "before".toByteArray(), byteArrayOf(1))
        keys = authority.revoke("lost", SyncVector(), keys, "k2")
        assertEquals("k2", keys.currentKeyId)
        val after = FarmCipher.seal(keys.current, "after".toByteArray(), byteArrayOf(2))

        assertEquals("before", String(FarmCipher.open(lostRing, before, byteArrayOf(1))))
        assertFailsWith<FarmKeyUnavailable> { FarmCipher.open(lostRing, after, byteArrayOf(2)) }
        assertFailsWith<IllegalStateException> { authority.rewrapFor("lost", lostReq.devicePublicKey, keys.current) }
        assertEquals(false, registry.accepts("lost", 1))

        val rewrapped = authority.rewrapFor("kept", keptReq.devicePublicKey, keys.current)
        val keptRing = FarmKeyRing(listOf(FarmKeyWrap.unwrap(rewrapped, keptKeys, farmId, "kept")), "k2")
        assertEquals("after", String(FarmCipher.open(keptRing, after, byteArrayOf(2))))
        assertFailsWith<IllegalArgumentException> { keys.rotate("k1") }
    }

    @Test
    fun hkdfMatchesTheRfc5869Sha256TestVector() {
        val ikm = ByteArray(22) { 0x0b }
        val salt = ByteArray(13) { it.toByte() }
        val info = ByteArray(10) { (0xf0 + it).toByte() }
        val okm = Hkdf.sha256(ikm, salt, info, 42).joinToString("") { "%02x".format(it) }
        assertEquals("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865", okm)
    }
}
