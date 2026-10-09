package com.farmos.app

import androidx.room.withTransaction
import com.farmos.core.database.ApplicationState
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.ReplicationOperationEntity
import com.farmos.core.database.journalLocalOperation
import com.farmos.core.database.toEnvelope
import com.farmos.domain.access.Permission
import com.farmos.domain.replication.DeviceKeys
import com.farmos.domain.replication.DeviceStatus
import com.farmos.domain.replication.FarmDataKey
import com.farmos.domain.replication.FarmKeyRing
import com.farmos.domain.replication.FarmKeyWrap
import com.farmos.domain.replication.WrappedFarmKey
import java.io.IOException
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

/** Sealed, device-local recovery metadata; it never leaves the vault or confers user authority. */
internal data class PendingFarmKeyRotation(
    val operationId: String,
    val keyId: String,
    val actorId: String,
    val deviceId: String,
    val businessTimeEpochMillis: Long,
    val payloadSha256: String,
)

internal class FarmKeyRotationPendingException(cause: Throwable? = null) : IOException(
    "Farm key rotation is pending on this device. Delivery is paused until its saved journal and vault can be reconciled.",
    cause,
)

/**
 * Room cannot roll back the vault. Stage the new key inactive, commit its exact journal, then activate.
 * A failure retains the old current key and every historical key; unresolved staging blocks delivery.
 * The supplied business time is preserved. A clock that has not advanced past the observed current
 * rotation is refused before staging; later received rotations still win by their original order.
 */
