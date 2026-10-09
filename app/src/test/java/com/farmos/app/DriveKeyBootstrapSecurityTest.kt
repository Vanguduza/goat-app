package com.farmos.app

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.COMMAND_PAYLOAD_KEY
import com.farmos.core.database.toEnvelope
import com.farmos.domain.replication.CommandMergeClassification
import com.farmos.domain.replication.DeviceKeys
import com.farmos.domain.replication.FarmDataKey
import com.farmos.domain.replication.FarmKeyWrap
import com.farmos.domain.replication.OperationBundle
import com.farmos.domain.replication.OperationEnvelope
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-rUS", application = Application::class)
class DriveKeyBootstrapSecurityTest {
    @Test
    fun anUnattestedRotationInsideAnOrdinaryBundleCannotOccupyAnyOriginPosition(): Unit = runBlocking {
        val farm = UUID.randomUUID().toString()
        val carrier = MemoryDriveCarrier()
        DriveRotationTestDevice(farm, "A", carrier).use { a ->
            DriveRotationTestDevice(farm, "B", carrier).use { b ->
                a.pairWith(b)
                val original = a.database.replication().operationsInRange(farm, "A", 1, 1).single().toEnvelope()
                val forged = forgedRotation(b, "A", 2)
                val ordinary = OperationBundle.seal(farm, "A", listOf(original, forged))
                DriveReplicationTransport(EncryptedDriveStore(carrier, farm) { a.secrets().keys }, farm).publish(farm, listOf(ordinary))

                assertEquals(DriveDeliveryStatus.FAILED, b.runtime.synchroniseOnce())
                assertTrue(b.runtime.state.value.lastError.orEmpty().contains("attestation"))
                assertNull(b.database.replication().operation(farm, forged.operationId))
                assertNull(b.database.replication().operation(farm, original.operationId))
                assertEquals(0, b.database.replication().operationsInRange(farm, "A", 1, 2).size)
                assertEquals("k1", b.secrets().keys.currentKeyId)
            }
        }
    }

    @Test
    fun unknownRevokedCutoffSpoofedAndTamperedSignersCannotAdmitAKey(): Unit = runBlocking {
        for (attack in listOf("unknown", "revoked", "cutoff", "spoofed", "tampered")) {
            val farm = UUID.randomUUID().toString()
            val carrier = MemoryDriveCarrier()
            DriveRotationTestDevice(farm, "A", carrier).use { a ->
                DriveRotationTestDevice(farm, "B", carrier).use { b ->
                    KeyRotationTestFixture(farm, "gateway").use { gateway ->
                        a.pairWith(b)
                        if (attack != "unknown") {
                            b.local.knows(gateway)
                            val row = requireNotNull(b.database.replication().device(farm, "gateway"))
                            if (attack == "revoked") b.database.replication().upsertDevice(row.copy(status = "LOST_REVOKED", revokedAfterSequence = 0))
                            if (attack == "cutoff") b.database.replication().upsertDevice(row.copy(status = "ACTIVE", revokedAfterSequence = 0))
                        }
                        val forged = forgedRotation(b, "A", 2)
                        val singleton = OperationBundle.seal(farm, "A", listOf(forged))
                        val path = DriveRotationBootstrapCodec.path(farm, "gateway", "k1", singleton)
                        val identity = if (attack == "spoofed") a.secrets().device else requireNotNull(gateway.vault.secrets(farm)).device
                        val bytes = DriveRotationBootstrapCodec.encode(path, "gateway", identity, singleton)
                        if (attack == "tampered") bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
                        carrier.objects[path] = DrivePayloadCodec.seal(a.secrets().keys, path, bytes)

                        assertEquals(attack, DriveDeliveryStatus.FAILED, b.runtime.synchroniseOnce())
                        assertNull(attack, b.database.replication().operation(farm, forged.operationId))
                        assertEquals(attack, "k1", b.secrets().keys.currentKeyId)
                        assertTrue(attack, b.runtime.state.value.lastError.orEmpty().contains("bootstrap"))
                    }
                }
            }
        }
    }

