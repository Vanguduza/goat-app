package com.farmos.app

import androidx.room.withTransaction
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.LocalAccountEntity
import com.farmos.core.database.LocalFarmEntity
import com.farmos.core.database.ReplicationDeviceEntity
import com.farmos.domain.access.AccountStatus
import com.farmos.domain.access.CredentialHasher
import com.farmos.domain.access.CredentialKind
import com.farmos.domain.access.LocalRole
import com.farmos.domain.replication.DeviceStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals

/** A capture test starts with the same local farm/account/device authority required by the app. */
internal suspend fun FarmOsDatabase.seedCaptureOwner(farmId: String) {
    withContext(Dispatchers.IO) {
        withTransaction {
            localAccess().insertFarmIfAbsent(LocalFarmEntity(farmId, "Capture test farm", CAPTURE_TIME))
            localAccess().upsertAccount(
                LocalAccountEntity(
                    accountId = "user-1",
                    farmId = farmId,
                    username = "capture-owner",
                    displayName = "Capture owner",
                    role = LocalRole.OWNER.name,
                    status = AccountStatus.ACTIVE.name,
                    credentialKind = CredentialKind.PIN.name,
                    credentialHash = CredentialHasher(iterations = 1_000).hash("482913"),
                    failedAttempts = 0,
                    lockedUntilEpochMillis = null,
                    workerId = null,
                    createdAtEpochMillis = CAPTURE_TIME,
                ),
            )
            replication().upsertDevice(
                ReplicationDeviceEntity(
                    farmId, "device-1", "Capture test device", DeviceStatus.ACTIVE.name,
                    0, null, isLocal = true,
                ),
            )
        }
    }
}

/** Each successful UI action writes one operation attributed to its real local capture account. */
internal suspend fun FarmOsDatabase.assertLocalCaptureJournal(farmId: String, command: String) {
    val operation = replication().operationsInRange(farmId, "device-1", 1, Long.MAX_VALUE)
        .single { it.operationType == command }
    assertEquals(farmId, operation.farmId)
    assertEquals("user-1", operation.actorId)
    assertEquals("device-1", operation.deviceId)
}

private const val CAPTURE_TIME = 1_790_000_000_000L
