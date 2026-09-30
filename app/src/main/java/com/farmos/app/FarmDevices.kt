package com.farmos.app

import androidx.room.withTransaction
import com.farmos.core.database.COMMAND_PAYLOAD_KEY
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.OperationApplier
import com.farmos.core.database.ReplicationDeviceEntity
import com.farmos.core.database.journalLocalOperation
import com.farmos.core.database.replicationVector
import com.farmos.domain.replication.DeviceGrant
import com.farmos.domain.replication.DeviceKeys
import com.farmos.domain.replication.DeviceStatus
import com.farmos.domain.replication.FarmDevice
import com.farmos.domain.replication.FarmKeyRing
import com.farmos.domain.replication.FarmKeyWrap
import java.security.KeyPair
import java.util.Base64
import java.util.UUID
import org.json.JSONObject

/** A device joined the farm; journalled by the approving device so every farm device accepts it. */
internal const val DEVICE_ENROLLED_COMMAND = "farm.device_enrolled.v1"

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
    val publicKey = Base64.getEncoder().encodeToString(devicePublicKey)
    if (replication().device(grant.farmId, grant.deviceId) == null) {
        replication().upsertDevice(ReplicationDeviceEntity(grant.farmId, grant.deviceId, grant.deviceName, DeviceStatus.ACTIVE.name, 0, null, isLocal = false, publicKey = publicKey))
    }
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
    require(status == DeviceStatus.RETIRED || status == DeviceStatus.LOST_REVOKED) { "Only retiring or revoking a device is recorded" }
    require(deviceId != thisDeviceId) { "This device cannot revoke itself" }
    val existing = requireNotNull(replication().device(farmId, deviceId)) { "Unknown device" }
    val cutoff = replicationVector(farmId).watermark(deviceId)
    replication().upsertDevice(existing.copy(status = status.name, revokedAfterSequence = minOf(existing.revokedAfterSequence ?: cutoff, cutoff)))
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
        payloadJson = JSONObject().put("deviceId", deviceId).put("status", status.name).put("revokedAfterSequence", cutoff).toString(),
        schemaVersion = 1,
    )
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
                    publicKey = if (local) Base64.getEncoder().encodeToString(DeviceKeys.encode(identity.public)) else null,
                ),
            )
        }
    }
}

/** This farm's devices as the pairing rules see them. */
internal suspend fun FarmOsDatabase.farmDevices(farmId: String): List<FarmDevice> =
    replication().devices(farmId).map {
        FarmDevice(it.deviceId, it.name, DeviceStatus.valueOf(it.status), it.lastReportedOwnSequence, it.revokedAfterSequence)
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
