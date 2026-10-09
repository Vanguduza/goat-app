package com.farmos.app

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.ApplicationState
import com.farmos.core.database.replicationVector
import com.farmos.core.database.toEnvelope
import com.farmos.domain.replication.FarmKeyUnavailable
import com.farmos.domain.replication.OperationBundle
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-rUS", application = Application::class)
class DriveKeyRotationDeliveryTest {
    @Test
    fun anOfflinePeerRecoversMultipleRotationsWithoutExposingLaterBusinessRecords(): Unit = runBlocking {
        val farm = UUID.randomUUID().toString()
        val carrier = MemoryDriveCarrier()
        DriveRotationTestDevice(farm, "A", carrier).use { a ->
            DriveRotationTestDevice(farm, "B", carrier).use { b ->
                a.pairWith(b)
                val oldKeys = b.secrets().keys
                assertEquals(DriveDeliveryStatus.COMPLETED, a.runtime.synchroniseOnce())
                assertEquals(DriveDeliveryStatus.COMPLETED, b.runtime.synchroniseOnce())
                val originals = carrier.objects.mapValues { it.value.copyOf() }

                a.local.rotate(a.now + 100)
                val first = a.secrets().keys.currentKeyId
                a.database.setFarmCurrency(farm, "EUR", "owner", "A", a.now + 101)
                a.local.rotate(a.now + 200)
                val second = a.secrets().keys.currentKeyId
                a.database.setFarmCurrency(farm, "GBP", "owner", "A", a.now + 201)
                assertEquals(DriveDeliveryStatus.COMPLETED, a.runtime.synchroniseOnce())
                assertEquals("k1", b.secrets().keys.currentKeyId)

                val newOrdinary = carrier.objects.filterKeys { "/sync/A/" in it && it !in originals }
                assertTrue(newOrdinary.isNotEmpty())
                newOrdinary.forEach { (path, bytes) ->
                    try {
                        DrivePayloadCodec.open(oldKeys, path, bytes)
                        fail("Post-rotation business data must require a new key")
                    } catch (_: FarmKeyUnavailable) {
                        // The separately signed bootstrap is the only old-key-readable new payload.
                    }
                }
                var oldReadableRotations = 0
                carrier.objects.filterKeys { "/key-bootstrap/" in it }.forEach { (path, bytes) ->
                    val plaintext = try { DrivePayloadCodec.open(oldKeys, path, bytes) } catch (_: FarmKeyUnavailable) { null }
                    if (plaintext != null) {
                        val attestation = DriveRotationBootstrapCodec.decode(path, plaintext, farm)
                        assertEquals(1, attestation.bundle.operations.size)
                        assertTrue(attestation.bundle.operations.single().operationType in DRIVE_KEY_BOOTSTRAP_COMMANDS)
                        assertFalse(plaintext.toString(Charsets.UTF_8).contains("currencyCode"))
                        if (attestation.bundle.operations.single().operationType == KEY_ROTATED_COMMAND) oldReadableRotations++
                    }
                }
                assertTrue(oldReadableRotations >= 2)
                originals.forEach { (path, bytes) -> assertArrayEquals(bytes, carrier.objects.getValue(path)) }
                val published = carrier.objects.mapValues { it.value.copyOf() }
                assertEquals(DriveDeliveryStatus.COMPLETED, a.runtime.synchroniseOnce())
                assertEquals(published.keys, carrier.objects.keys)
                published.forEach { (path, bytes) -> assertArrayEquals(bytes, carrier.objects.getValue(path)) }

                assertEquals(DriveDeliveryStatus.COMPLETED, b.runtime.synchroniseOnce())
                assertEquals(setOf("k1", first, second), b.secrets().keys.keyIds)
                assertEquals(second, b.secrets().keys.currentKeyId)
                assertEquals("GBP", b.database.farmCurrency(farm))
                assertArrayEquals(a.secrets().keys.current.materialForVault(), b.secrets().keys.current.materialForVault())
                assertEquals(a.database.replicationVector(farm), b.database.replicationVector(farm))
            }
        }
    }

