package com.farmos.app

import android.content.Context
import androidx.room.Room
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.replicationVector
import com.farmos.domain.access.Credential
import com.farmos.domain.access.CredentialHasher
import com.farmos.domain.access.CredentialKind
import com.farmos.domain.access.LocalAccount
import java.io.Closeable
import java.io.File
import java.nio.file.Files
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/** A real local farm, sealed test vault and deliberately slow carrier; no Google account or network. */
internal class DriveDeliveryTestFixture(val context: Context) : Closeable {
    val database = Room.inMemoryDatabaseBuilder(context, FarmOsDatabase::class.java).allowMainThreadQueries().build()
    val deviceId = "gateway-device"
    val root: File = Files.createTempDirectory("drive-delivery").toFile()
    val vaultDirectory = File(root, "vault")
    val vault = testFarmKeyVault(vaultDirectory, TestDriveSealer())
    val directory = LocalFarmDirectory(database, deviceId, CredentialHasher(iterations = 1_000), initialKeys = vault)
    val coordinator = DriveFarmCoordinator()
    val store = DriveConfigStore(context)
    val carrier = MemoryDriveCarrier()
    val authorizer = CountingDriveAuthorizer()
    val owner: LocalAccount
    val farmId: String
    var online = true
    var now = 1_800_000_000_000L
    private val runtimes = mutableListOf<FarmDriveRuntime>()

    init {
        var created: LocalAccount? = null
        farmId = runBlocking {
            directory.createFarm("Background test farm") { farm ->
                created = directory.access.setUpFarm(farm, "owner", "Farm owner", Credential(CredentialKind.PIN, "482913")).owner
            }
        }.farmId
        owner = requireNotNull(created)
        runBlocking { approve() }
    }

    fun config() = DriveGatewayConfig("owner@example.com", "approved-folder", "Farm backup", now)

    suspend fun approve() = directory.approveDriveGateway(context, farmId, owner.accountId, config())

    fun runtime(sharedCoordinator: DriveFarmCoordinator = coordinator): FarmDriveRuntime =
        FarmDriveRuntime(
            context, database, vault, farmId, deviceId, FileAttachmentStore(File(root, "attachments")),
            authorizer = authorizer, clock = { now }, coordinator = sharedCoordinator,
            online = { online }, openStore = { _, _ -> carrier },
        ).also { runtimes += it }

    fun localOperationCount(): Long = runBlocking { database.replicationVector(farmId).entries.values.sum() }

    override fun close() {
        runtimes.forEach { it.close() }
        database.close()
        root.deleteRecursively()
    }
}

internal class CountingDriveAuthorizer : DriveAuthorizer {
    var token: String? = "test-only-bearer"
    val calls = AtomicInteger()
    override suspend fun accessToken(): String? {
        calls.incrementAndGet()
        return token
    }
}

internal class MemoryDriveCarrier : DriveObjectStore {
    val objects = ConcurrentHashMap<String, ByteArray>()
    val writes = AtomicInteger()
    val calls = AtomicInteger()
    var latencyMillis = 0L
    var onList: (suspend () -> Unit)? = null
    var onRead: (suspend () -> Unit)? = null

    override suspend fun list(prefix: String): List<DriveObjectStore.DriveObject> {
        calls.incrementAndGet()
        onList?.invoke()
        delay(latencyMillis)
        return objects.filterKeys { it.startsWith(prefix) }.map { (path, bytes) ->
            DriveObjectStore.DriveObject(path, bytes.size.toLong(), sha256Hex(bytes))
        }
    }

    override suspend fun read(path: String): ByteArray? {
        calls.incrementAndGet()
        onRead?.invoke()
        delay(latencyMillis)
        return objects[path]?.copyOf()
    }

    override suspend fun putIfAbsent(path: String, bytes: ByteArray, sha256: String): Boolean {
        calls.incrementAndGet()
        writes.incrementAndGet()
        delay(latencyMillis)
        val old = objects.putIfAbsent(path, bytes.copyOf())
        return old == null || old.contentEquals(bytes)
    }
}

private class TestDriveSealer : DeviceSealer {
    private val key = ByteArray(32).also(SecureRandom()::nextBytes)
    override fun seal(plaintext: ByteArray): ByteArray {
        val iv = ByteArray(12).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return iv + cipher.doFinal(plaintext)
    }

    override fun open(sealed: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, sealed.copyOfRange(0, 12)))
        return cipher.doFinal(sealed.copyOfRange(12, sealed.size))
    }
}
