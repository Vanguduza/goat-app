package com.farmos.app

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.ReplicationDeviceEntity
import com.farmos.core.database.toEntity
import com.farmos.core.database.toEnvelope
import com.farmos.domain.access.LocalRole
import com.farmos.domain.replication.OperationEnvelope
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.Base64
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking

/** Real Room and a sealed file vault, with faults at the same sealing boundary as the Android vault. */
internal class KeyRotationTestFixture(
    val farm: String = UUID.randomUUID().toString(),
    val device: String = "tablet",
    provision: Boolean = true,
) : Closeable {
    val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FarmOsDatabase::class.java)
        .allowMainThreadQueries().build()
    val directory = Files.createTempDirectory("rotation-test").toFile()
    val vaultDirectory = File(directory, "vault")
    val sealer = RotationFaultSealer()
    val vault = testFarmKeyVault(vaultDirectory, sealer)

    init {
        seedCommandAuthority(database, farm, "owner", device, LocalRole.OWNER)
        if (provision) {
            val secrets = vault.provisionNewFarm(farm)
            runBlocking { database.announceIdentity(farm, device, secrets.device) }
        }
    }

    suspend fun rotate(at: Long = 1_800_000_000_100L) =
        database.rotateFarmKey(farm, device, "owner", vault, at)

    suspend fun knows(other: KeyRotationTestFixture) {
        val identity = requireNotNull(other.vault.rotationState(farm)).device
        database.replication().upsertDevice(ReplicationDeviceEntity(
            farm, other.device, other.device, "ACTIVE", 0L, null, false,
            Base64.getEncoder().encodeToString(identity.public.encoded),
        ))
    }

    suspend fun committedRotation(keyId: String): OperationEnvelope =
        database.replication().operationsForEntity(farm, "farm_key", keyId).single().toEnvelope()

    suspend fun receive(operation: OperationEnvelope) {
        database.withTransaction { database.replication().insertOperation(operation.toEntity()) }
        database.withTransaction { keyRotationApplier(vault, device).apply(database, operation) }
    }

    override fun close() {
        database.close()
        directory.deleteRecursively()
    }
}

internal class RotationFaultSealer : DeviceSealer {
    private val delegate = TestSoftwareSealer()
    val sealCalls = AtomicInteger()
    val openCalls = AtomicInteger()
    @Volatile var failSealAt: Int? = null

    override fun seal(plaintext: ByteArray): ByteArray {
        val attempt = sealCalls.incrementAndGet()
        if (attempt == failSealAt) throw IOException("Injected vault write failure")
        return delegate.seal(plaintext)
    }

    override fun open(sealed: ByteArray): ByteArray {
        openCalls.incrementAndGet()
        return delegate.open(sealed)
    }
}