    @Test
    fun signerRevocationAfterDownloadIsCheckedInsideTheJournalTransaction(): Unit = runBlocking {
        val farm = UUID.randomUUID().toString()
        val carrier = MemoryDriveCarrier()
        DriveRotationTestDevice(farm, "A", carrier).use { a ->
            DriveRotationTestDevice(farm, "B", carrier).use { b ->
                KeyRotationTestFixture(farm, "gateway").use { gateway ->
                    a.pairWith(b)
                    b.local.knows(gateway)
                    val operation = forgedRotation(b, "A", 2)
                    val bundle = OperationBundle.seal(farm, "A", listOf(operation))
                    val path = DriveRotationBootstrapCodec.path(farm, "gateway", "k1", bundle)
                    val bytes = DriveRotationBootstrapCodec.encode(path, "gateway", requireNotNull(gateway.vault.secrets(farm)).device, bundle)
                    carrier.objects[path] = DrivePayloadCodec.seal(a.secrets().keys, path, bytes)
                    val bootstrap = DriveKeyBootstrap(b.database, carrier, farm, "B", b::secrets)
                    var checkedInsideTransaction = false
                    val endpoint = RoomReplicaEndpoint(b.database, farm, "B", farmAppliers(b.vault, "B"), admission = { incoming ->
                        checkedInsideTransaction = b.database.inTransaction()
                        bootstrap.admitKeyPrerequisites(incoming)
                    })
                    carrier.onRead = {
                        val signer = requireNotNull(b.database.replication().device(farm, "gateway"))
                        b.database.replication().upsertDevice(signer.copy(revokedAfterSequence = 0))
                    }
                    try {
                        bootstrap.importReadable(endpoint, EncryptedDriveStore(carrier, farm) { b.secrets().keys })
                        fail("The signer lost permission before journal admission")
                    } catch (expected: java.io.IOException) {
                        assertTrue(expected.message.orEmpty().contains("permitted"))
                    }
                    assertTrue(checkedInsideTransaction)
                    assertNull(b.database.replication().operation(farm, operation.operationId))
                    assertEquals(0, b.database.replication().operationsInRange(farm, "A", 2, 2).size)
                    assertEquals("k1", b.secrets().keys.currentKeyId)
                }
            }
        }
    }

    @Test
    fun theSignatureBindsThePathFarmSignerAndExactOperationBytes(): Unit = runBlocking {
        val farm = UUID.randomUUID().toString()
        val carrier = MemoryDriveCarrier()
        DriveRotationTestDevice(farm, "A", carrier).use { a ->
            DriveRotationTestDevice(farm, "B", carrier).use { b ->
                a.pairWith(b)
                val bundle = OperationBundle.seal(farm, "A", listOf(forgedRotation(b, "A", 2)))
                val path = DriveRotationBootstrapCodec.path(farm, "A", "k1", bundle)
                val bytes = DriveRotationBootstrapCodec.encode(path, "A", a.secrets().device, bundle)
                val valid = DriveRotationBootstrapCodec.decode(path, bytes, farm)
                assertTrue(DriveRotationBootstrapCodec.authentic(valid, a.secrets().device.public))
                val moved = path.replace("/k1/", "/another-retained-key/")
                assertFalse(DriveRotationBootstrapCodec.authentic(DriveRotationBootstrapCodec.decode(moved, bytes, farm), a.secrets().device.public))
                assertFalse(DriveRotationBootstrapCodec.authentic(valid, b.secrets().device.public))
                try {
                    DriveRotationBootstrapCodec.decode(path, bytes, UUID.randomUUID().toString())
                    fail("Another farm must not accept the bootstrap")
                } catch (_: IllegalArgumentException) {
                    // The signed farm and the endpoint farm are independently bound.
                }
                val changed = valid.encodedBundle.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
                assertFalse(DriveRotationBootstrapCodec.authentic(valid.copy(encodedBundle = changed), a.secrets().device.public))
            }
        }
    }

