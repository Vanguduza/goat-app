package com.farmos.app

import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.app.LocalAccessTestFixture.Companion.pin
import com.farmos.core.database.ApplicationState
import com.farmos.core.database.COMMAND_PAYLOAD_KEY
import com.farmos.core.database.LocalAccountEntity
import com.farmos.core.database.OperationApplier
import com.farmos.core.database.toEntity
import com.farmos.core.database.toEnvelope
import com.farmos.core.model.LocalCommandContext
import com.farmos.domain.access.RecoveryCode
import com.farmos.domain.access.RecoveryResult
import com.farmos.domain.replication.CommandMergeClassification
import com.farmos.domain.replication.OperationBundle
import com.farmos.domain.replication.OperationEnvelope
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-rUS")
class LocalAccessReplaySecurityTest {
    private var sequence = 0L

    @Test
    fun malformedTenantAndIdentityClaimsFailWithoutPoisoningLaterValidHistory(): Unit = runBlocking(Dispatchers.IO) {
        LocalAccessTestFixture().use { f ->
            f.create()
            val endpoint = receiver(f)
            val farms = f.directory.farms()
            val wrongFarm = operation(f, FARM_CREATE_COMMAND, "local_farm", f.farmId,
                JSONObject().put("farmId", "foreign-farm").put("name", "Foreign").put("createdAtEpochMillis", f.now))
            failed(f, endpoint, wrongFarm)
            assertEquals(farms, f.directory.farms())

            val id = UUID.randomUUID().toString()
            val base = worker(f, id)
            failed(f, endpoint, operation(f, ACCOUNT_SET_COMMAND, ACCOUNT_ENTITY, id,
                fields(base).put("accountId", "different-account")))
            failed(f, endpoint, operation(f, ACCOUNT_SET_COMMAND, ACCOUNT_ENTITY, id,
                fields(base).put("changed", JSONArray(listOf("farmId")))))
            failed(f, endpoint, operation(f, ACCOUNT_SET_COMMAND, ACCOUNT_ENTITY, id,
                fields(base).put("role", "UNRECOGNISED")))
            assertNull(f.directory.account(f.farmId, id))
            val valid = operation(f, ACCOUNT_SET_COMMAND, ACCOUNT_ENTITY, id, fields(base))
            assertFalse(endpoint.ingest(OperationBundle.seal(f.farmId, "origin", listOf(valid))).rejected)
            assertEquals(ApplicationState.APPLIED.name, f.database.replicationApplications().get(f.farmId, valid.operationId)?.state)
            assertEquals("Worker", f.directory.account(f.farmId, id)?.displayName)
            assertEquals(f.farmId, f.database.localAccess().accountFarmId(id))
        }
    }

    @Test
    fun globalAccountAndAuditIdCollisionsCannotOverwriteOrHideAnotherFarmsHistory(): Unit = runBlocking(Dispatchers.IO) {
        LocalAccessTestFixture().use { f ->
            f.create()
            val otherFarm = f.directory.createFarm("Other farm") { id ->
                f.directory.access.setUpFarm(id, "other-owner", "Other Owner", pin("639204"))
            }
            val otherOwner = f.database.localAccess().accounts(otherFarm.farmId).single()
            val otherAudit = f.database.localAccess().audit(otherFarm.farmId, 1).single()
            val endpoint = receiver(f)
            failed(f, endpoint, operation(f, ACCOUNT_SET_COMMAND, ACCOUNT_ENTITY, otherOwner.accountId,
                fields(otherOwner.copy(farmId = f.farmId, username = "collision"))))
            assertEquals(otherOwner, f.database.localAccess().account(otherFarm.farmId, otherOwner.accountId))
            assertNull(f.directory.account(f.farmId, otherOwner.accountId))
            val audit = JSONObject().put("eventId", otherAudit.eventId).put("farmId", f.farmId)
                .put("actorAccountId", f.setup.owner.accountId).put("subjectAccountId", JSONObject.NULL)
                .put("action", "SIGNED_IN").put("detail", "Conflicting event").put("atEpochMillis", f.now)
            failed(f, endpoint, operation(f, AUDIT_COMMAND, "access_audit", otherAudit.eventId, audit))
            assertEquals(otherAudit, f.database.localAccess().auditEvent(otherAudit.eventId))
        }
    }

