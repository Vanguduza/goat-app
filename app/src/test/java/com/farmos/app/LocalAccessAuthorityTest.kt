package com.farmos.app

import android.database.sqlite.SQLiteException
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.domain.access.AccessAuditEvent
import com.farmos.domain.access.AccessAction
import com.farmos.domain.access.AccessDenied
import com.farmos.domain.access.LocalRole
import com.farmos.app.LocalAccessTestFixture.Companion.pin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-rUS")
class LocalAccessAuthorityTest {
    @Test
    fun unavailableOrRevokedLocalDevicesCannotChangeCredentialsAccountsOrAccessHistory(): Unit = runBlocking(Dispatchers.IO) {
        for (mode in listOf("MISSING", "REMOTE", "RETIRED", "LOST_REVOKED", "TEMPORARILY_OFFLINE", "CUTOFF")) {
            LocalAccessTestFixture().use { f ->
                f.create()
                val dao = f.database.replicationBlocking()
                val original = requireNotNull(dao.device(f.farmId, f.deviceId))
                when (mode) {
                    "MISSING" -> f.database.openHelper.writableDatabase.execSQL(
                        "DELETE FROM replication_devices WHERE farmId = ? AND deviceId = ?", arrayOf(f.farmId, f.deviceId),
                    )
                    "REMOTE" -> dao.upsertDevice(original.copy(isLocal = false))
                    "CUTOFF" -> dao.upsertDevice(original.copy(revokedAfterSequence = original.lastReportedOwnSequence))
                    else -> dao.upsertDevice(original.copy(status = mode))
                }
                val before = f.snapshot()
                expectDenied(f, before, mode) {
                    f.directory.transact { it.createAccount(f.setup.owner, "worker", "Worker", LocalRole.WORKER, pin("730418")) }
                }
                expectDenied(f, before, mode) {
                    f.directory.transact { it.recoverOwner(f.farmId, f.setup.owner.accountId, f.setup.recoveryCode, pin("639204")) }
                }
                expectDenied(f, before, mode) {
                    f.directory.transact { it.signIn(f.farmId, "owner", "000000") }
                }
            }
        }
    }

    @Test
    fun staleDisabledMissingAndForeignFarmActorsCannotUseManagementAuthority(): Unit = runBlocking(Dispatchers.IO) {
        for (mode in listOf("DISABLED", "VIEWER", "MISSING", "FOREIGN")) {
            LocalAccessTestFixture().use { f ->
                f.create()
                val owner = f.setup.owner
                val dao = f.database.localAccess()
                val row = requireNotNull(dao.account(f.farmId, owner.accountId))
                when (mode) {
                    "DISABLED" -> dao.upsertAccount(row.copy(status = "DISABLED"))
                    "VIEWER" -> dao.upsertAccount(row.copy(role = "VIEWER"))
                    "MISSING" -> f.database.openHelper.writableDatabase.execSQL(
                        "DELETE FROM local_accounts WHERE accountId = ?", arrayOf(owner.accountId),
                    )
                }
                val actor = if (mode == "FOREIGN") owner.copy(farmId = "another-farm") else owner
                expectDenied(f, f.snapshot(), mode) {
                    f.directory.transact { it.createAccount(actor, "worker", "Worker", LocalRole.WORKER, pin("730418")) }
                }
            }
        }
    }

    @Test
    fun journalFailureRollsBackTheCredentialRecoveryHashAuditAndLocalSequence(): Unit = runBlocking(Dispatchers.IO) {
        LocalAccessTestFixture().use { f ->
            f.create()
            val before = f.snapshot()
            f.database.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER fail_recovery_journal BEFORE INSERT ON replication_operations " +
                    "WHEN NEW.operationType = 'access.recovery_set.v1' " +
                    "BEGIN SELECT RAISE(ABORT, 'injected recovery journal failure'); END",
            )
            val failure = runCatching {
                f.directory.transact { it.recoverOwner(f.farmId, f.setup.owner.accountId, f.setup.recoveryCode, pin("639204")) }
            }.exceptionOrNull()
            assertNotNull("The recovery write must reach the failing journal insert", failure)
            assertTrue(
                "Expected the injected SQLite failure, not a credential or authority rejection: $failure",
                generateSequence(failure) { it.cause }.any {
                    it is SQLiteException && it.message.orEmpty().contains("injected recovery journal failure")
                },
            )
            assertEquals(before, f.snapshot())
            assertTrue(f.hasher.verify("482913", f.directory.account(f.farmId, f.setup.owner.accountId)!!.credentialHash))
        }
    }

    @Test
    fun directStoreCallsRequireATransactionAndCannotOverwriteAnotherFarmsGlobalIds(): Unit = runBlocking(Dispatchers.IO) {
        LocalAccessTestFixture().use { f ->
            f.create()
            val store = RoomLocalAccessStore(f.database, f.deviceId) { f.now }
            val before = f.snapshot()
            val outside = runCatching { store.save(f.setup.owner, f.setup.owner.accountId) }.exceptionOrNull()
            assertTrue(outside?.message.orEmpty().contains("require a Room transaction"))
            val other = f.directory.createFarm("Other farm") { id ->
                f.directory.access.setUpFarm(id, "other-owner", "Other Owner", pin("639204"))
            }
            val afterOtherFarm = f.snapshot()
            val collision = runCatching {
                f.database.withTransaction {
                    store.save(f.setup.owner.copy(farmId = other.farmId), f.directory.accounts(other.farmId).single().accountId)
                }
            }.exceptionOrNull()
            assertTrue(collision?.message.orEmpty().contains("belongs to another farm"))
            assertEquals(afterOtherFarm, f.snapshot())
            assertEquals(before.accounts, f.snapshot().accounts)
            val originalEvent = requireNotNull(f.database.localAccess().audit(f.farmId, 1).singleOrNull())
            val auditCollision = runCatching {
                f.database.withTransaction {
                    store.record(AccessAuditEvent(
                        originalEvent.eventId, other.farmId, null, null, AccessAction.SIGN_IN_FAILED, "Collision", f.now,
                    ))
                }
            }.exceptionOrNull()
            assertTrue(auditCollision?.message.orEmpty().contains("audit ID is already in use"))
            assertEquals(originalEvent, f.database.localAccess().auditEvent(originalEvent.eventId))
        }
    }

    private suspend fun expectDenied(f: LocalAccessTestFixture, before: AccessSnapshot, label: String, action: suspend () -> Any?) {
        val failure = runCatching { action() }.exceptionOrNull()
        assertTrue("$label must reject the local write: $failure", failure is AccessDenied)
        assertEquals("$label changed durable access state", before, f.snapshot())
    }
}
