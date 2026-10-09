package com.farmos.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.app.LocalAccessTestFixture.Companion.pin
import com.farmos.domain.replication.DeviceKeys
import com.farmos.domain.replication.FarmCipher
import java.io.File
import java.io.FileDescriptor
import java.io.IOException
import java.io.SyncFailedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-rUS")
class FarmVaultDurabilityTest {
    @Test
    fun fileSyncFailurePreservesThePreviousSealedVaultAndCleansOnlyTheTemporaryFile(): Unit = runBlocking(Dispatchers.IO) {
        LocalAccessTestFixture().use { f ->
            f.create()
            val file = File(f.vaultDirectory, f.farmId + ".vault")
            val before = file.readBytes()
            val original = f.vault.requireSecrets(f.farmId)
            val failing = testFarmKeyVault(f.vaultDirectory, f.vaultSealer, object : VaultFileDurability by JvmVaultFileDurability {
                override fun syncFile(descriptor: FileDescriptor) {
                    assertTrue(descriptor.valid())
                    throw SyncFailedException("injected file sync failure")
                }
            })
            val failure = runCatching {
                failing.save(f.farmId, FarmSecrets(original.keys.rotate("k2"), original.device))
            }.exceptionOrNull()
            assertTrue(failure is SyncFailedException)
            assertTrue(failure?.message.orEmpty().contains("injected file sync failure"))
            assertArrayEquals(before, file.readBytes())
            assertEquals(original.keys.currentKeyId, f.vault.requireSecrets(f.farmId).keys.currentKeyId)
            assertTrue(f.vaultDirectory.listFiles().orEmpty().none { it.name.endsWith(".tmp") })
        }
    }

    @Test
    fun directorySyncFailureIsReportedAndTheCompleteReplacementKeepsEveryHistoricalKey(): Unit = runBlocking(Dispatchers.IO) {
        LocalAccessTestFixture().use { f ->
            f.create()
            val original = f.vault.requireSecrets(f.farmId)
            val sealedBefore = FarmCipher.seal(original.keys.current, "historical record".toByteArray(), byteArrayOf(1))
            val failing = testFarmKeyVault(f.vaultDirectory, f.vaultSealer, failDirectory(f.vaultDirectory))
            val failure = runCatching {
                failing.save(f.farmId, FarmSecrets(original.keys.rotate("k2"), original.device))
            }.exceptionOrNull()
            assertTrue(failure is IOException)
            assertTrue(failure?.message.orEmpty().contains("injected directory sync failure"))
            val visible = f.vault.requireSecrets(f.farmId)
            assertEquals(setOf("k1", "k2"), visible.keys.keyIds)
            assertArrayEquals(DeviceKeys.encode(original.device.public), DeviceKeys.encode(visible.device.public))
            assertEquals("historical record", String(FarmCipher.open(visible.keys, sealedBefore, byteArrayOf(1))))
            assertTrue(f.vaultDirectory.listFiles().orEmpty().none { it.name.endsWith(".tmp") })
        }
    }

    @Test
    fun aFailedInitialDirectorySyncCannotCommitAFarmOrItsAccessJournal(): Unit = runBlocking(Dispatchers.IO) {
        LocalAccessTestFixture().use { f ->
            val failing = testFarmKeyVault(f.vaultDirectory, f.vaultSealer, failDirectory(f.vaultDirectory))
            val directory = LocalFarmDirectory(f.database, f.deviceId, f.hasher, clock = { f.now }, initialKeys = failing)
            var attempted: String? = null
            val failure = runCatching {
                directory.createFarm("Uncommitted farm") { id ->
                    attempted = id
                    directory.access.setUpFarm(id, "owner", "Owner", pin("482913"))
                }
            }.exceptionOrNull()
            assertTrue(failure?.message.orEmpty().contains("injected directory sync failure"))
            val id = requireNotNull(attempted)
            assertTrue(directory.farms().isEmpty())
            assertTrue(directory.accounts(id).isEmpty())
            assertEquals(0L, f.database.replication().count(id))
            assertEquals(0L, f.database.localAccess().auditCount(id))
            assertNull(f.database.localAccess().recovery(id))
            assertNull(f.database.replication().device(id, f.deviceId))
            // Replacement already happened, but Room did not commit. Preserve the unreferenced
            // sealed key file rather than deleting potentially recoverable material.
            assertNotNull(f.vault.secrets(id))
        }
    }