    @Test
    fun anApplierRequiresTheExactJournalReceiptAndKeepsHistoricalActorAndBusinessTime(): Unit = runBlocking(Dispatchers.IO) {
        LocalAccessTestFixture().use { f ->
            f.create()
            val id = UUID.randomUUID().toString()
            val original = operation(f, ACCOUNT_SET_COMMAND, ACCOUNT_ENTITY, id, fields(worker(f, id)))
            val applier = accessReplicationAppliers.getValue(ACCOUNT_SET_COMMAND)
            val absent = runCatching { f.database.withTransaction { applier.apply(f.database, original) } }.exceptionOrNull()
            assertTrue(absent?.message.orEmpty().contains("exact admitted journal receipt"))
            // Receipt ingestion without this version's applier records the actual pending state.
            val pending = receiver(f, emptyMap())
            assertFalse(pending.ingest(OperationBundle.seal(f.farmId, "origin", listOf(original))).rejected)
            assertEquals(ApplicationState.AWAITING_APPLIER.name, f.database.replicationApplications().get(f.farmId, original.operationId)?.state)
            val altered = operation(f, ACCOUNT_SET_COMMAND, ACCOUNT_ENTITY, id,
                fields(worker(f, id).copy(displayName = "Altered")), operationId = original.operationId)
            val mismatch = runCatching { f.database.withTransaction { applier.apply(f.database, altered) } }.exceptionOrNull()
            assertTrue(mismatch?.message.orEmpty().contains("exact admitted journal receipt"))
            assertNull(f.directory.account(f.farmId, id))

            // This represents an already-admitted historical operation. A later loss of the actor's
            // local role cannot turn replay into a newly authorized call or rewrite its attribution.
            val actor = f.database.localAccess().account(f.farmId, f.setup.owner.accountId)!!
            f.database.localAccess().upsertAccount(actor.copy(status = "DISABLED", role = "VIEWER"))
            receiver(f).applyPendingNow()
            assertEquals(ApplicationState.APPLIED.name, f.database.replicationApplications().get(f.farmId, original.operationId)?.state)
            assertEquals("Worker", f.directory.account(f.farmId, id)?.displayName)
            assertEquals(original, f.database.replication().operation(f.farmId, original.operationId)?.toEnvelope())
            assertEquals(f.setup.owner.accountId, original.actorId)
            assertEquals(f.now, original.businessTimeEpochMillis)
            assertEquals("DISABLED", f.database.localAccess().account(f.farmId, actor.accountId)?.status)
        }
    }

    @Test
    fun anOlderReceiptCannotDowngradeANewerRetainedLegacyRecoveryProjection(): Unit = runBlocking(Dispatchers.IO) {
        LocalAccessTestFixture().use { f ->
            f.create()
            val retained = f.database.localAccess().recovery(f.farmId)!!.copy(updatedAtEpochMillis = f.now + 1_000)
            f.database.localAccess().upsertRecovery(retained)
            val op = operation(f, RECOVERY_SET_COMMAND, RECOVERY_ENTITY, f.farmId,
                JSONObject().put("farmId", f.farmId).put("recoveryHash", "older-historical-hash"))
            val endpoint = receiver(f)
            assertFalse(endpoint.ingest(OperationBundle.seal(f.farmId, "origin", listOf(op))).rejected)
            assertEquals(ApplicationState.APPLIED.name, f.database.replicationApplications().get(f.farmId, op.operationId)?.state)
            assertEquals(retained, f.database.localAccess().recovery(f.farmId))
        }
    }

    @Test
    fun aSetAsideAccountReceiptCannotReenterThroughAnotherAppliedOrLocalIdentityChange(): Unit = runBlocking(Dispatchers.IO) {
        LocalAccessTestFixture().use { f ->
            f.create()
            f.now += 1_000
            val owner = f.database.localAccess().account(f.farmId, f.setup.owner.accountId)!!
            val targetId = UUID.randomUUID().toString()
            val decision = setAsideDecision(f, targetId)
            val target = operation(f, ACCOUNT_SET_COMMAND, ACCOUNT_ENTITY, owner.accountId,
                fields(owner.copy(role = "VIEWER")).put("changed", JSONArray(listOf("role"))),
                operationId = targetId, at = f.now + 2_000)
            val accepted = operation(f, ACCOUNT_SET_COMMAND, ACCOUNT_ENTITY, owner.accountId,
                fields(owner.copy(displayName = "Reviewed Owner")).put("changed", JSONArray(listOf("displayName"))),
                at = f.now + 1)
            val endpoint = receiver(f, accessReplicationAppliers + conflictReplicationAppliers)
            // All are pending when the scheduler snapshots its pass. The earlier decision must
            // still stand when that pass reaches the target and another receipt folds its history.
            assertFalse(endpoint.ingest(OperationBundle.seal(f.farmId, "origin", listOf(decision, target, accepted))).rejected)
            assertEquals(ApplicationState.SET_ASIDE.name, f.database.replicationApplications().get(f.farmId, targetId)?.state)
            assertEquals(ApplicationState.APPLIED.name, f.database.replicationApplications().get(f.farmId, accepted.operationId)?.state)
            assertEquals("OWNER", f.database.localAccess().account(f.farmId, owner.accountId)?.role)
            assertEquals("Reviewed Owner", f.directory.account(f.farmId, owner.accountId)?.displayName)
            assertSetAsideRefused(f, target)

            f.now += 2
            f.directory.transact { it.resetCredential(f.setup.owner, owner.accountId, pin("639204")) }
            val current = f.database.localAccess().account(f.farmId, owner.accountId)!!
            assertEquals("OWNER", current.role)
            assertEquals("Reviewed Owner", current.displayName)
            assertTrue(f.hasher.verify("639204", current.credentialHash))
            assertEquals(ApplicationState.SET_ASIDE.name, f.database.replicationApplications().get(f.farmId, targetId)?.state)
        }
    }