    @Test
    fun independentlyRotatedDevicesConvergeThroughTheActualDriveRuntime(): Unit = runBlocking {
        val farm = UUID.randomUUID().toString()
        val carrier = MemoryDriveCarrier()
        DriveRotationTestDevice(farm, "A", carrier).use { a ->
            DriveRotationTestDevice(farm, "B", carrier).use { b ->
                a.pairWith(b)
                val one = async(Dispatchers.IO) { a.local.rotate(a.now + 100) }
                val two = async(Dispatchers.IO) { b.local.rotate(b.now + 100) }
                one.await()
                two.await()
                val aKey = a.secrets().keys.currentKeyId
                val bKey = b.secrets().keys.currentKeyId
                assertNotEquals(aKey, bKey)
                a.database.setFarmCurrency(farm, "EUR", "owner", "A", a.now + 101)
                b.database.setFarmCurrency(farm, "GBP", "owner", "B", b.now + 102)

                assertEquals(DriveDeliveryStatus.COMPLETED, a.runtime.synchroniseOnce())
                assertEquals(DriveDeliveryStatus.COMPLETED, b.runtime.synchroniseOnce())
                assertEquals(DriveDeliveryStatus.COMPLETED, a.runtime.synchroniseOnce())
                val expected = setOf("k1", aKey, bKey)
                assertEquals(expected, a.secrets().keys.keyIds)
                assertEquals(expected, b.secrets().keys.keyIds)
                assertEquals(maxOf(aKey, bKey), a.secrets().keys.currentKeyId)
                assertEquals(a.secrets().keys.currentKeyId, b.secrets().keys.currentKeyId)
                assertEquals("GBP", a.database.farmCurrency(farm))
                assertEquals("GBP", b.database.farmCurrency(farm))
                val fromA = a.local.committedRotation(aKey)
                val fromB = b.local.committedRotation(bKey)
                assertEquals(ApplicationState.APPLIED.name, b.database.replicationApplications().get(farm, fromA.operationId)?.state)
                assertEquals(ApplicationState.APPLIED.name, a.database.replicationApplications().get(farm, fromB.operationId)?.state)
                assertEquals(a.database.replicationVector(farm), b.database.replicationVector(farm))
            }
        }
    }

    @Test
    fun aSingletonBootstrapDoesNotPretendThatMissingOriginSequencesAreBackedUp(): Unit = runBlocking {
        val farm = UUID.randomUUID().toString()
        val carrier = MemoryDriveCarrier()
        DriveRotationTestDevice(farm, "A", carrier).use { a ->
            DriveRotationTestDevice(farm, "B", carrier).use { b ->
                a.pairWith(b)
                a.local.rotate(a.now + 100)
                val keyId = a.secrets().keys.currentKeyId
                val operation = a.local.committedRotation(keyId)
                assertTrue(operation.deviceSequence > 1)
                assertEquals(DriveDeliveryStatus.COMPLETED, a.runtime.synchroniseOnce())
                val normal = carrier.objects.filterKeys { "/sync/" in it }.mapValues { it.value.copyOf() }
                normal.keys.forEach { carrier.objects.remove(it) }
                // Simulate a partial upload containing rotations but still lacking their earlier enrolment.
                carrier.objects.entries.removeIf { (path, bytes) ->
                    "/key-bootstrap/" in path && DriveRotationBootstrapCodec.decode(path,
                        DrivePayloadCodec.open(a.secrets().keys, path, bytes), farm).bundle.operations.single().operationType == DEVICE_ENROLLED_COMMAND
                }

                assertEquals(DriveDeliveryStatus.COMPLETED, b.runtime.synchroniseOnce())
                assertEquals(keyId, b.secrets().keys.currentKeyId)
                assertEquals(operation, b.database.replication().operation(farm, operation.operationId)?.toEnvelope())
                assertEquals(0L, b.database.replicationVector(farm).watermark("A"))
                val config = requireNotNull(DriveConfigStore(b.context).get(farm))
                assertEquals(0L, DriveCursorStore(b.context).load(farm, config).verifiedThrough["A"] ?: 0L)

                carrier.objects.putAll(normal)
                assertEquals(DriveDeliveryStatus.COMPLETED, b.runtime.synchroniseOnce())
                assertEquals(operation.deviceSequence, b.database.replicationVector(farm).watermark("A"))
            }
        }
    }