    @Test
    fun failedDirectorySyncDuringRotationReconcilesWithoutActivatingAnUncommittedKey(): Unit = runBlocking(Dispatchers.IO) {
        KeyRotationTestFixture().use { f ->
            val before = f.vault.requireSecrets(f.farm)
            val operations = f.database.replication().count(f.farm)
            val failing = testFarmKeyVault(f.vaultDirectory, f.sealer, failDirectory(f.vaultDirectory))
            val failure = runCatching {
                f.database.rotateFarmKey(f.farm, f.device, "owner", failing, 1_800_000_000_100L)
            }.exceptionOrNull()
            assertTrue(failure?.message.orEmpty().contains("injected directory sync failure"))
            assertEquals(operations, f.database.replication().count(f.farm))
            val staged = requireNotNull(f.vault.rotationState(f.farm))
            assertEquals(1, staged.pendingRotations.size)
            assertEquals(before.keys.currentKeyId, staged.keys.currentKeyId)
            assertTrue(runCatching { f.vault.requireSecrets(f.farm) }.exceptionOrNull() is FarmKeyRotationPendingException)
            f.database.reconcileFarmKeyRotations(f.farm, f.device, f.vault)
            val recovered = f.vault.requireSecrets(f.farm)
            assertEquals(before.keys.currentKeyId, recovered.keys.currentKeyId)
            assertTrue(recovered.keys.keyIds.containsAll(staged.keys.keyIds))
            assertTrue(recovered.pendingRotations.isEmpty())
            assertEquals(operations, f.database.replication().count(f.farm))
            assertArrayEquals(before.keys.current.materialForVault(), recovered.keys.current.materialForVault())
        }
    }

    @Test
    fun retryAfterMkdirMustRepeatTheFailedParentBarrierBeforeSavingSealedKeys(): Unit = runBlocking(Dispatchers.IO) {
        LocalAccessTestFixture().use { f ->
            f.create()
            val original = f.vault.requireSecrets(f.farmId)
            val originalFile = File(f.vaultDirectory, f.farmId + ".vault")
            val before = originalFile.readBytes()
            val retryDirectory = File(f.vaultDirectory, "retried-directory")
            val retryFile = File(retryDirectory, f.farmId + ".vault")
            val barriers = mutableListOf<String>()
            var parentAttempts = 0
            val durability = object : VaultFileDurability by JvmVaultFileDurability {
                override fun syncDirectory(directory: File) {
                    if (directory.absoluteFile == f.vaultDirectory.absoluteFile && retryDirectory.isDirectory) {
                        barriers += "parent"
                        parentAttempts++
                        if (parentAttempts == 1) throw IOException("injected parent metadata sync failure")
                    }
                    if (directory.absoluteFile == retryDirectory.absoluteFile) barriers += "directory"
                    JvmVaultFileDurability.syncDirectory(directory)
                }

                override fun syncFile(descriptor: FileDescriptor) {
                    barriers += "file"
                    JvmVaultFileDurability.syncFile(descriptor)
                }
            }
            val retryVault = testFarmKeyVault(retryDirectory, f.vaultSealer, durability)
            val failure = runCatching { retryVault.save(f.farmId, original) }.exceptionOrNull()
            assertTrue(failure is IOException)
            assertTrue(failure?.message.orEmpty().contains("injected parent metadata sync failure"))
            assertTrue(retryDirectory.isDirectory)
            assertFalse(retryFile.exists())
            assertEquals(listOf("parent"), barriers)
            assertArrayEquals(before, originalFile.readBytes())

            // The mkdir survived its failed parent fsync. Existing-directory detection must
            // retry that barrier, not silently jump straight to the sealed-file write.
            barriers.clear()
            retryVault.save(f.farmId, original)
            assertEquals(2, parentAttempts)
            assertEquals(listOf("parent", "file", "directory"), barriers)
            val recovered = retryVault.requireSecrets(f.farmId)
            assertArrayEquals(original.keys.current.materialForVault(), recovered.keys.current.materialForVault())
            assertArrayEquals(DeviceKeys.encode(original.device.public), DeviceKeys.encode(recovered.device.public))
            assertArrayEquals(before, originalFile.readBytes())
            assertTrue(retryDirectory.listFiles().orEmpty().none { it.name.endsWith(".tmp") })
        }
    }

    private fun failDirectory(target: File) = object : VaultFileDurability by JvmVaultFileDurability {
        override fun syncDirectory(directory: File) {
            if (directory.absoluteFile == target.absoluteFile) throw IOException("injected directory sync failure")
            JvmVaultFileDurability.syncDirectory(directory)
        }
    }
}
