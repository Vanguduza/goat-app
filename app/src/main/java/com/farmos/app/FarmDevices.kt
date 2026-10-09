package com.farmos.app

import androidx.room.withTransaction
import com.farmos.core.database.COMMAND_PAYLOAD_KEY
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.OperationApplier
import com.farmos.core.database.ReplicationDeviceEntity
import com.farmos.core.database.journalLocalOperation
import com.farmos.core.database.replicationVector
import com.farmos.domain.access.Permission
import com.farmos.domain.replication.DeviceGrant
import com.farmos.domain.replication.DeviceKeys
import com.farmos.domain.replication.DeviceStatus
import com.farmos.domain.replication.FarmDevice
import com.farmos.domain.replication.FarmKeyRing
import com.farmos.domain.replication.FarmKeyWrap
import com.farmos.domain.replication.WrappedFarmKey
import java.security.KeyPair
import java.util.Base64
import java.util.UUID
import org.json.JSONObject

/** A device joined the farm; journalled by the approving device so every farm device accepts it. */
internal const val DEVICE_ENROLLED_COMMAND = "farm.device_enrolled.v1"

/** A new farm key, wrapped to each remaining active device's identity key; never journalled unwrapped. */
internal const val KEY_ROTATED_COMMAND = "farm.key_rotated.v1"

/** A device was retired or reported lost; irreversible, and its later operations are refused everywhere. */
internal const val DEVICE_STATUS_COMMAND = "farm.device_set_status.v1"

/** How far a status is from active; a device's status only ever moves further, on every device. */
private val SEVERITY = listOf(DeviceStatus.ACTIVE, DeviceStatus.TEMPORARILY_OFFLINE, DeviceStatus.RETIRED, DeviceStatus.LOST_REVOKED)

/**
 * On the approving device: records the newly paired device with its public key and journals the
 * enrolment, in one transaction, before the grant is sent.
 */
internal suspend fun FarmOsDatabase.enrolPairedDevice(
    grant: DeviceGrant,
    devicePublicKey: ByteArray,
    thisDeviceId: String,
    nowEpochMillis: Long = System.currentTimeMillis(),
) = withTransaction {
    requireLocalAppPermission(grant.farmId, grant.approvedByAccountId, thisDeviceId, Permission.APPROVE_DEVICE_PAIRING)
    require(grant.deviceId != thisDeviceId) { "This device cannot enrol itself through pairing" }
    DeviceKeys.decode(devicePublicKey)
    val publicKey = Base64.getEncoder().encodeToString(devicePublicKey)
    val existing = replication().device(grant.farmId, grant.deviceId)
    require(existing == null) { "This device is already enrolled or was removed from the farm" }
    replication().upsertDevice(ReplicationDeviceEntity(grant.farmId, grant.deviceId, grant.deviceName, DeviceStatus.ACTIVE.name, 0, null, isLocal = false, publicKey = publicKey))
    journalLocalOperation(
        operationId = UUID.randomUUID().toString(),
        farmId = grant.farmId,
        entityType = "farm_device",
        entityId = grant.deviceId,
        actorId = grant.approvedByAccountId,
        deviceId = thisDeviceId,
        businessTimeEpochMillis = nowEpochMillis,
        createdAtEpochMillis = nowEpochMillis,
        baseVersion = null,
        operationType = DEVICE_ENROLLED_COMMAND,
        payloadJson = JSONObject().put("deviceId", grant.deviceId).put("name", grant.deviceName).put("publicKey", publicKey).toString(),
        schemaVersion = 1,
    )
}

/**
 * Retires a device or records it as lost. Operations it originated above what this device already holds
 * are refused from now on, here and, once the change replicates, on every farm device.
 */
