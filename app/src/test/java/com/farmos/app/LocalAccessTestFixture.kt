package com.farmos.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.farmos.core.database.AccessAuditEntity
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.FarmRecoveryEntity
import com.farmos.core.database.LocalAccountEntity
import com.farmos.core.database.ReplicationDeviceEntity
import com.farmos.core.database.ReplicationOperationEntity
import com.farmos.domain.access.Credential
import com.farmos.domain.access.CredentialHasher
import com.farmos.domain.access.CredentialKind
import com.farmos.domain.access.FarmSetupResult
import java.io.Closeable
import java.io.File
import java.util.UUID

/** Uses the production farm-creation transaction, rather than manufacturing an authorized owner. */
internal class LocalAccessTestFixture(val deviceId: String = "tablet") : Closeable {
    val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(), FarmOsDatabase::class.java,
    ).allowMainThreadQueries().build()
    var now = 1_790_000_000_000L
    val hasher = CredentialHasher(iterations = 1_000)
    val vaultDirectory = File(ApplicationProvider.getApplicationContext<Context>().cacheDir, "access-fixture-vault-" + UUID.randomUUID())
    val vaultSealer = LocalAccessTestSealer()
    val vault = localAccessTestVault(vaultDirectory, vaultSealer)
    val directory = LocalFarmDirectory(database, deviceId, hasher, clock = { now }, initialKeys = vault)
    lateinit var farmId: String
    lateinit var setup: FarmSetupResult

    suspend fun create() {
        farmId = directory.createFarm("Recovery test farm") { id ->
            setup = directory.access.setUpFarm(id, "owner", "Farm Owner", pin("482913"))
        }.farmId
    }

    /** Authenticated pairing establishes the new local device before accounts arrive. */
    fun pairedTo(id: String) {
        farmId = id
        database.replicationBlocking().upsertDevice(ReplicationDeviceEntity(id, deviceId, deviceId, "ACTIVE", 0L, null, true))
    }

    suspend fun snapshot(): AccessSnapshot {
        val devices = database.replication().devices(farmId)
        val operations = devices.flatMap {
            database.replication().operationsInRange(farmId, it.deviceId, 1L, Long.MAX_VALUE)
        }.sortedBy { it.operationId }
        return AccessSnapshot(
            database.localAccess().accounts(farmId),
            database.localAccess().recovery(farmId),
            database.localAccess().audit(farmId, Int.MAX_VALUE),
            devices,
            operations,
        )
    }

    override fun close() {
        database.close()
        vaultDirectory.deleteRecursively()
    }

    companion object {
        fun pin(value: String) = Credential(CredentialKind.PIN, value)
    }
}

internal data class AccessSnapshot(
    val accounts: List<LocalAccountEntity>,
    val recovery: FarmRecoveryEntity?,
    val audit: List<AccessAuditEntity>,
    val devices: List<ReplicationDeviceEntity>,
    val operations: List<ReplicationOperationEntity>,
)
