package com.farmos.app

import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.LocalAccountEntity
import com.farmos.core.database.LocalFarmEntity
import com.farmos.core.database.ReplicationDeviceEntity
import com.farmos.domain.access.LocalRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/** Explicit local authority for command tests; each caller must choose the workflow's real role. */
internal fun seedCommandAuthority(
    database: FarmOsDatabase,
    farmId: String,
    actorId: String,
    deviceId: String,
    role: LocalRole,
): Unit = runBlocking(Dispatchers.IO) {
    database.localAccess().insertFarmIfAbsent(LocalFarmEntity(farmId, "Command test farm", 1L))
    database.localAccess().upsertAccount(
        LocalAccountEntity(
            accountId = actorId, farmId = farmId, username = actorId, displayName = actorId,
            role = role.name, status = "ACTIVE", credentialKind = "PIN", credentialHash = "test-only-hash",
            failedAttempts = 0, lockedUntilEpochMillis = null, workerId = null, createdAtEpochMillis = 1L,
        ),
    )
    // Adding a second test actor must not reset the device's issued sequence or revive a revoked row.
    if (database.replicationBlocking().device(farmId, deviceId) == null) {
        database.replicationBlocking().upsertDevice(
            ReplicationDeviceEntity(farmId, deviceId, deviceId, "ACTIVE", 0L, null, true),
        )
    }
}