internal suspend fun FarmOsDatabase.setDeviceStatus(
    farmId: String,
    deviceId: String,
    status: DeviceStatus,
    actorId: String,
    thisDeviceId: String,
    nowEpochMillis: Long = System.currentTimeMillis(),
) = withTransaction {
    requireLocalAppPermission(farmId, actorId, thisDeviceId, Permission.MANAGE_DEVICES)
    require(status == DeviceStatus.RETIRED || status == DeviceStatus.LOST_REVOKED) { "Only retiring or revoking a device is recorded" }
    require(deviceId != thisDeviceId) { "This device cannot revoke itself" }
    val existing = requireNotNull(replication().device(farmId, deviceId)) { "Unknown device" }
    val current = DeviceStatus.valueOf(existing.status)
    val effectiveStatus = if (SEVERITY.indexOf(status) > SEVERITY.indexOf(current)) status else current
    val held = replicationVector(farmId).watermark(deviceId)
    val cutoff = minOf(existing.revokedAfterSequence ?: held, held)
    if (effectiveStatus == current && existing.revokedAfterSequence == cutoff) return@withTransaction
    replication().upsertDevice(existing.copy(status = effectiveStatus.name, revokedAfterSequence = cutoff))
    journalLocalOperation(
        operationId = UUID.randomUUID().toString(),
        farmId = farmId,
        entityType = "farm_device",
        entityId = deviceId,
        actorId = actorId,
        deviceId = thisDeviceId,
        businessTimeEpochMillis = nowEpochMillis,
        createdAtEpochMillis = nowEpochMillis,
        baseVersion = null,
        operationType = DEVICE_STATUS_COMMAND,
        payloadJson = JSONObject().put("deviceId", deviceId).put("status", effectiveStatus.name).put("revokedAfterSequence", cutoff).toString(),
        schemaVersion = 1,
    )
}

/**
 * Operations [deviceId] reported issuing that have not reached this device (D-014). Retiring the device
 * now would refuse them for good, so they are shown before retirement is confirmed.
 */
internal suspend fun FarmOsDatabase.unpublishedOperations(farmId: String, deviceId: String): Long {
    val device = replication().device(farmId, deviceId) ?: return 0
    return (device.lastReportedOwnSequence - replicationVector(farmId).watermark(deviceId)).coerceAtLeast(0)
}

/**
 * On the new device: installs a pairing grant. The farm keys are unwrapped with this device's own key
 * and sealed into the vault; the farm's devices are recorded so their operations are accepted at once.
 */
internal suspend fun FarmOsDatabase.installGrant(grant: DeviceGrant, identity: KeyPair, thisDeviceId: String, vault: FarmKeyVault) {
    require(grant.deviceId == thisDeviceId) { "This grant is for another device" }
    val keys = grant.wrappedKeys.map { FarmKeyWrap.unwrap(it, identity, grant.farmId, thisDeviceId) }
    vault.save(grant.farmId, FarmSecrets(FarmKeyRing(keys, grant.currentKeyId), identity))
    withTransaction {
        grant.devices.forEach { device ->
            val local = device.deviceId == thisDeviceId
            replication().upsertDevice(
                ReplicationDeviceEntity(
                    farmId = grant.farmId,
                    deviceId = device.deviceId,
                    name = device.name,
                    status = device.status.name,
                    lastReportedOwnSequence = if (local) 0 else device.lastReportedOwnSequence,
                    revokedAfterSequence = device.revokedAfterSequence,
                    isLocal = local,
                    publicKey = if (local) Base64.getEncoder().encodeToString(DeviceKeys.encode(identity.public)) else device.publicKey,
                ),
            )
        }
    }
}

/** This farm's devices as the pairing rules see them. */
internal suspend fun FarmOsDatabase.farmDevices(farmId: String): List<FarmDevice> =
    replication().devices(farmId).map {
        FarmDevice(it.deviceId, it.name, DeviceStatus.valueOf(it.status), it.lastReportedOwnSequence, it.revokedAfterSequence, it.publicKey)
    }

