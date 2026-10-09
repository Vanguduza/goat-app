package com.farmos.app

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.FarmOsDatabase
import com.farmos.domain.access.Credential
import com.farmos.domain.access.CredentialHasher
import com.farmos.domain.access.CredentialKind
import com.farmos.domain.access.FarmSetupResult
import com.farmos.domain.access.RecoveryCode
import com.farmos.domain.access.RecoveryResult
import com.farmos.domain.access.SignInResult
import java.io.Closeable
import java.io.File
import java.security.KeyStore
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Android SQLite close/reopen and rollback evidence; this does not claim a physical-device reboot. */
@RunWith(AndroidJUnit4::class)
class LocalAccessRecoveryDurabilityTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val names = mutableListOf<String>()
    private val vaultAlias = "goat_recovery_instrumentation_" + UUID.randomUUID()
    private val vaultDirectory = File(context.cacheDir, "recovery-vault-" + UUID.randomUUID())
    private val vault = FarmKeyVault(vaultDirectory, KeystoreSealer(vaultAlias))
    private val hasher = CredentialHasher(iterations = 1_000)
    private val deviceId = "recovery-instrumentation-device"
    private val now = 1_790_000_000_000L

    @After
    fun removeDatabases() {
        names.forEach { context.deleteDatabase(it) }
        vaultDirectory.deleteRecursively()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(vaultAlias)
    }

    @Test
    fun recoveredCredentialAndOneTimeCodeSurviveDatabaseCloseAndReopen(): Unit = runBlocking(Dispatchers.IO) {
        val name = newDatabaseName()
        lateinit var setup: FarmSetupResult
        lateinit var farmId: String
        lateinit var recovered: RecoveryResult.Recovered
        var committedOperations = 0L
        withDatabase(name) { db ->
            val directory = directory(db)
            farmId = directory.createFarm("Offline recovery farm") { id ->
                setup = directory.access.setUpFarm(id, "owner", "Owner", pin("482913"))
            }.farmId
            recovered = directory.transact {
                it.recoverOwner(farmId, setup.owner.accountId, setup.recoveryCode, pin("639204"))
            } as RecoveryResult.Recovered
            committedOperations = db.replication().count(farmId)
        }
        withDatabase(name) { db ->
            val directory = directory(db)
            val current = requireNotNull(directory.account(farmId, setup.owner.accountId))
            assertEquals(committedOperations, db.replication().count(farmId))
            assertTrue(hasher.verify("639204", current.credentialHash))
            assertFalse(hasher.verify("482913", current.credentialHash))
            val recoveryHash = requireNotNull(db.localAccess().recoveryHash(farmId))
            assertTrue(hasher.verify(RecoveryCode.normalise(recovered.newRecoveryCode), recoveryHash))
            assertFalse(hasher.verify(RecoveryCode.normalise(setup.recoveryCode), recoveryHash))
            assertEquals(RecoveryResult.InvalidCode, directory.transact {
                it.recoverOwner(farmId, setup.owner.accountId, setup.recoveryCode, pin("817362"))
            })
            assertTrue(directory.transact { it.signIn(farmId, "owner", "639204") } is SignInResult.SignedIn)
            val next = directory.transact {
                it.recoverOwner(farmId, setup.owner.accountId, recovered.newRecoveryCode, pin("817362"))
            } as RecoveryResult.Recovered
            assertFalse(hasher.verify(RecoveryCode.normalise(recovered.newRecoveryCode), db.localAccess().recoveryHash(farmId)!!))
            assertTrue(hasher.verify(RecoveryCode.normalise(next.newRecoveryCode), db.localAccess().recoveryHash(farmId)!!))
        }
        withDatabase(name) { db ->
            assertTrue(hasher.verify("817362", directory(db).account(farmId, setup.owner.accountId)!!.credentialHash))
            assertEquals(2, db.localAccess().audit(farmId, 50).count { it.action == "OWNER_RECOVERED" })
        }
    }

    @Test
    fun actualSQLiteJournalFailureRollsBackEveryRecoveryWriteAcrossReopen(): Unit = runBlocking(Dispatchers.IO) {
        val name = newDatabaseName()
        lateinit var setup: FarmSetupResult
        lateinit var farmId: String
        var operationCount = 0L
        var auditCount = 0L
        var deviceSequence = 0L
        var initialHash = ""
        withDatabase(name) { db ->
            val directory = directory(db)
            farmId = directory.createFarm("Rollback farm") { id ->
                setup = directory.access.setUpFarm(id, "owner", "Owner", pin("482913"))
            }.farmId
            operationCount = db.replication().count(farmId)
            auditCount = db.localAccess().auditCount(farmId)
            deviceSequence = db.replication().device(farmId, deviceId)!!.lastReportedOwnSequence
            initialHash = db.localAccess().recoveryHash(farmId)!!
            db.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER fail_recovery_journal BEFORE INSERT ON replication_operations " +
                    "WHEN NEW.operationType = 'access.recovery_set.v1' " +
                    "BEGIN SELECT RAISE(ABORT, 'instrumented recovery journal failure'); END",
            )
            val failure = runCatching {
                directory.transact { it.recoverOwner(farmId, setup.owner.accountId, setup.recoveryCode, pin("639204")) }
            }.exceptionOrNull()
            assertNotNull(failure)
            assertTrue(
                "Recovery must fail at the injected SQLite boundary: $failure",
                generateSequence(failure) { it.cause }.any {
                    it is SQLiteException && it.message.orEmpty().contains("instrumented recovery journal failure")
                },
            )
        }
        withDatabase(name) { db ->
            assertEquals(operationCount, db.replication().count(farmId))
            assertEquals(auditCount, db.localAccess().auditCount(farmId))
            assertEquals(deviceSequence, db.replication().device(farmId, deviceId)?.lastReportedOwnSequence)
            assertEquals(initialHash, db.localAccess().recoveryHash(farmId))
            assertEquals(setup.owner.credentialHash, directory(db).account(farmId, setup.owner.accountId)?.credentialHash)
            assertTrue(hasher.verify(RecoveryCode.normalise(setup.recoveryCode), initialHash))
        }
    }

    @Test
    fun aMissingAndroidKeystoreKeyCannotBeRecreatedByOpeningTheRetainedFarmVault(): Unit = runBlocking(Dispatchers.IO) {
        val name = newDatabaseName()
        lateinit var farmId: String
        withDatabase(name) { db ->
            val directory = directory(db)
            farmId = directory.createFarm("Keystore loss test farm") { id ->
                directory.access.setUpFarm(id, "owner", "Owner", pin("482913"))
            }.farmId
        }
        val sealedFile = File(vaultDirectory, farmId + ".vault")
        val retainedBytes = sealedFile.readBytes()
        val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        assertTrue(keystore.containsAlias(vaultAlias))
        // This alias was created by this test only, never the application's production vault alias.
        keystore.deleteEntry(vaultAlias)
        val failure = runCatching {
            FarmKeyVault(vaultDirectory, KeystoreSealer(vaultAlias)).requireSecrets(farmId)
        }.exceptionOrNull()
        assertTrue(failure?.message.orEmpty().contains("farm vault key is unavailable"))
        assertFalse(keystore.containsAlias(vaultAlias))
        assertArrayEquals(retainedBytes, sealedFile.readBytes())
    }

    private fun pin(value: String) = Credential(CredentialKind.PIN, value)
    private fun directory(db: FarmOsDatabase) = LocalFarmDirectory(db, deviceId, hasher, clock = { now }, initialKeys = vault)
    private fun newDatabaseName() = ("access-recovery-" + UUID.randomUUID() + ".db").also { names += it }
    private suspend fun <T> withDatabase(name: String, block: suspend (FarmOsDatabase) -> T): T {
        val database = Room.databaseBuilder(context, FarmOsDatabase::class.java, name)
            .addMigrations(*FarmOsDatabase.ALL_MIGRATIONS).build()
        return Closeable { database.close() }.use { block(database) }
    }
}