    @Test
    fun anUnsignedEnrollmentCannotManufactureTheSignerForAFabricatedRotation(): Unit = runBlocking {
        for (existingNullKey in listOf(false, true)) {
            val farm = UUID.randomUUID().toString()
            val carrier = MemoryDriveCarrier()
            DriveRotationTestDevice(farm, "A", carrier).use { a ->
                DriveRotationTestDevice(farm, "B", carrier).use { b ->
                    a.pairWith(b)
                    val attacker = DeviceKeys.generate()
                    if (existingNullKey) {
                        b.database.replication().upsertDevice(com.farmos.core.database.ReplicationDeviceEntity(
                            farm, "attacker", "Unbound old entry", "ACTIVE", 0, null, false, null,
                        ))
                    }
                    val original = a.database.replication().operationsInRange(farm, "A", 1, 1).single().toEnvelope()
                    val enrollment = operation(
                        farm, "A", 2, DEVICE_ENROLLED_COMMAND, "farm_device", "attacker",
                        JSONObject().put("deviceId", "attacker").put("name", "Fabricated device")
                            .put("publicKey", Base64.getEncoder().encodeToString(attacker.public.encoded)).toString(),
                    )
                    val rotation = forgedRotation(b, "A", 3)
                    val ordinary = OperationBundle.seal(farm, "A", listOf(original, enrollment))
                    DriveReplicationTransport(EncryptedDriveStore(carrier, farm) { a.secrets().keys }, farm).publish(farm, listOf(ordinary))
                    val singleton = OperationBundle.seal(farm, "A", listOf(rotation))
                    val path = DriveRotationBootstrapCodec.path(farm, "attacker", "k1", singleton)
                    carrier.objects[path] = DrivePayloadCodec.seal(a.secrets().keys, path,
                        DriveRotationBootstrapCodec.encode(path, "attacker", attacker, singleton))

                    assertEquals(DriveDeliveryStatus.FAILED, b.runtime.synchroniseOnce())
                    assertNull(b.database.replication().operation(farm, enrollment.operationId))
                    assertNull(b.database.replication().operation(farm, rotation.operationId))
                    assertNull(b.database.replication().device(farm, "attacker")?.publicKey)
                    assertEquals("k1", b.secrets().keys.currentKeyId)
                }
            }
        }
    }

    private fun forgedRotation(recipient: DriveRotationTestDevice, origin: String, sequence: Long): OperationEnvelope {
        val key = FarmDataKey.generate("k-" + UUID.randomUUID())
        val wrapped = FarmKeyWrap.wrap(key, recipient.secrets().device.public, recipient.farm, recipient.device)
        fun b64(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)
        val payload = JSONObject().put("keyId", key.keyId).put("wrapped", JSONObject().put(recipient.device,
            JSONObject().put("epk", b64(wrapped.ephemeralPublicKey)).put("nonce", b64(wrapped.nonce)).put("ct", b64(wrapped.ciphertext)),
        )).toString()
        return operation(recipient.farm, origin, sequence, KEY_ROTATED_COMMAND, "farm_key", key.keyId, payload)
    }

    private fun operation(farm: String, origin: String, sequence: Long, type: String, entityType: String, entityId: String, payload: String) =
        OperationEnvelope.seal(
            operationId = UUID.randomUUID().toString(), farmId = farm, entityType = entityType, entityId = entityId,
            actorId = "owner", deviceId = origin, deviceSequence = sequence,
            businessTimeEpochMillis = 1_800_000_000_100L + sequence, createdAtEpochMillis = 1_800_000_000_100L + sequence,
            baseVersion = null, operationType = type, mergeClass = CommandMergeClassification.forCommand(type),
            payload = mapOf(COMMAND_PAYLOAD_KEY to payload), schemaVersion = 1, provenance = "local",
        )
}