    @Test
    fun aSetAsideFutureRecoveryReceiptCannotReplaceAnAcceptedOrNewLocalRecoveryCode(): Unit = runBlocking(Dispatchers.IO) {
        LocalAccessTestFixture().use { f ->
            f.create()
            f.now += 1_000
            val original = f.database.localAccess().recovery(f.farmId)!!
            val targetId = UUID.randomUUID().toString()
            val decision = setAsideDecision(f, targetId)
            val target = operation(f, RECOVERY_SET_COMMAND, RECOVERY_ENTITY, f.farmId,
                JSONObject().put("farmId", f.farmId).put("recoveryHash", "set-aside-future-hash"),
                operationId = targetId, at = f.now + 2_000)
            val accepted = operation(f, RECOVERY_SET_COMMAND, RECOVERY_ENTITY, f.farmId,
                JSONObject().put("farmId", f.farmId).put("recoveryHash", original.recoveryHash), at = f.now + 1)
            val endpoint = receiver(f, accessReplicationAppliers + conflictReplicationAppliers)
            assertFalse(endpoint.ingest(OperationBundle.seal(f.farmId, "origin", listOf(decision, target, accepted))).rejected)
            assertEquals(original.recoveryHash, f.database.localAccess().recoveryHash(f.farmId))
            assertEquals(ApplicationState.APPLIED.name, f.database.replicationApplications().get(f.farmId, accepted.operationId)?.state)
            assertSetAsideRefused(f, target)

            f.now += 2
            val recovered = f.directory.transact {
                it.recoverOwner(f.farmId, f.setup.owner.accountId, f.setup.recoveryCode, pin("639204"))
            } as RecoveryResult.Recovered
            assertTrue(f.hasher.verify(RecoveryCode.normalise(recovered.newRecoveryCode), f.database.localAccess().recoveryHash(f.farmId)!!))
            assertFalse(f.hasher.verify(RecoveryCode.normalise(f.setup.recoveryCode), f.database.localAccess().recoveryHash(f.farmId)!!))
            assertEquals(ApplicationState.SET_ASIDE.name, f.database.replicationApplications().get(f.farmId, targetId)?.state)
        }
    }