    @Test
    fun anExactAlreadyAdmittedLanRotationRemainsCompatibleWithoutABootstrapCopy(): Unit = runBlocking {
        val farm = UUID.randomUUID().toString()
        val carrier = MemoryDriveCarrier()
        DriveRotationTestDevice(farm, "A", carrier).use { a ->
            DriveRotationTestDevice(farm, "B", carrier).use { b ->
                a.pairWith(b)
                a.local.rotate(a.now + 100)
                val rotation = a.local.committedRotation(a.secrets().keys.currentKeyId)
                val lanEndpoint = RoomReplicaEndpoint(b.database, farm, "B", farmAppliers(b.vault, "B"))
                assertFalse(lanEndpoint.ingest(OperationBundle.seal(farm, "A", listOf(rotation))).rejected)
                val rows = a.database.replication().operationsInRange(farm, "A", 1, rotation.deviceSequence).map { it.toEnvelope() }
                val normal = OperationBundle.seal(farm, "A", rows)
                DriveReplicationTransport(EncryptedDriveStore(carrier, farm) { a.secrets().keys }, farm).publish(farm, listOf(normal))
                // Runtime may publish its own attestation; the ordinary rotation was already accepted via LAN.
                assertEquals(DriveDeliveryStatus.COMPLETED, b.runtime.synchroniseOnce())
                assertEquals(rotation.deviceSequence, b.database.replicationVector(farm).watermark("A"))
                assertEquals(rotation, b.database.replication().operation(farm, rotation.operationId)?.toEnvelope())
            }
        }
    }
    @Test
    fun aSignedEnrollmentFromAKnownGatewayCanIntroduceTheNextRotationSigner(): Unit = runBlocking {
        val farm = UUID.randomUUID().toString()
        val carrier = MemoryDriveCarrier()
        DriveRotationTestDevice(farm, "z-approver", carrier).use { approver ->
            DriveRotationTestDevice(farm, "old-offline", carrier).use { offline ->
                DriveRotationTestDevice(farm, "a-new", carrier, provision = false).use { newcomer ->
                    approver.pairWith(offline)
                    val identity = com.farmos.domain.replication.DeviceKeys.generate()
                    val held = approver.secrets()
                    val publicKey = java.util.Base64.getEncoder().encodeToString(identity.public.encoded)
                    val devices = approver.database.farmDevices(farm) + com.farmos.domain.replication.FarmDevice(
                        newcomer.device, "New tablet", com.farmos.domain.replication.DeviceStatus.ACTIVE, 0, null, publicKey,
                    )
                    val grant = com.farmos.domain.replication.DeviceGrant(
                        farm, newcomer.device, "New tablet", "owner", approver.now, held.keys.currentKeyId,
                        held.keys.all().map { com.farmos.domain.replication.FarmKeyWrap.wrap(it, identity.public, farm, newcomer.device) },
                        "test-folder", devices,
                    )
                    approver.database.enrolPairedDevice(grant, identity.public.encoded, approver.device, approver.now + 10)
                    newcomer.database.installGrant(grant, identity, newcomer.device, newcomer.vault)
                    assertEquals(null, offline.database.replication().device(farm, newcomer.device))
                    assertEquals(DriveDeliveryStatus.COMPLETED, approver.runtime.synchroniseOnce())

                    newcomer.local.rotate(newcomer.now + 100)
                    val newKey = newcomer.secrets().keys.currentKeyId
                    newcomer.database.setFarmCurrency(farm, "ZAR", "owner", newcomer.device, newcomer.now + 101)
                    assertEquals(DriveDeliveryStatus.COMPLETED, newcomer.runtime.synchroniseOnce())
                    // The newcomer's bootstrap paths sort first, so the importer must retry after the
                    // known approver's signed enrollment establishes its public key.
                    assertEquals(DriveDeliveryStatus.COMPLETED, offline.runtime.synchroniseOnce())
                    assertEquals(publicKey, offline.database.replication().device(farm, newcomer.device)?.publicKey)
                    assertEquals(newKey, offline.secrets().keys.currentKeyId)
                    assertEquals("ZAR", offline.database.farmCurrency(farm))
                    val enrollment = approver.database.replication().operationsForEntity(farm, "farm_device", newcomer.device).single()
                    assertEquals(ApplicationState.APPLIED.name, offline.database.replicationApplications().get(farm, enrollment.operationId)?.state)
                }
            }
        }
    }

}
