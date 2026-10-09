package com.farmos.app

import androidx.room.withTransaction
import com.farmos.core.database.FarmOsDatabase
import com.farmos.domain.access.AccountStatus
import com.farmos.domain.access.LocalRole
import com.farmos.domain.access.Permission
import com.farmos.domain.access.RolePermissions
import com.farmos.domain.replication.DeviceStatus
import java.io.IOException

internal class DriveGatewayApprovalException(message: String) : IOException(message)
internal class DriveKeysUnavailableException : IOException("This device's farm keys are unavailable. Restore its authorised farm keys before backing up.")

/** Local-device approval; Google identity and replicated settings never confer this capability. */
internal class DriveGatewayAuthority(
    private val database: FarmOsDatabase,
    private val deviceId: String,
) {
    suspend fun requireApprover(farmId: String, accountId: String) = database.withTransaction {
        val account = database.localAccess().account(farmId, accountId)
        val role = account?.role?.let { runCatching { LocalRole.valueOf(it) }.getOrNull() }
        if (account?.status != AccountStatus.ACTIVE.name || role == null ||
            !RolePermissions.allows(role, Permission.MANAGE_STORAGE_AND_BACKUP)
        ) {
            throw DriveGatewayApprovalException("An active Owner or Manager must approve storage and backup on this device.")
        }
        val device = database.replication().device(farmId, deviceId)
        if (device?.isLocal != true || device.status != DeviceStatus.ACTIVE.name || device.revokedAfterSequence != null) {
            throw DriveGatewayApprovalException("This device is not an active local device of the farm.")
        }
    }

    /** Called while holding the farm coordinator across destination validation and connection change. */
    suspend fun approve(
        farmId: String,
        accountId: String,
        config: DriveGatewayConfig,
        store: DriveConfigStore,
    ): DriveGatewayConfig = database.withTransaction {
        requireApprover(farmId, accountId)
        config.copy(approvedByAccountId = accountId, approvedDeviceId = deviceId).also { store.save(farmId, it) }
    }

    suspend fun requireCurrent(farmId: String, config: DriveGatewayConfig, store: DriveConfigStore) {
        if (store.get(farmId) != config) {
            throw DriveGatewayApprovalException("The Drive connection changed. Reconnect before retrying.")
        }
        val approver = config.approvedByAccountId
        if (approver.isNullOrBlank() || config.approvedDeviceId != deviceId) {
            throw DriveGatewayApprovalException("Reconnect Google Drive with an Owner or Manager to approve this device for backup.")
        }
        requireApprover(farmId, approver)
    }
}

/** A carrier with an internal lookup must check authority again before its actual network write. */
internal interface DriveCheckedWriter {
    suspend fun putIfAbsentChecked(
        path: String,
        bytes: ByteArray,
        sha256: String,
        beforeWrite: suspend () -> Unit,
    ): Boolean
}

/** Rechecks local authority before every carrier operation, including after a remote revocation arrives. */
internal class ApprovedDriveStore(
    private val delegate: DriveObjectStore,
    private val requireApproval: suspend () -> Unit,
) : DriveObjectStore {
    override suspend fun validateDestination(accountEmail: String) {
        requireApproval()
        delegate.validateDestination(accountEmail)
    }

    override suspend fun ensureFarmFolder(farmId: String, folderName: String, accountEmail: String): String {
        requireApproval()
        return delegate.ensureFarmFolder(farmId, folderName, accountEmail)
    }

    override suspend fun list(prefix: String): List<DriveObjectStore.DriveObject> {
        requireApproval()
        return delegate.list(prefix)
    }

    override suspend fun read(path: String): ByteArray? {
        requireApproval()
        return delegate.read(path)
    }

    override suspend fun putIfAbsent(path: String, bytes: ByteArray, sha256: String): Boolean {
        requireApproval()
        return if (delegate is DriveCheckedWriter) {
            delegate.putIfAbsentChecked(path, bytes, sha256, requireApproval)
        } else {
            delegate.putIfAbsent(path, bytes, sha256)
        }
    }
}
