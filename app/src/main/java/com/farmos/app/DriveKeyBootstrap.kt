package com.farmos.app

import androidx.room.withTransaction
import com.farmos.core.database.ApplicationState
import com.farmos.core.database.COMMAND_PAYLOAD_KEY
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.toEnvelope
import com.farmos.domain.replication.DeviceKeys
import com.farmos.domain.replication.DeviceStatus
import com.farmos.domain.replication.FarmKeyRing
import com.farmos.domain.replication.FarmKeyUnavailable
import com.farmos.domain.replication.OperationBundle
import com.farmos.domain.replication.IngestResult
import com.farmos.domain.replication.OperationEnvelope
import java.io.IOException
import java.util.Base64
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

/**
 * Lets an authorised peer recover a wrapped new key before reading the normal new-key-encrypted log.
 * Only exact committed/applied rotations and their device-enrolment prerequisites are attested.
 * Retained-key copies contain no account credentials or business data; normal bundles are never resealed.
 */
internal class DriveKeyBootstrap(
    private val database: FarmOsDatabase,
    private val carrier: DriveObjectStore,
    private val farmId: String,
    private val deviceId: String,
    private val secrets: () -> FarmSecrets,
) {
    private val attestations = mutableMapOf<String, DriveRotationAttestation>()

    /** Runs inside RoomReplicaEndpoint's existing transaction, before any fresh journal row is written. */
    suspend fun admitKeyPrerequisites(bundle: OperationBundle): String? {
        check(database.inTransaction()) { "Drive rotation admission must share the journal transaction" }
        for (operation in bundle.operations.filter { it.operationType in DRIVE_KEY_BOOTSTRAP_COMMANDS }) {
            if (alreadyAccepted(operation) || matchesExistingIdentity(operation)) continue
            val attestation = attestations[operation.checksum]
                ?: return "Drive key prerequisite ${operation.operationId} needs an authenticated gateway attestation."
            if (attestation.bundle.operations.single() != operation) return "Drive rotation attestation does not match the exact operation."
            signerRefusal(attestation)?.let { return it }
        }
        return null
    }

    /** The carrier is ApprovedDriveStore, so every read/write repeats fresh local gateway permission. */
    suspend fun publishAvailable() {
        val rotations = database.withTransaction {
            database.replication().sequenceSpans(farmId).flatMap { span ->
                database.replication().operationsInRange(farmId, span.deviceId, 1, span.maxSequence)
            }.filter { it.operationType in DRIVE_KEY_BOOTSTRAP_COMMANDS }.mapNotNull { row ->
                val operation = row.toEnvelope()
                if (!alreadyAccepted(operation)) null else OperationBundle.seal(farmId, operation.deviceId, listOf(operation))
            }
        }
        for (bundle in rotations) {
            val held = secrets()
            val operation = bundle.operations.single()
            for (key in held.keys.all().filter { operation.operationType != KEY_ROTATED_COMMAND || it.keyId != operation.entityId }) {
                val path = DriveRotationBootstrapCodec.path(farmId, deviceId, key.keyId, bundle)
                val encrypted = EncryptedDriveStore(carrier, farmId) {
                    val latest = secrets().keys
                    FarmKeyRing(latest.all(), key.keyId)
                }
                val existing = encrypted.read(path)
                if (existing != null) {
                    requireMatching(existing, path, bundle, held.device.public)
                    continue
                }
                val bytes = DriveRotationBootstrapCodec.encode(path, deviceId, held.device, bundle)
                if (!encrypted.putIfAbsent(path, bytes, sha256Hex(bytes))) {
                    // Signatures and GCM use random values. A racing valid identical attestation is safe.
                    requireMatching(encrypted.read(path) ?: throw IOException("A Drive key bootstrap disappeared"), path, bundle, held.device.public)
                }
            }
        }
    }

    /**
     * Read history and signed prerequisites in passes: an enrolment attested by an already-known
     * gateway can admit a signer, then its rotation can unlock history. Ordinary shared-key ciphertext
     * never establishes a new signer identity. Gaps remain gaps in the normal vector.
     * Unreadable redundant key copies are ignored; unmet journal or signer dependencies stay visible.
     */
    suspend fun importReadable(endpoint: RoomReplicaEndpoint, store: DriveObjectStore): IngestResult {
        val ordinary = store.list("GOAT/farms/$farmId/sync/").sortedBy { it.path }.toMutableList()
        val rotations = carrier.list("GOAT/farms/$farmId/key-bootstrap/").sortedBy { it.path }.toMutableList()
        val reasons = mutableMapOf<String, String>()
        val missingKey = mutableSetOf<String>()
        var applied = 0
        var duplicates = 0
        var progress = true
        while (progress) {
            progress = false
            val rotationIterator = rotations.iterator()
            while (rotationIterator.hasNext()) {
                val obj = rotationIterator.next()
                try {
                    require(obj.path.startsWith("GOAT/farms/$farmId/key-bootstrap/") &&
                        DRIVE_ROTATION_BOOTSTRAP_PATH.matches(obj.path)) { "Invalid Drive key bootstrap path" }
                    require(obj.sizeBytes in 0..MAX_DRIVE_OBJECT_BYTES.toLong()) { "Drive key bootstrap exceeds the permitted size" }
                    val sealed = carrier.read(obj.path) ?: throw IOException("A listed Drive key bootstrap is missing")
                    val bytes = DrivePayloadCodec.open(secrets().keys, obj.path, sealed)
                    missingKey.remove(obj.path)
                    val attestation = DriveRotationBootstrapCodec.decode(obj.path, bytes, farmId)
                    attestations[attestation.bundle.operations.single().checksum] = attestation
                    val result = endpoint.ingest(attestation.bundle)
                    if (result.rejected) {
                        reasons[obj.path] = requireNotNull(result.rejectedReason)
                    } else {
                        applied += result.applied
                        duplicates += result.duplicates
                        rotationIterator.remove()
                        reasons.remove(obj.path)
                        progress = true
                    }
                } catch (unavailable: FarmKeyUnavailable) {
                    missingKey += obj.path
                    reasons[obj.path] = unavailable.message.orEmpty()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (interrupted: InterruptedException) {
                    throw interrupted
                } catch (denied: DriveGatewayApprovalException) {
                    throw denied
                } catch (auth: DriveAuthNeededException) {
                    throw auth
                } catch (pending: FarmKeyRotationPendingException) {
                    throw pending
                } catch (missing: DriveKeysUnavailableException) {
                    throw missing
                } catch (failure: Exception) {
                    reasons[obj.path] = failure.message ?: "A Drive key bootstrap failed verification"
                }
            }
            val ordinaryIterator = ordinary.iterator()
            while (ordinaryIterator.hasNext()) {
                val obj = ordinaryIterator.next()
                try {
                    val bundle = readDriveJournalBundle(store, farmId, obj)
                    val result = endpoint.ingest(bundle)
                    if (result.rejected) {
                        reasons[obj.path] = requireNotNull(result.rejectedReason)
                    } else {
                        applied += result.applied
                        duplicates += result.duplicates
                        ordinaryIterator.remove()
                        reasons.remove(obj.path)
                        progress = true
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (interrupted: InterruptedException) {
                    throw interrupted
                } catch (denied: DriveGatewayApprovalException) {
                    throw denied
                } catch (auth: DriveAuthNeededException) {
                    throw auth
                } catch (pending: FarmKeyRotationPendingException) {
                    throw pending
                } catch (missing: DriveKeysUnavailableException) {
                    throw missing
                } catch (failure: Exception) {
                    reasons[obj.path] = failure.message ?: "A Drive journal bundle failed verification"
                }
            }
        }
        val unresolved = ordinary.firstOrNull() ?: rotations.firstOrNull { it.path !in missingKey }
        if (unresolved != null) throw IOException(reasons[unresolved.path] ?: "Drive key recovery needs an authenticated farm peer.")
        return IngestResult(applied = applied, duplicates = duplicates)
    }

    /** Replaying an already installed pairing/LAN binding is a no-op, not a new identity approval. */
    private suspend fun matchesExistingIdentity(operation: OperationEnvelope): Boolean {
        if (operation.operationType != DEVICE_ENROLLED_COMMAND || operation.entityType != "farm_device") return false
        val payload = JSONObject(operation.payload.getValue(COMMAND_PAYLOAD_KEY))
        val target = payload.getString("deviceId")
        if (operation.entityId != target) return false
        val existing = database.replication().device(farmId, target) ?: return false
        return existing.publicKey != null && existing.publicKey == payload.getString("publicKey")
    }

    private suspend fun alreadyAccepted(operation: OperationEnvelope): Boolean {
        val row = database.replication().operation(farmId, operation.operationId) ?: return false
        if (row.toEnvelope() != operation) return false
        val application = database.replicationApplications().get(farmId, operation.operationId)
        return application == null || application.state == ApplicationState.APPLIED.name
    }

    private suspend fun signerRefusal(attestation: DriveRotationAttestation): String? {
        val signer = database.replication().device(farmId, attestation.signerId)
        if (signer == null || signer.publicKey == null || signer.revokedAfterSequence != null ||
            signer.status !in setOf(DeviceStatus.ACTIVE.name, DeviceStatus.TEMPORARILY_OFFLINE.name)
        ) return "Drive key bootstrap signer ${attestation.signerId} is not a known permitted farm device."
        val authentic = runCatching {
            DriveRotationBootstrapCodec.authentic(attestation, DeviceKeys.decode(Base64.getDecoder().decode(signer.publicKey)))
        }.getOrDefault(false)
        return if (authentic) null else "Drive key bootstrap could not authenticate device ${attestation.signerId}."
    }

    private fun requireMatching(bytes: ByteArray, path: String, expected: OperationBundle, publicKey: java.security.PublicKey) {
        val attestation = DriveRotationBootstrapCodec.decode(path, bytes, farmId)
        require(attestation.bundle == expected && DriveRotationBootstrapCodec.authentic(attestation, publicKey)) {
            "Drive refused a conflicting or unauthenticated key bootstrap"
        }
    }
}
