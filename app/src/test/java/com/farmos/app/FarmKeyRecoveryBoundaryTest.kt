package com.farmos.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.app.LocalAccessTestFixture.Companion.pin
import com.farmos.domain.access.RecoveryResult
import com.farmos.domain.replication.DeviceKeys
import com.farmos.domain.replication.FarmDiscoveryDescriptor
import java.io.Closeable
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.AEADBadTagException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
class FarmKeyRecoveryBoundaryTest {
    @Test
    fun explicitFarmCreationPersistsKeysBeforeAnyCarrierStartsAndReopensTheSameIdentity(): Unit = runBlocking(Dispatchers.IO) {
        LocalAccessTestFixture().use { f ->
            f.create()
            val original = f.vault.requireSecrets(f.farmId)
            val reopened = testFarmKeyVault(f.vaultDirectory, f.vaultSealer).requireSecrets(f.farmId)
            assertArrayEquals(original.keys.current.materialForVault(), reopened.keys.current.materialForVault())
            assertArrayEquals(DeviceKeys.encode(original.device.public), DeviceKeys.encode(reopened.device.public))
            assertEquals(original.keys.keyIds, reopened.keys.keyIds)
            assertEquals(f.setup.owner, f.directory.account(f.farmId, f.setup.owner.accountId))
            assertTrue(File(f.vaultDirectory, f.farmId + ".vault").isFile)
        }
    }

    @Test
    fun unconfiguredFirstFarmKeyStorageIsRejectedBeforeCreatingAnyFarm(): Unit = runBlocking(Dispatchers.IO) {
        LocalAccessTestFixture().use { f ->
            val directory = LocalFarmDirectory(f.database, f.deviceId, f.hasher)
            var enteredSetup = false
            val failure = runCatching { directory.createFarm("Missing storage") { enteredSetup = true } }.exceptionOrNull()
            assertTrue(failure?.message.orEmpty().contains("requires configured local key storage"))
            assertFalse(enteredSetup)
            assertTrue(directory.farms().isEmpty())
            assertFalse(f.vaultDirectory.exists())
        }
    }

    @Test
    fun aSealingFailureRollsBackTheFarmOwnerRecoveryHashAuditAndJournal(): Unit = runBlocking(Dispatchers.IO) {
        LocalAccessTestFixture().use { f ->
            val failing = testFarmKeyVault(f.vaultDirectory, object : DeviceSealer {
                override fun seal(plaintext: ByteArray): ByteArray = error("injected initial key sealing failure")
                override fun open(sealed: ByteArray): ByteArray = error("No existing test vault")
            })
            val directory = LocalFarmDirectory(f.database, f.deviceId, f.hasher, clock = { f.now }, initialKeys = failing)
            var attemptedFarm: String? = null
            val failure = runCatching {
                directory.createFarm("Failed setup") { id ->
                    attemptedFarm = id
                    directory.access.setUpFarm(id, "owner", "Owner", pin("482913"))
                }
            }.exceptionOrNull()
            assertTrue(failure?.message.orEmpty().contains("injected initial key sealing failure"))
            val id = requireNotNull(attemptedFarm)
            assertTrue(directory.farms().isEmpty())
            assertTrue(directory.accounts(id).isEmpty())
            assertNull(f.database.localAccess().recovery(id))
            assertEquals(0L, f.database.localAccess().auditCount(id))
            assertEquals(0L, f.database.replication().count(id))
            assertNull(f.database.replication().device(id, f.deviceId))
            assertFalse(File(f.vaultDirectory, id + ".vault").exists())
        }
    }

    @Test
    fun aMissingVaultCannotStartLanDiscoverOrMintAReplacementIdentity(): Unit = runBlocking(Dispatchers.IO) {
        LocalAccessTestFixture().use { f ->
            f.create()
            val file = File(f.vaultDirectory, f.farmId + ".vault")
            assertTrue(file.delete())
            val before = f.snapshot()
            val failure = runCatching { farmIdentity(f.database, f.vault, f.farmId) }.exceptionOrNull()
            assertTrue(failure?.message.orEmpty().contains("farm keys are unavailable"))
            assertLanRefused(f)
            assertFalse(file.exists())
            assertEquals(before, f.snapshot())
        }
    }

    @Test
    fun aCorruptVaultIsLeftIntactAndNeverReplacedWhenLanStarts(): Unit = runBlocking(Dispatchers.IO) {
        LocalAccessTestFixture().use { f ->
            f.create()
            val file = File(f.vaultDirectory, f.farmId + ".vault")
            val corrupt = file.readBytes().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
            file.writeBytes(corrupt)
            val before = f.snapshot()
            val failure = runCatching { f.vault.requireSecrets(f.farmId) }.exceptionOrNull()
            assertNotNull(failure)
            assertTrue(generateSequence(failure) { it.cause }.any { it is AEADBadTagException })
            assertLanRefused(f)
            assertArrayEquals(corrupt, file.readBytes())
            assertEquals(before, f.snapshot())
        }
    }

    @Test
    fun localPinRecoveryDoesNotPretendToRestoreMissingFarmKeys(): Unit = runBlocking(Dispatchers.IO) {
        LocalAccessTestFixture().use { f ->
            f.create()
            val file = File(f.vaultDirectory, f.farmId + ".vault")
            assertTrue(file.delete())
            val recovered = f.directory.transact {
                it.recoverOwner(f.farmId, f.setup.owner.accountId, f.setup.recoveryCode, pin("639204"))
            }
            assertTrue(recovered is RecoveryResult.Recovered)
            assertTrue(f.hasher.verify("639204", f.directory.account(f.farmId, f.setup.owner.accountId)!!.credentialHash))
            assertFalse(file.exists())
            assertTrue(runCatching { f.vault.requireSecrets(f.farmId) }.isFailure)
        }
    }

    private suspend fun assertLanRefused(f: LocalAccessTestFixture) {
        val discovery = CountingDiscovery()
        FarmLanRuntime(f.database, f.vault, discovery, f.farmId, "Recovery farm", f.deviceId).use { runtime ->
            runtime.start(intervalSeconds = 3_600)
            val failed = withTimeout(5_000) { runtime.state.first { it.lastError != null } }
            assertFalse(failed.serving)
            assertEquals(0, discovery.advertisements.get())
            assertEquals(0, discovery.discoveries.get())
        }
    }

    private class CountingDiscovery : FarmPeerDiscovery {
        val advertisements = AtomicInteger()
        val discoveries = AtomicInteger()
        override fun advertise(serviceName: String, descriptor: FarmDiscoveryDescriptor, port: Int): Closeable {
            advertisements.incrementAndGet()
            return Closeable {}
        }
        override fun discover(onChange: (List<DiscoveredFarm>) -> Unit): Closeable {
            discoveries.incrementAndGet()
            return Closeable {}
        }
    }
}
