package com.farmos.app

import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.LocalAccountEntity
import com.farmos.core.database.LocalFarmEntity
import com.farmos.core.database.ReplicationDeviceEntity

/** File-backed device tests start with an actually authorized local Worker. */
internal suspend fun seedAndroidCommandWorker(database: FarmOsDatabase, farm: String, actor: String, device: String) {
    database.localAccess().insertFarmIfAbsent(LocalFarmEntity(farm, "Device acceptance test farm", 1L))
    database.localAccess().upsertAccount(
        LocalAccountEntity(actor, farm, actor, actor, "WORKER", "ACTIVE", "PIN", "test-only-hash",
            0, null, null, 1L),
    )
    if (database.replication().device(farm, device) == null) {
        database.replication().upsertDevice(ReplicationDeviceEntity(farm, device, device, "ACTIVE", 0L, null, true))
    }
}