    @Test
    fun anotherPendingOrUnadmittedReceiptCannotContributeToTheCurrentAccessFold(): Unit = runBlocking(Dispatchers.IO) {
        for (pendingState in listOf(ApplicationState.FAILED, ApplicationState.AWAITING_APPLIER)) {
            LocalAccessTestFixture().use { f ->
                sequence = 0
                f.create()
                f.now += 1_000
                val owner = f.database.localAccess().account(f.farmId, f.setup.owner.accountId)!!
                val recovery = f.database.localAccess().recovery(f.farmId)!!
                val localHistory = f.database.replication().operationsForEntity(f.farmId, ACCOUNT_ENTITY, owner.accountId)
                assertTrue(localHistory.isNotEmpty())
                assertTrue(localHistory.all { f.database.replicationApplications().get(f.farmId, it.operationId) == null })
                assertTrue(f.database.replication().device(f.farmId, f.deviceId)!!.isLocal)

                val futureAccount = operation(f, ACCOUNT_SET_COMMAND, ACCOUNT_ENTITY, owner.accountId,
                    fields(owner.copy(role = "VIEWER")).put("changed", JSONArray(listOf("role"))), at = f.now + 2_000)
                val futureRecovery = operation(f, RECOVERY_SET_COMMAND, RECOVERY_ENTITY, f.farmId,
                    JSONObject().put("farmId", f.farmId).put("recoveryHash", "not-applied-future-hash"), at = f.now + 2_000)
                val currentAccount = operation(f, ACCOUNT_SET_COMMAND, ACCOUNT_ENTITY, owner.accountId,
                    fields(owner.copy(displayName = "Current Owner")).put("changed", JSONArray(listOf("displayName"))))
                val currentRecovery = operation(f, RECOVERY_SET_COMMAND, RECOVERY_ENTITY, f.farmId,
                    JSONObject().put("farmId", f.farmId).put("recoveryHash", recovery.recoveryHash))
                val pending = receiver(f, emptyMap())
                assertFalse(pending.ingest(OperationBundle.seal(f.farmId, "origin",
                    listOf(futureAccount, futureRecovery, currentAccount, currentRecovery))).rejected)
                for (future in listOf(futureAccount, futureRecovery)) {
                    val app = f.database.replicationApplications().get(f.farmId, future.operationId)!!
                    f.database.replicationApplications().upsert(app.copy(state = pendingState.name, reason = "Waiting for a dependency"))
                }
                val unadmitted = operation(f, ACCOUNT_SET_COMMAND, ACCOUNT_ENTITY, owner.accountId,
                    fields(owner.copy(displayName = "Unadmitted future")).put("changed", JSONArray(listOf("displayName"))), at = f.now + 3_000)
                f.database.replication().insertOperation(unadmitted.toEntity())
                val missingAdmission = runCatching {
                    f.database.withTransaction { accessReplicationAppliers.getValue(ACCOUNT_SET_COMMAND).apply(f.database, unadmitted) }
                }.exceptionOrNull()
                assertTrue(missingAdmission?.message.orEmpty().contains("received application record"))

                // Apply only these two admitted receipts, just as one scheduler transaction does.
                for (current in listOf(currentAccount, currentRecovery)) {
                    f.database.withTransaction {
                        accessReplicationAppliers.getValue(current.operationType).apply(f.database, current)
                        val app = f.database.replicationApplications().get(f.farmId, current.operationId)!!
                        f.database.replicationApplications().upsert(app.copy(state = ApplicationState.APPLIED.name, reason = null))
                    }
                }
                assertEquals("OWNER", f.database.localAccess().account(f.farmId, owner.accountId)?.role)
                assertEquals("Current Owner", f.directory.account(f.farmId, owner.accountId)?.displayName)
                assertEquals(recovery.recoveryHash, f.database.localAccess().recoveryHash(f.farmId))
                for (future in listOf(futureAccount, futureRecovery)) {
                    assertEquals(pendingState.name, f.database.replicationApplications().get(f.farmId, future.operationId)?.state)
                }

                f.now += 1
                val recovered = f.directory.transact {
                    it.recoverOwner(f.farmId, owner.accountId, f.setup.recoveryCode, pin("639204"))
                } as RecoveryResult.Recovered
                assertEquals("OWNER", f.database.localAccess().account(f.farmId, owner.accountId)?.role)
                assertTrue(f.hasher.verify(RecoveryCode.normalise(recovered.newRecoveryCode), f.database.localAccess().recoveryHash(f.farmId)!!))
            }
        }
    }

    @Test
    fun aFailedApplicationCannotOverwriteASetAsideCommittedWhileItsTransactionRollsBack(): Unit = runBlocking(Dispatchers.IO) {
        LocalAccessTestFixture().use { f ->
            f.create()
            val owner = f.database.localAccess().account(f.farmId, f.setup.owner.accountId)!!
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val calls = AtomicInteger()
            val endpoint = receiver(f, mapOf(ACCOUNT_SET_COMMAND to OperationApplier { database, _ ->
                calls.incrementAndGet()
                database.localAccess().upsertAccount(owner.copy(displayName = "Uncommitted name"))
                entered.countDown()
                check(release.await(10, TimeUnit.SECONDS)) { "Test did not release the failing transaction" }
                throw IOException("injected access application failure")
            }))
            val op = operation(f, ACCOUNT_SET_COMMAND, ACCOUNT_ENTITY, owner.accountId,
                fields(owner.copy(displayName = "Uncommitted name")).put("changed", JSONArray(listOf("displayName"))))
            val receiving = async(Dispatchers.IO) {
                endpoint.ingest(OperationBundle.seal(f.farmId, "origin", listOf(op)))
            }
            try {
                assertTrue("The real Room application transaction must be running", entered.await(10, TimeUnit.SECONDS))
                // UNDISPATCHED starts the real review call now and queues its Room transaction
                // behind the held application, before the failure recorder can acquire the lock.
                val review = async(start = CoroutineStart.UNDISPATCHED) {
                    f.database.setAsideReceivedOperation(f.farmId, op.operationId, "Reviewed while delivery was failing",
                        LocalCommandContext(farmId = f.farmId, actorId = owner.accountId, deviceId = f.deviceId,
                            mutationId = UUID.randomUUID().toString(), occurredAtEpochMillis = f.now))
                }
                release.countDown()
                review.await()
                assertFalse(receiving.await().rejected)
                assertEquals(owner, f.database.localAccess().account(f.farmId, owner.accountId))
                val application = f.database.replicationApplications().get(f.farmId, op.operationId)!!
                assertEquals(ApplicationState.SET_ASIDE.name, application.state)
                assertEquals("Reviewed while delivery was failing", application.reason)
                endpoint.applyPendingNow()
                assertEquals(1, calls.get())
                assertEquals(op, f.database.replication().operation(f.farmId, op.operationId)?.toEnvelope())
                assertEquals(1, f.database.replication().operationsInRange(f.farmId, f.deviceId, 1, Long.MAX_VALUE)
                    .count { it.operationType == SET_ASIDE_COMMAND })
            } finally {
                release.countDown()
            }
        }
    }