internal suspend fun FarmOsDatabase.rotateFarmKey(
    farmId: String,
    thisDeviceId: String,
    actorId: String,
    vault: FarmKeyVault,
    nowEpochMillis: Long = System.currentTimeMillis(),
) {
    var staged = false
    try {
        withTransaction {
            requireLocalAppPermission(farmId, actorId, thisDeviceId, Permission.MANAGE_DEVICES)
            val secrets = requireNotNull(vault.rotationState(farmId)) { "This device's farm keys are unavailable; restore them before rotating." }
            if (secrets.pendingRotations.isNotEmpty()) throw FarmKeyRotationPendingException()
            check(nowEpochMillis > secrets.currentSinceEpochMillis) {
                "The device clock has not advanced past the current key rotation; rotation was not started. Correct the clock and retry."
            }
            val operationId = UUID.randomUUID().toString()
            val key = FarmDataKey.generate("k-" + UUID.randomUUID())
            val recipients = replication().devices(farmId).filter {
                it.deviceId != thisDeviceId && it.publicKey != null && it.revokedAfterSequence == null &&
                    (it.status == DeviceStatus.ACTIVE.name || it.status == DeviceStatus.TEMPORARILY_OFFLINE.name)
            }
            val wrapped = JSONObject()
            fun wrapFor(deviceId: String, publicKey: java.security.PublicKey) {
                val value = FarmKeyWrap.wrap(key, publicKey, farmId, deviceId)
                wrapped.put(deviceId, JSONObject()
                    .put("epk", rotationB64(value.ephemeralPublicKey))
                    .put("nonce", rotationB64(value.nonce))
                    .put("ct", rotationB64(value.ciphertext)))
            }
            // Including this device makes the committed operation independently usable during reconciliation.
            wrapFor(thisDeviceId, secrets.device.public)
            recipients.forEach { wrapFor(it.deviceId, DeviceKeys.decode(Base64.getDecoder().decode(it.publicKey))) }
            val payload = JSONObject().put("keyId", key.keyId).put("wrapped", wrapped).toString()
            val pending = PendingFarmKeyRotation(
                operationId, key.keyId, actorId, thisDeviceId, nowEpochMillis, sha256Hex(payload.toByteArray(Charsets.UTF_8)),
            )
            vault.updateRotationState(farmId) { current ->
                check(current.pendingRotations.isEmpty()) { "A previous key rotation still needs reconciliation" }
                check(current.keys.key(key.keyId) == null) { "A farm key identity is never reused" }
                FarmSecrets(
                    FarmKeyRing(current.keys.all() + key, current.keys.currentKeyId), current.device,
                    current.currentSinceEpochMillis, listOf(pending),
                )
            }
            staged = true
            journalLocalOperation(
                operationId, farmId, "farm_key", key.keyId, actorId, thisDeviceId, nowEpochMillis,
                nowEpochMillis, null, KEY_ROTATED_COMMAND, payload, 1,
            )
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        if (staged) throw FarmKeyRotationPendingException(failure)
        throw failure
    }
    try {
        reconcileFarmKeyRotations(farmId, thisDeviceId, vault)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        throw FarmKeyRotationPendingException(failure)
    }
}

/**
 * Called before LAN/Drive delivery and after a local rotation commit. The Room transaction serializes
 * the receipt check with staging, so an absent receipt can never be an in-flight local transaction.
 * This is recovery of a committed operation, not a new command or a synthetic user approval.
 */
internal suspend fun FarmOsDatabase.reconcileFarmKeyRotations(
    farmId: String,
    thisDeviceId: String,
    vault: FarmKeyVault,
) {
    // An outer transaction may still roll back; it cannot supply proof of a committed rotation.
    if (inTransaction()) throw FarmKeyRotationPendingException()
    var pendingFound = false
    try {
        withTransaction {
            val pending = vault.rotationState(farmId)?.pendingRotations.orEmpty()
            if (pending.isEmpty()) return@withTransaction
            pendingFound = true
            val accepted = mutableMapOf<String, ReplicationOperationEntity>()
            for (candidate in pending) {
                if (candidate.deviceId != thisDeviceId) throw FarmKeyRotationPendingException()
                val operation = replication().operation(farmId, candidate.operationId) ?: continue
                val application = replicationApplications().get(farmId, candidate.operationId)
                val matches = operation.farmId == farmId && operation.operationType == KEY_ROTATED_COMMAND &&
                    operation.entityType == "farm_key" && operation.entityId == candidate.keyId &&
                    operation.actorId == candidate.actorId && operation.deviceId == candidate.deviceId &&
                    operation.businessTimeEpochMillis == candidate.businessTimeEpochMillis && operation.provenance == "local" &&
                    operation.toEnvelope().checksumValid() &&
                    sha256Hex(operation.payloadJson.toByteArray(Charsets.UTF_8)) == candidate.payloadSha256 &&
                    JSONObject(operation.payloadJson).optString("keyId") == candidate.keyId &&
                    (application == null || application.state == ApplicationState.APPLIED.name)
                if (!matches) throw FarmKeyRotationPendingException()
                accepted[candidate.operationId] = operation
            }
            vault.updateRotationState(farmId) { latest ->
                var ring = latest.keys
                var currentSince = latest.currentSinceEpochMillis
                for (candidate in pending) {
                    check(latest.pendingRotations.contains(candidate)) { "Pending key rotation changed during reconciliation" }
                    check(ring.key(candidate.keyId) != null) { "A staged farm key is unavailable" }
                    accepted[candidate.operationId]?.let { operation ->
                        val wrapped = JSONObject(operation.payloadJson).getJSONObject("wrapped").getJSONObject(thisDeviceId)
                        val actual = FarmKeyWrap.unwrap(
                            WrappedFarmKey(candidate.keyId, rotationDecode(wrapped.getString("epk")),
                                rotationDecode(wrapped.getString("nonce")), rotationDecode(wrapped.getString("ct"))),
                            latest.device, farmId, thisDeviceId,
                        )
                        if (!actual.materialForVault().contentEquals(requireNotNull(ring.key(candidate.keyId)).materialForVault())) {
                            throw FarmKeyRotationPendingException()
                        }
                    }
                    if (candidate.operationId in accepted && keyRotationWins(candidate.businessTimeEpochMillis, candidate.keyId, currentSince, ring.currentKeyId)) {
                        ring = FarmKeyRing(ring.all(), candidate.keyId)
                        currentSince = candidate.businessTimeEpochMillis
                    }
                }
                // An uncommitted candidate remains an inactive historical key; it is never made current.
                FarmSecrets(ring, latest.device, currentSince, latest.pendingRotations - pending.toSet())
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        if (pendingFound && failure !is FarmKeyRotationPendingException) throw FarmKeyRotationPendingException(failure)
        throw failure
    }
}

internal fun keyRotationWins(at: Long, keyId: String, currentAt: Long, currentId: String): Boolean =
    at > currentAt || (at == currentAt && keyId > currentId)

private fun rotationB64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
private fun rotationDecode(text: String): ByteArray = Base64.getDecoder().decode(text)
