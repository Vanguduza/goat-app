package com.farmos.app

import android.app.Application
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.COMMAND_PAYLOAD_KEY
import com.farmos.domain.access.AccessDenied
import com.farmos.domain.access.LocalRole
import com.farmos.domain.replication.CommandMergeClassification
import com.farmos.domain.replication.FarmDataKey
import com.farmos.domain.replication.FarmKeyWrap
import com.farmos.domain.replication.OperationEnvelope
import java.io.IOException
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-rUS", application = Application::class)
class FarmKeyRotationRecoveryTest {
    private val fixtures = mutableListOf<KeyRotationTestFixture>()
    private fun fixture(provision: Boolean = true) = KeyRotationTestFixture(provision = provision).also { fixtures += it }

    @After
    fun tearDown() = fixtures.forEach { it.close() }

    @Test
    fun currentAuthorityIsCheckedBeforeTheVaultIsOpenedOrChanged(): Unit = runBlocking {
        val f = fixture()
        val account = requireNotNull(f.database.localAccess().account(f.farm, "owner"))
        val device = requireNotNull(f.database.replication().device(f.farm, f.device))
        val journal = f.database.replication().count(f.farm)
        for ((role, status, cutoff) in listOf(
            Triple(LocalRole.WORKER.name, "ACTIVE", null),
            Triple(LocalRole.OWNER.name, "DISABLED", null),
            Triple(LocalRole.OWNER.name, "ACTIVE", 0L),
        )) {
            f.database.withTransaction {
                f.database.localAccess().upsertAccount(account.copy(role = role, status = status))
                f.database.replication().upsertDevice(device.copy(revokedAfterSequence = cutoff))
            }
            val opens = f.sealer.openCalls.get()
            val saves = f.sealer.sealCalls.get()
            expectFailure<AccessDenied> { f.rotate() }
            assertEquals(opens, f.sealer.openCalls.get())
            assertEquals(saves, f.sealer.sealCalls.get())
            assertEquals(journal, f.database.replication().count(f.farm))
        }
    }

    @Test
    fun anAuthorisedRotationNeverProvisionsAMissingVault(): Unit = runBlocking {
        val f = fixture(provision = false)
        expectFailure<IllegalArgumentException> { f.rotate() }
        assertFalse(f.vaultDirectory.exists())
        assertEquals(0, f.sealer.openCalls.get())
        assertEquals(0, f.sealer.sealCalls.get())
        assertEquals(0L, f.database.replication().count(f.farm))
    }

    @Test
    fun failedStagingPreservesTheOriginalSealedVaultAndJournal(): Unit = runBlocking {
        val f = fixture()
        val path = java.io.File(f.vaultDirectory, "${f.farm}.vault")
        val sealed = path.readBytes()
        val journal = f.database.replication().count(f.farm)
        f.sealer.failSealAt = f.sealer.sealCalls.get() + 1
        expectFailure<IOException> { f.rotate() }
        assertArrayEquals(sealed, path.readBytes())
        assertEquals(journal, f.database.replication().count(f.farm))
        assertEquals("k1", f.vault.secrets(f.farm)?.keys?.currentKeyId)
    }

    @Test
    fun failedJournalLeavesAnInactiveKeyWhichRestartNeverActivates(): Unit = runBlocking {
        val f = fixture()
        val journal = f.database.replication().count(f.farm)
        f.database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_rotation BEFORE INSERT ON replication_operations " +
                "WHEN NEW.operationType = 'farm.key_rotated.v1' BEGIN SELECT RAISE(ABORT, 'injected journal failure'); END",
        )
        expectFailure<FarmKeyRotationPendingException> { f.rotate() }
        val staged = requireNotNull(f.vault.rotationState(f.farm))
        assertEquals("k1", staged.keys.currentKeyId)
        assertEquals(2, staged.keys.keyIds.size)
        assertEquals(1, staged.pendingRotations.size)
        assertEquals(journal, f.database.replication().count(f.farm))
        expectFailure<FarmKeyRotationPendingException> { f.vault.secrets(f.farm) }

