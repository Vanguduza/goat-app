package com.farmos.domain.replication

import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.SecureRandom
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Pairing over real loopback sockets: the new device sends its request, a person on the farm device types
 * the code shown on the new device, and the grant carries farm keys only the new device can unwrap.
 */
class PairingWireTest {
    private val farmId = "farm-1"
    private val loopback = InetAddress.getLoopbackAddress()
    private val farmIdentity = DeviceKeys.generate()
    private val fingerprint = DeviceKeys.fingerprint(DeviceKeys.encode(farmIdentity.public))
    private val registry = DeviceRegistry(listOf(FarmDevice("owner-phone", "Owner phone", DeviceStatus.ACTIVE)))
    private val authority = PairingAuthority(farmId, fingerprint, registry, System::currentTimeMillis)
    private val keys = FarmKeyRing(listOf(FarmDataKey.generate("k1")), "k1")
    private val servers = mutableListOf<PairingServer>()

    @AfterTest
    fun tearDown() = servers.forEach { it.close() }

    private fun serve(decide: (PendingEnrolment) -> EnrolmentDecision) =
        PairingServer(authority, { keys }, { "drive-folder" }, decide = decide).start(InetSocketAddress(loopback, 0)).also { servers += it }

    private fun request(device: java.security.KeyPair, token: String? = null) = EnrolmentRequest(
        "req-${System.nanoTime()}", farmId, "tablet", "Tablet", DeviceKeys.encode(device.public),
        ByteArray(16).also(SecureRandom()::nextBytes), System.currentTimeMillis(), token,
    )

    @Test
    fun anApproverTypingTheCodeShownOnTheNewDeviceEnrolsIt() {
        val device = DeviceKeys.generate()
        val req = request(device)
        val shownOnNewDevice = PairingCode.of(fingerprint, req.devicePublicKey, req.nonce)
        var askedAbout: String? = null
        val server = serve { pending ->
            askedAbout = pending.deviceName
            EnrolmentDecision.Approve("owner-1", approverMayApprove = true, codeShownOnNewDevice = shownOnNewDevice)
        }

        val outcome = PairingClient.requestEnrolment(loopback.hostAddress, server.port, req)

        val grant = assertIs<EnrolmentOutcome.Granted>(outcome).grant
        assertEquals("Tablet", askedAbout)
        assertEquals("drive-folder", grant.driveFolderId)
        assertEquals(DeviceStatus.ACTIVE, registry.device("tablet")?.status)
        assertEquals(setOf("owner-phone", "tablet"), grant.devices.map { it.deviceId }.toSet())
        val key = FarmKeyWrap.unwrap(grant.wrappedKeys.single(), device, farmId, "tablet")
        assertContentEquals(keys.current.material(), key.material())
    }

    @Test
    fun aMistypedCodeOrADeclineRefusesAndEnrolsNothing() {
        val req = request(DeviceKeys.generate())
        val wrong = if (PairingCode.of(fingerprint, req.devicePublicKey, req.nonce) == "000000") "000001" else "000000"
        val server = serve { EnrolmentDecision.Approve("owner-1", true, wrong) }
        assertEquals(EnrolmentOutcome.Refused("CODE_MISMATCH"), PairingClient.requestEnrolment(loopback.hostAddress, server.port, req))

        val declining = serve { EnrolmentDecision.Decline }
        assertEquals(EnrolmentOutcome.Refused("DECLINED"), PairingClient.requestEnrolment(loopback.hostAddress, declining.port, request(DeviceKeys.generate())))
        assertNull(registry.device("tablet"))
    }

    @Test
    fun aScannedInvitationEnrolsWithoutAskingAndCannotBeReused() {
        var asked = false
        val server = serve { asked = true; EnrolmentDecision.Decline }
        val invitation = authority.issueInvitation("owner-1", approverMayApprove = true)

        val first = PairingClient.requestEnrolment(loopback.hostAddress, server.port, request(DeviceKeys.generate(), invitation.token))
        assertIs<EnrolmentOutcome.Granted>(first)
        val second = PairingClient.requestEnrolment(loopback.hostAddress, server.port, request(DeviceKeys.generate(), invitation.token))
        assertEquals(EnrolmentOutcome.Refused("INVITATION_INVALID"), second)
        assertEquals(false, asked)
    }
}