    private suspend fun assertSetAsideRefused(f: LocalAccessTestFixture, op: OperationEnvelope) {
        val before = f.snapshot()
        val failure = runCatching {
            f.database.withTransaction { accessReplicationAppliers.getValue(op.operationType).apply(f.database, op) }
        }.exceptionOrNull()
        assertTrue(failure?.message.orEmpty().contains("set-aside access receipt"))
        assertEquals(before, f.snapshot())
        assertEquals(ApplicationState.SET_ASIDE.name, f.database.replicationApplications().get(f.farmId, op.operationId)?.state)
    }

    private fun setAsideDecision(f: LocalAccessTestFixture, targetId: String) =
        operation(f, SET_ASIDE_COMMAND, "replication_operation", targetId,
            JSONObject().put("operationId", targetId).put("reason", "Reviewed and excluded"))

    private fun receiver(f: LocalAccessTestFixture, appliers: Map<String, OperationApplier> = accessReplicationAppliers) =
        RoomReplicaEndpoint(f.database, f.farmId, f.deviceId, appliers).apply {
            registerPairedDevice("origin", "Known historical origin")
        }

    private suspend fun failed(f: LocalAccessTestFixture, endpoint: RoomReplicaEndpoint, op: OperationEnvelope) {
        assertFalse("The carrier records the immutable receipt", endpoint.ingest(OperationBundle.seal(f.farmId, "origin", listOf(op))).rejected)
        assertEquals(ApplicationState.FAILED.name, f.database.replicationApplications().get(f.farmId, op.operationId)?.state)
        assertEquals(op, f.database.replication().operation(f.farmId, op.operationId)?.toEnvelope())
    }

    private fun operation(f: LocalAccessTestFixture, command: String, entity: String, id: String, payload: JSONObject,
        operationId: String = UUID.randomUUID().toString(),
        at: Long = f.now,
    ) = OperationEnvelope.seal(
        operationId = operationId, farmId = f.farmId, entityType = entity, entityId = id,
        actorId = f.setup.owner.accountId, deviceId = "origin", deviceSequence = ++sequence,
        businessTimeEpochMillis = at, createdAtEpochMillis = at, baseVersion = null,
        operationType = command, mergeClass = CommandMergeClassification.forCommand(command),
        payload = mapOf(COMMAND_PAYLOAD_KEY to payload.toString()), schemaVersion = 1, provenance = "local",
    )

    private fun worker(f: LocalAccessTestFixture, id: String) = LocalAccountEntity(
        accountId = id, farmId = f.farmId, username = "worker", displayName = "Worker",
        role = "WORKER", status = "ACTIVE", credentialKind = "PIN", credentialHash = f.setup.owner.credentialHash,
        failedAttempts = 0, lockedUntilEpochMillis = null, workerId = null, createdAtEpochMillis = f.now,
    )

    private fun fields(account: LocalAccountEntity) = JSONObject()
        .put("accountId", account.accountId).put("farmId", account.farmId).put("username", account.username)
        .put("displayName", account.displayName).put("role", account.role).put("status", account.status)
        .put("credentialKind", account.credentialKind).put("credentialHash", account.credentialHash)
        .put("workerId", account.workerId ?: JSONObject.NULL).put("createdAtEpochMillis", account.createdAtEpochMillis)
        .put("changed", JSONArray(IDENTITY_FIELDS))
}