/** Appliers for replicated device membership. A device never becomes active again once retired or lost. */
internal val deviceReplicationAppliers: Map<String, OperationApplier> = mapOf(
    DEVICE_ENROLLED_COMMAND to OperationApplier { database, op ->
        val device = JSONObject(op.payload.getValue(COMMAND_PAYLOAD_KEY))
        val deviceId = device.getString("deviceId")
        val existing = database.replication().device(op.farmId, deviceId)
        when {
            existing == null -> database.replication().upsertDevice(
                ReplicationDeviceEntity(op.farmId, deviceId, device.getString("name"), DeviceStatus.ACTIVE.name, 0, null, isLocal = false, publicKey = device.getString("publicKey")),
            )
            existing.publicKey == null -> database.replication().upsertDevice(existing.copy(publicKey = device.getString("publicKey")))
        }
    },
    DEVICE_STATUS_COMMAND to OperationApplier { database, op ->
        val change = JSONObject(op.payload.getValue(COMMAND_PAYLOAD_KEY))
        val deviceId = change.getString("deviceId")
        val incoming = DeviceStatus.valueOf(change.getString("status"))
        val cutoff = change.getLong("revokedAfterSequence")
        val existing = database.replication().device(op.farmId, deviceId)
            ?: ReplicationDeviceEntity(op.farmId, deviceId, deviceId, DeviceStatus.ACTIVE.name, 0, null, isLocal = false)
        val current = DeviceStatus.valueOf(existing.status)
        val status = if (SEVERITY.indexOf(incoming) > SEVERITY.indexOf(current)) incoming else current
        database.replication().upsertDevice(
            existing.copy(status = status.name, revokedAfterSequence = minOf(existing.revokedAfterSequence ?: cutoff, cutoff)),
        )
    },
)

/**
 * Announces this device's identity key once per farm, so the farm's other devices can authenticate it on
 * the LAN and wrap rotated farm keys to it. Journalled as an enrolment of this device.
 */
internal suspend fun FarmOsDatabase.announceIdentity(farmId: String, thisDeviceId: String, identity: KeyPair, nowEpochMillis: Long = System.currentTimeMillis()) =
    withTransaction {
        val publicKey = Base64.getEncoder().encodeToString(DeviceKeys.encode(identity.public))
        val row = replication().device(farmId, thisDeviceId)
        if (row?.publicKey == publicKey) return@withTransaction
        replication().upsertDevice(
            row?.copy(publicKey = publicKey)
                ?: ReplicationDeviceEntity(farmId, thisDeviceId, THIS_DEVICE, DeviceStatus.ACTIVE.name, 0, null, isLocal = true, publicKey = publicKey),
        )
        journalLocalOperation(
            operationId = UUID.randomUUID().toString(),
            farmId = farmId,
            entityType = "farm_device",
            entityId = thisDeviceId,
            actorId = thisDeviceId,
            deviceId = thisDeviceId,
            businessTimeEpochMillis = nowEpochMillis,
            createdAtEpochMillis = nowEpochMillis,
            baseVersion = null,
            operationType = DEVICE_ENROLLED_COMMAND,
            payloadJson = JSONObject().put("deviceId", thisDeviceId).put("name", row?.name ?: THIS_DEVICE).put("publicKey", publicKey).toString(),
            schemaVersion = 1,
        )
    }

/**
 * Installs a rotated farm key received from another device, when one was wrapped to this device. The
 * latest rotation by business time becomes current; an older one arriving late only joins the ring.
 */
internal fun keyRotationApplier(vault: FarmKeyVault, thisDeviceId: String) = OperationApplier { _, op ->
    val payload = JSONObject(op.payload.getValue(COMMAND_PAYLOAD_KEY))
    val keyId = payload.getString("keyId")
    require(op.entityType == "farm_key" && op.entityId == keyId) { "Rotated key does not match its journal identity" }
    val mine = payload.getJSONObject("wrapped").optJSONObject(thisDeviceId) ?: return@OperationApplier
    vault.update(op.farmId) { secrets ->
        val key = FarmKeyWrap.unwrap(
            WrappedFarmKey(keyId, unb64(mine.getString("epk")), unb64(mine.getString("nonce")), unb64(mine.getString("ct"))),
            secrets.device, op.farmId, thisDeviceId,
        )
        val existing = secrets.keys.key(keyId)
        require(existing == null || existing.materialForVault().contentEquals(key.materialForVault())) { "A farm key identity was reused with different material" }
        val newer = keyRotationWins(op.businessTimeEpochMillis, keyId, secrets.currentSinceEpochMillis, secrets.keys.currentKeyId)
        if (existing != null && !newer) return@update null
        FarmSecrets(
            FarmKeyRing(if (existing == null) secrets.keys.all() + key else secrets.keys.all(), if (newer) keyId else secrets.keys.currentKeyId),
            secrets.device,
            if (newer) op.businessTimeEpochMillis else secrets.currentSinceEpochMillis,
            secrets.pendingRotations,
        )
    }
}

private fun b64(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)

private fun unb64(text: String) = Base64.getDecoder().decode(text)

private const val THIS_DEVICE = "This device"
