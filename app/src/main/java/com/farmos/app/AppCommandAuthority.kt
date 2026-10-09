package com.farmos.app

import com.farmos.core.database.FarmOsDatabase
import com.farmos.domain.access.AccessDenied
import com.farmos.domain.access.AccountStatus
import com.farmos.domain.access.LocalRole
import com.farmos.domain.access.Permission
import com.farmos.domain.access.RolePermissions
import com.farmos.domain.replication.DeviceStatus

/** Local user commands re-read authority in the transaction that will commit their mutation. */
internal suspend fun FarmOsDatabase.requireLocalAppPermission(
    farmId: String,
    actorId: String,
    deviceId: String,
    permission: Permission,
): LocalRole {
    check(inTransaction()) { "Local authority must be checked in the command transaction" }
    val account = localAccess().account(farmId, actorId)
    val role = account?.role?.let { runCatching { LocalRole.valueOf(it) }.getOrNull() }
    if (account?.status != AccountStatus.ACTIVE.name || role == null || !RolePermissions.allows(role, permission)) {
        throw AccessDenied("Your active farm account does not have permission for this action.")
    }
    val device = replication().device(farmId, deviceId)
    if (device?.isLocal != true || device.status != DeviceStatus.ACTIVE.name || device.revokedAfterSequence != null) {
        throw AccessDenied("This action requires an active local device of this farm.")
    }
    return role
}