        f.database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_rotation")
        val restarted = testFarmKeyVault(f.vaultDirectory, f.sealer)
        f.database.reconcileFarmKeyRotations(f.farm, f.device, restarted)
        val ready = requireNotNull(restarted.secrets(f.farm))
        assertEquals("k1", ready.keys.currentKeyId)
        assertEquals(staged.keys.keyIds, ready.keys.keyIds)
        assertEquals(journal, f.database.replication().count(f.farm))
    }

    @Test
    fun callerOwnedTransactionCannotActivateBeforeItsOuterCommit(): Unit = runBlocking {
        val f = fixture()
        val journal = f.database.replication().count(f.farm)
        expectFailure<FarmKeyRotationPendingException> {
            f.database.withTransaction { f.rotate() }
        }
        assertEquals(journal, f.database.replication().count(f.farm))
        assertEquals("k1", f.vault.rotationState(f.farm)?.keys?.currentKeyId)
        f.database.reconcileFarmKeyRotations(f.farm, f.device, f.vault)
        assertEquals("k1", f.vault.secrets(f.farm)?.keys?.currentKeyId)
    }

    @Test
    fun committedRotationSurvivesFailedActivationAndReconcilesOnRestart(): Unit = runBlocking {
        val f = fixture()
        val old = requireNotNull(f.vault.secrets(f.farm)).keys.current.materialForVault()
        val pending = leaveCommittedPending(f)
        val journal = f.database.replication().count(f.farm)
        expectFailure<FarmKeyRotationPendingException> { f.vault.secrets(f.farm) }
        f.sealer.failSealAt = null
        val restarted = testFarmKeyVault(f.vaultDirectory, f.sealer)
        f.database.reconcileFarmKeyRotations(f.farm, f.device, restarted)
        val ready = requireNotNull(restarted.secrets(f.farm))
        assertEquals(pending.keyId, ready.keys.currentKeyId)
        assertArrayEquals(old, ready.keys.key("k1")!!.materialForVault())
        assertTrue(ready.pendingRotations.isEmpty())
        assertEquals(journal, f.database.replication().count(f.farm))
    }

    @Test
    fun aMismatchingJournalKeepsDeliveryBlockedAndEveryKeyRetained(): Unit = runBlocking {
        val f = fixture()
        val pending = leaveCommittedPending(f)
        val staged = requireNotNull(f.vault.rotationState(f.farm))
        f.database.openHelper.writableDatabase.execSQL(
            "UPDATE replication_operations SET actorId = 'unexpected-actor' WHERE operationId = ?",
            arrayOf(pending.operationId),
        )
        f.sealer.failSealAt = null
        expectFailure<FarmKeyRotationPendingException> {
            f.database.reconcileFarmKeyRotations(f.farm, f.device, f.vault)
        }
        val stillPending = requireNotNull(f.vault.rotationState(f.farm))
        assertEquals(staged.keys.keyIds, stillPending.keys.keyIds)
        assertEquals(staged.keys.currentKeyId, stillPending.keys.currentKeyId)
        assertEquals(staged.pendingRotations, stillPending.pendingRotations)
        expectFailure<FarmKeyRotationPendingException> { f.vault.secrets(f.farm) }
    }

    @Test
    fun delayedLocalActivationPreservesANewerReceivedKeyAndPendingHistory(): Unit = runBlocking {
        val f = fixture()
        val pending = leaveCommittedPending(f)
        f.sealer.failSealAt = null
        val incoming = FarmDataKey.generate("k-" + UUID.randomUUID())
        f.receive(rotationFor(f, incoming, pending.businessTimeEpochMillis + 1_000))
        assertEquals(incoming.keyId, f.vault.rotationState(f.farm)?.keys?.currentKeyId)
        assertEquals(listOf(pending), f.vault.rotationState(f.farm)?.pendingRotations)

        f.database.reconcileFarmKeyRotations(f.farm, f.device, f.vault)
        val ready = requireNotNull(f.vault.secrets(f.farm))
        assertEquals(incoming.keyId, ready.keys.currentKeyId)
        assertTrue(ready.keys.keyIds.containsAll(listOf("k1", pending.keyId, incoming.keyId)))
        assertTrue(ready.pendingRotations.isEmpty())
    }

    @Test
    fun simultaneousDeviceRotationsUseDistinctIdsAndConvergeAtEqualBusinessTime(): Unit = runBlocking {
        val farm = UUID.randomUUID().toString()
        val a = KeyRotationTestFixture(farm, "A").also { fixtures += it }
        val b = KeyRotationTestFixture(farm, "B").also { fixtures += it }
        val first = requireNotNull(a.vault.secrets(farm))
        val bIdentity = requireNotNull(b.vault.secrets(farm)).device
        b.vault.save(farm, FarmSecrets(first.keys, bIdentity))
        a.knows(b)
        b.knows(a)
        val at = 1_800_000_000_100L
        val one = async(Dispatchers.IO) { a.rotate(at) }
        val two = async(Dispatchers.IO) { b.rotate(at) }
        one.await()
        two.await()
        val aId = requireNotNull(a.vault.secrets(farm)).keys.currentKeyId
        val bId = requireNotNull(b.vault.secrets(farm)).keys.currentKeyId
        assertNotEquals(aId, bId)
        a.receive(b.committedRotation(bId))
        b.receive(a.committedRotation(aId))

        val aKeys = requireNotNull(a.vault.secrets(farm)).keys
        val bKeys = requireNotNull(b.vault.secrets(farm)).keys
        assertEquals(maxOf(aId, bId), aKeys.currentKeyId)
        assertEquals(aKeys.currentKeyId, bKeys.currentKeyId)
        assertEquals(setOf("k1", aId, bId), aKeys.keyIds)
        assertEquals(aKeys.keyIds, bKeys.keyIds)
        assertArrayEquals(aKeys.current.materialForVault(), bKeys.current.materialForVault())
        assertArrayEquals(first.keys.current.materialForVault(), aKeys.key("k1")!!.materialForVault())
    }

    private suspend fun leaveCommittedPending(f: KeyRotationTestFixture): PendingFarmKeyRotation {
        f.sealer.failSealAt = f.sealer.sealCalls.get() + 2
        expectFailure<FarmKeyRotationPendingException> { f.rotate() }
        return requireNotNull(f.vault.rotationState(f.farm)).pendingRotations.single().also {
            assertTrue(f.database.replication().operation(f.farm, it.operationId) != null)
        }
    }

    private fun rotationFor(f: KeyRotationTestFixture, key: FarmDataKey, at: Long): OperationEnvelope {
        val identity = requireNotNull(f.vault.rotationState(f.farm)).device
        val wrapped = FarmKeyWrap.wrap(key, identity.public, f.farm, f.device)
        fun b64(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)
        val payload = JSONObject().put("keyId", key.keyId).put("wrapped", JSONObject().put(f.device,
            JSONObject().put("epk", b64(wrapped.ephemeralPublicKey)).put("nonce", b64(wrapped.nonce)).put("ct", b64(wrapped.ciphertext)),
        )).toString()
        return OperationEnvelope.seal(
            operationId = UUID.randomUUID().toString(), farmId = f.farm, entityType = "farm_key",
            entityId = key.keyId, actorId = "peer-owner", deviceId = "peer", deviceSequence = 1L,
            businessTimeEpochMillis = at, createdAtEpochMillis = at, baseVersion = null,
            operationType = KEY_ROTATED_COMMAND, mergeClass = CommandMergeClassification.forCommand(KEY_ROTATED_COMMAND),
            payload = mapOf(COMMAND_PAYLOAD_KEY to payload), schemaVersion = 1, provenance = "local",
        )
    }

    private suspend inline fun <reified T : Throwable> expectFailure(crossinline action: suspend () -> Any?) {
        try {
            action()
            fail("Expected ${T::class.java.simpleName}")
        } catch (failure: Throwable) {
            if (failure !is T) throw failure
        }
    }
}
