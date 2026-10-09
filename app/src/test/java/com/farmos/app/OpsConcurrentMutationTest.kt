package com.farmos.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.ApplicationState
import com.farmos.core.database.COMMAND_PAYLOAD_KEY
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.LocalAccountEntity
import com.farmos.core.database.LocalFarmEntity
import com.farmos.core.database.ReplicationDeviceEntity
import com.farmos.core.database.FeedIssueEntity
import com.farmos.core.database.PurchaseEntity
import com.farmos.core.database.WaterRecordEntity
import com.farmos.core.database.toEnvelope
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.access.AccessDenied
import com.farmos.domain.access.AccountStatus
import com.farmos.domain.access.LocalRole
import com.farmos.domain.ops.AmendAnimalGroup
import com.farmos.domain.ops.CreateFarmAsset
import com.farmos.domain.ops.RecordWater
import com.farmos.domain.ops.ClosePoultryFlock
import com.farmos.domain.ops.CreateAnimalGroup
import com.farmos.domain.ops.RecordBudget
import com.farmos.domain.ops.RecordGroupCensus
import com.farmos.domain.ops.RecordUnitPreference
import com.farmos.domain.ops.ReviseBudget
import com.farmos.domain.replication.OperationBundle
import com.farmos.domain.replication.OperationEnvelope
import com.farmos.domain.replication.MergeClass
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Original bases survive replication: conflicting changes wait for review and are never renamed into later versions. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-rUS")
class OpsConcurrentMutationTest {
    private val farm = "11111111-1111-4111-8111-111111111111"
    private val otherFarm = "22222222-2222-4222-8222-222222222222"
    private val databases = mutableListOf<FarmOsDatabase>()
    private val start = 1_790_000_000_000L

    private fun database(device: String = "A") = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(), FarmOsDatabase::class.java,
    ).allowMainThreadQueries().build().also { db ->
        databases += db
        db.localAccess().insertFarmIfAbsent(LocalFarmEntity(farm, "Review farm", start))
        db.localAccess().upsertAccount(LocalAccountEntity(
            accountId = "owner-" + device, farmId = farm, username = "owner-" + device,
            displayName = "Owner " + device, role = LocalRole.OWNER.name, status = AccountStatus.ACTIVE.name,
            credentialKind = "PIN", credentialHash = "test-credential-hash", failedAttempts = 0,
            lockedUntilEpochMillis = null, workerId = null, createdAtEpochMillis = start,
        ))
        db.replicationBlocking().upsertDevice(ReplicationDeviceEntity(
            farmId = farm, deviceId = device, name = device, status = "ACTIVE",
            lastReportedOwnSequence = 0L, revokedAfterSequence = null, isLocal = true,
        ))
    }

    private fun account(db: FarmOsDatabase, device: String = "A") =
        requireNotNull(db.localAccess().account(farm, "owner-" + device))

    private fun endpoint(db: FarmOsDatabase, id: String, vararg peers: String) =
        RoomReplicaEndpoint(db, farm, id, replicationAppliers).apply { peers.forEach { registerPairedDevice(it, it) } }

    private fun context(device: String, offset: Long) =
        LocalCommandContext(farm, "owner-" + device, device, UUID.randomUUID().toString(), start + offset)

    private suspend fun send(db: FarmOsDatabase, origin: String, target: RoomReplicaEndpoint) {
        val last = db.replication().device(farm, origin)?.lastReportedOwnSequence ?: 0L
        if (last == 0L) return
        val operations = db.replication().operationsInRange(farm, origin, 1, last).map { it.toEnvelope() }
        assertNull(target.ingest(OperationBundle.seal(farm, origin, operations)).rejectedReason)
    }

    private suspend fun exchange(aDb: FarmOsDatabase, a: RoomReplicaEndpoint, bDb: FarmOsDatabase, b: RoomReplicaEndpoint) {
        send(aDb, "A", b)
        send(bDb, "B", a)
    }

    @After
    fun close() = databases.forEach { it.close() }

    @Test
    fun concurrentBudgetRevisionsKeepTheirOriginalVersionAndBothJournalRecords() = runBlocking {
        val aDb = database(); val bDb = database("B")
        val a = endpoint(aDb, "A", "B"); val b = endpoint(bDb, "B", "A")
        val aOps = RoomOpsRepository(aDb, farm); val bOps = RoomOpsRepository(bDb, farm)
        aOps.recordBudget(RecordBudget("budget-1", "feed-usd", "Feed", "expense", "feed", "2026-10", "2026-10", 10_000L, "USD"), context("A", 1))
        send(aDb, "A", b)

        val aChange = context("A", 2); val bChange = context("B", 3)
        aOps.reviseBudget(ReviseBudget("budget-a", "feed-usd", "A reviewed feed", 12_000L), aChange)
        bOps.reviseBudget(ReviseBudget("budget-b", "feed-usd", "B reviewed feed", 14_000L), bChange)
        exchange(aDb, a, bDb, b)

        assertEquals(listOf(2L, 1L), aOps.budgetRevisions("feed-usd").map { it.version })
        assertEquals(listOf(2L, 1L), bOps.budgetRevisions("feed-usd").map { it.version })
        assertEquals(12_000L, aOps.budgets().single().amountMinor)
        assertEquals(14_000L, bOps.budgets().single().amountMinor)
        for (db in listOf(aDb, bDb)) {
            val operations = db.replication().operationsForEntity(farm, "budget", "feed-usd")
            assertEquals(setOf(aChange.mutationId, bChange.mutationId), operations.filter { it.operationType == "finance.revise_budget.v1" }.map { it.operationId }.toSet())
            assertTrue(operations.all { it.toEnvelope().checksumValid() })
            assertEquals(1L, db.loadConflictReview(farm).waitingTotal)
        }
        assertEquals(ApplicationState.FAILED.name, aDb.replicationApplications().get(farm, bChange.mutationId)?.state)
        assertTrue(aDb.replicationApplications().get(farm, bChange.mutationId)?.reason.orEmpty().contains("version 1 to 2"))
        // Repeated delivery cannot fabricate revision 3 or discard the pending revision.
        exchange(aDb, a, bDb, b)
        assertEquals(listOf(2L, 1L), aOps.budgetRevisions("feed-usd").map { it.version })
        assertEquals(1L, aDb.loadConflictReview(farm).waitingTotal)
    }

    @Test
    fun sequentialBudgetRevisionArrivingBeforeItsBaseWaitsAndThenKeepsTheOriginVersion() = runBlocking {
        val aDb = database(); val bDb = database("B")
        val b = endpoint(bDb, "B", "A")
        endpoint(aDb, "A", "B")
        val ops = RoomOpsRepository(aDb, farm)
        ops.recordBudget(RecordBudget("budget-1", "feed-usd", "Feed", "expense", "feed", "2026-10", "2026-10", 10_000L, "USD"), context("A", 1))
        ops.reviseBudget(ReviseBudget("budget-2", "feed-usd", "Feed revised", 20_000L), context("A", 2))
        val later = aDb.replication().operationsInRange(farm, "A", 2, 2).map { it.toEnvelope() }
        assertNull(b.ingest(OperationBundle.seal(farm, "A", later)).rejectedReason)
        assertEquals(1L, bDb.loadConflictReview(farm).waitingTotal)
        send(aDb, "A", b)
        assertEquals(0L, bDb.loadConflictReview(farm).waitingTotal)
        assertEquals(ops.budgetRevisions("feed-usd"), RoomOpsRepository(bDb, farm).budgetRevisions("feed-usd"))
    }

    @Test
    fun separateUnitFieldsMergeButConcurrentChangesToOneUnitRemainForReview() = runBlocking {
        val aDb = database(); val bDb = database("B")
        val a = endpoint(aDb, "A", "B"); val b = endpoint(bDb, "B", "A")
        val aOps = RoomOpsRepository(aDb, farm); val bOps = RoomOpsRepository(bDb, farm)
        aOps.recordUnitPreference(RecordUnitPreference(farm, "weight", "kg"), context("A", 1))
        bOps.recordUnitPreference(RecordUnitPreference(farm, "volume", "L"), context("B", 2))
        exchange(aDb, a, bDb, b)
        assertEquals(mapOf("volume" to "L", "weight" to "kg"), bOps.unitPreferences().associate { it.quantityKind to it.displayUnit })
        assertEquals(0L, aDb.loadConflictReview(farm).waitingTotal)

        aOps.recordUnitPreference(RecordUnitPreference(farm, "weight", "lb"), context("A", 3))
        bOps.recordUnitPreference(RecordUnitPreference(farm, "weight", "oz"), context("B", 4))
        exchange(aDb, a, bDb, b)
        assertEquals("lb", aOps.unitPreferences().single { it.quantityKind == "weight" }.displayUnit)
        assertEquals("oz", bOps.unitPreferences().single { it.quantityKind == "weight" }.displayUnit)
        assertEquals(1L, aDb.loadConflictReview(farm).waitingTotal)
        assertEquals(1L, bDb.loadConflictReview(farm).waitingTotal)
        assertEquals(4L, aDb.replication().count(farm))
        assertEquals(4L, bDb.replication().count(farm))
    }

    @Test
    fun legacyFarmPreferenceHistoryReplaysAndCannotOverwriteNewPerQuantityEdits() = runBlocking {
        val bDb = database("B"); val cDb = database("C")
        val b = endpoint(bDb, "B", "A", "C"); val c = endpoint(cDb, "C", "A", "B")
        fun legacy(sequence: Long, kind: String, unit: String) = OperationEnvelope.seal(
            operationId = UUID.randomUUID().toString(), farmId = farm,
            entityType = "farm", entityId = farm, actorId = "owner-A", deviceId = "A",
            deviceSequence = sequence, businessTimeEpochMillis = start + sequence,
            createdAtEpochMillis = start + sequence, baseVersion = sequence - 1,
            operationType = "farm.record_unit_preference.v1", mergeClass = MergeClass.APPEND_ONLY_EVENT,
            payload = mapOf(COMMAND_PAYLOAD_KEY to JSONObject().put("farmId", farm)
                .put("quantityKind", kind).put("displayUnit", unit).toString()),
            schemaVersion = 1, provenance = "legacy-unit-preference-test",
        )
        val oldOperations = listOf(legacy(1, "weight", "kg"), legacy(2, "volume", "L"), legacy(3, "weight", "lb"))
        val oldBundle = OperationBundle.seal(farm, "A", oldOperations)
        assertNull(b.ingest(oldBundle).rejectedReason)
        assertNull(c.ingest(oldBundle).rejectedReason)
        assertEquals(0L, bDb.loadConflictReview(farm).waitingTotal)
        val ops = RoomOpsRepository(bDb, farm)
        assertEquals(mapOf("volume" to "L", "weight" to "lb"), ops.unitPreferences().associate { it.quantityKind to it.displayUnit })

        val correction = context("B", 10)
        ops.recordUnitPreference(RecordUnitPreference(farm, "weight", "oz"), correction)
        assertEquals(2L, bDb.replication().operation(farm, correction.mutationId)?.baseVersion)
        send(bDb, "B", c)
        assertEquals(ops.unitPreferences(), RoomOpsRepository(cDb, farm).unitPreferences())
        assertEquals(0L, cDb.loadConflictReview(farm).waitingTotal)

        val delayedOldWriter = legacy(4, "weight", "kg")
        assertNull(b.ingest(OperationBundle.seal(farm, "A", listOf(delayedOldWriter))).rejectedReason)
        assertEquals("oz", ops.unitPreferences().single { it.quantityKind == "weight" }.displayUnit)
        assertEquals(1L, bDb.loadConflictReview(farm).waitingTotal)
        assertTrue(bDb.loadConflictReview(farm).waiting.single().reason.orEmpty().contains("newer unit preference"))
        assertEquals(delayedOldWriter, bDb.replication().operation(farm, delayedOldWriter.operationId)?.toEnvelope())
    }

    @Test
    fun concurrentGroupEditsRequireExplicitReviewAndARecordedCorrectionConverges() = runBlocking {
        val aDb = database(); val bDb = database("B")
        val a = endpoint(aDb, "A", "B"); val b = endpoint(bDb, "B", "A")
        val aOps = RoomOpsRepository(aDb, farm); val bOps = RoomOpsRepository(bDb, farm)
        aOps.createGroup(CreateAnimalGroup("group-1", "goat", "Original", 10), context("A", 1))
        send(aDb, "A", b)
        val aEdit = context("A", 2); val bEdit = context("B", 3)
        aOps.amendGroup(AmendAnimalGroup("group-1", "Alpha", "goat"), aEdit)
        bOps.amendGroup(AmendAnimalGroup("group-1", "Beta", "goat"), bEdit)
        exchange(aDb, a, bDb, b)
        assertEquals("Alpha", aOps.group("group-1")?.name)
        assertEquals("Beta", bOps.group("group-1")?.name)
        assertEquals(bEdit.mutationId, aDb.loadConflictReview(farm).waiting.single().operationId)
        assertEquals(aEdit.mutationId, bDb.loadConflictReview(farm).waiting.single().operationId)

        // Review closes the competing received changes. A new authoritative correction is a new operation.
        aDb.setAsideReceivedOperation(farm, bEdit.mutationId, "Reconciled against the farm register", context("A", 4))
        bDb.setAsideReceivedOperation(farm, aEdit.mutationId, "Reconciled against the farm register", context("B", 5))
        aOps.amendGroup(AmendAnimalGroup("group-1", "Reviewed register", "goat"), context("A", 6))
        exchange(aDb, a, bDb, b)
        assertEquals(aOps.group("group-1"), bOps.group("group-1"))
        assertEquals("Reviewed register", bOps.group("group-1")?.name)
        assertEquals(0L, aDb.loadConflictReview(farm).waitingTotal)
        assertEquals(0L, bDb.loadConflictReview(farm).waitingTotal)
        for (db in listOf(aDb, bDb)) {
            assertNotNull(db.replication().operation(farm, aEdit.mutationId))
            assertNotNull(db.replication().operation(farm, bEdit.mutationId))
        }
    }

    @Test
    fun flockClosureIsTerminalAndCannotBeAppliedToAnotherSpecies() = runBlocking {
        val db = database(); val ops = RoomOpsRepository(db, farm)
        ops.createGroup(CreateAnimalGroup("flock-1", "poultry", "Layers", 10), context("A", 1))
        ops.closeFlock(ClosePoultryFlock("close-1", "flock-1", 10, "Production cycle ended", 20_700), context("A", 2))
        assertEquals(0, ops.group("flock-1")?.headCount)
        val countBefore = db.replication().count(farm)
        assertTrue(runCatching { ops.recordCensus(RecordGroupCensus("census-1", "flock-1", 5, 20_701), context("A", 3)) }.isFailure)
        assertTrue(runCatching { ops.closeFlock(ClosePoultryFlock("close-2", "flock-1", 5, "Second close", 20_701), context("A", 4)) }.isFailure)
        assertTrue(runCatching { ops.amendGroup(AmendAnimalGroup("flock-1", "Reused as goats", "goat"), context("A", 5)) }.isFailure)
        assertEquals(countBefore, db.replication().count(farm))
        ops.createGroup(CreateAnimalGroup("goats", "goat", "Does", 10), context("A", 5))
        assertTrue(runCatching { ops.closeFlock(ClosePoultryFlock("bad-close", "goats", 10, "Wrong species", 20_701), context("A", 6)) }.isFailure)
        assertEquals(10, ops.group("goats")?.headCount)
    }

    @Test
    fun workRolesCannotChangeManagementSettingsAndViewersCannotWriteThroughAnyExtractedHandler() = runBlocking {
        val db = database(); val ops = RoomOpsRepository(db, farm)
        db.localAccess().upsertAccount(account(db).copy(role = LocalRole.WORKER.name))
        ops.recordWater(RecordWater("worker-water", "Borehole", 1_000L, 20_700L), context("A", 1))
        assertEquals(1, ops.waterRecordCount())
        assertTrue(runCatching {
            ops.recordUnitPreference(RecordUnitPreference(farm, "weight", "kg"), context("A", 2))
        }.exceptionOrNull() is AccessDenied)
        assertTrue(ops.unitPreferences().isEmpty())

        db.localAccess().upsertAccount(account(db).copy(role = LocalRole.VIEWER.name))
        val denied = listOf<suspend () -> Unit>(
            { ops.recordBudget(RecordBudget("viewer-budget", "feed-usd", "Feed", "expense", "feed", "2026-10", "2026-10", 1_000L, "USD"), context("A", 3)) },
            { ops.createGroup(CreateAnimalGroup("viewer-group", "goat", "Does", 10), context("A", 4)) },
            { ops.createAsset(CreateFarmAsset("viewer-asset", "TR-1", "Tractor"), context("A", 5)) },
        )
        denied.forEach { attempt -> assertTrue(runCatching { attempt() }.exceptionOrNull() is AccessDenied) }
        assertTrue(ops.budgets().isEmpty())
        assertNull(ops.group("viewer-group"))
        assertTrue(ops.assets().isEmpty())
        assertEquals(1L, db.replication().count(farm))
        assertEquals(1L, db.outbox().countUnacknowledgedForFarm(farm))
    }

    @Test
    fun inactiveMissingAndOtherFarmActorsAreDeniedWithoutDomainOrJournalWrites() = runBlocking {
        val db = database(); val ops = RoomOpsRepository(db, farm)
        val original = account(db)
        db.localAccess().upsertAccount(original.copy(status = AccountStatus.DISABLED.name))
        assertTrue(runCatching {
            ops.recordWater(RecordWater("inactive", "Borehole", 1_000L, 20_700L), context("A", 1))
        }.exceptionOrNull() is AccessDenied)
        db.localAccess().upsertAccount(original)
        db.localAccess().upsertAccount(original.copy(accountId = "foreign-actor", farmId = otherFarm, username = "foreign"))
        for (actor in listOf("missing-actor", "foreign-actor")) {
            assertTrue(runCatching {
                ops.recordWater(RecordWater(actor, "Borehole", 1_000L, 20_700L), context("A", 2).copy(actorId = actor))
            }.exceptionOrNull() is AccessDenied)
        }
        assertTrue(runCatching {
            ops.recordWater(RecordWater("wrong-farm", "Borehole", 1_000L, 20_700L), context("A", 3).copy(farmId = otherFarm))
        }.exceptionOrNull() is IllegalArgumentException)
        assertEquals(0, ops.waterRecordCount())
        assertEquals(0, db.water().count(otherFarm))
        assertEquals(0L, db.replication().count(farm))
        assertEquals(0L, db.replication().count(otherFarm))
        assertEquals(0L, db.outbox().countUnacknowledgedForFarm(farm))
    }

    @Test
    fun revokedNonlocalAndUnknownDevicesCannotRecordOrAdvanceASequence() = runBlocking {
        val db = database(); val ops = RoomOpsRepository(db, farm)
        val original = requireNotNull(db.replication().device(farm, "A"))
        val deniedDevices = listOf(
            original.copy(revokedAfterSequence = 0L),
            original.copy(status = "REVOKED", revokedAfterSequence = 0L),
            original.copy(isLocal = false),
        )
        deniedDevices.forEachIndexed { index, device ->
            db.replication().upsertDevice(device)
            assertTrue(runCatching {
                ops.createGroup(CreateAnimalGroup("denied-" + index, "goat", "Does", 10), context("A", index.toLong()))
            }.exceptionOrNull() is AccessDenied)
            assertNull(ops.group("denied-" + index))
            assertEquals(0L, db.replication().device(farm, "A")?.lastReportedOwnSequence)
        }
        assertTrue(runCatching {
            ops.recordWater(RecordWater("unknown-device", "Borehole", 1_000L, 20_700L), context("A", 4).copy(deviceId = "unknown"))
        }.exceptionOrNull() is AccessDenied)
        assertEquals(0, ops.waterRecordCount())
        assertEquals(0L, db.replication().count(farm))
        assertEquals(0L, db.outbox().countUnacknowledgedForFarm(farm))
    }

    @Test
    fun historicalReceiptsAreNotReauthorizedAgainstALaterRoleChange() = runBlocking {
        val aDb = database(); val bDb = database("B")
        endpoint(aDb, "A", "B"); val b = endpoint(bDb, "B", "A")
        val ops = RoomOpsRepository(aDb, farm)
        val created = context("A", 1)
        ops.createGroup(CreateAnimalGroup("historic-group", "goat", "Does", 10), created)
        val disabled = account(aDb).copy(role = LocalRole.VIEWER.name, status = AccountStatus.DISABLED.name)
        aDb.localAccess().upsertAccount(disabled)
        bDb.localAccess().upsertAccount(disabled)
        send(aDb, "A", b)
        assertEquals("Does", RoomOpsRepository(bDb, farm).group("historic-group")?.name)
        assertEquals(ApplicationState.APPLIED.name, bDb.replicationApplications().get(farm, created.mutationId)?.state)
        assertEquals(0L, bDb.loadConflictReview(farm).waitingTotal)
        assertTrue(runCatching {
            ops.amendGroup(AmendAnimalGroup("historic-group", "Disallowed edit", "goat"), context("A", 2))
        }.exceptionOrNull() is AccessDenied)
        assertEquals("Does", ops.group("historic-group")?.name)
        assertEquals(1L, aDb.replication().count(farm))
    }

    @Test
    fun reportReadersIncludeRowsBeyondFiftyAndRetainFarmIsolation() = runBlocking {
        val db = database(); val ops = RoomOpsRepository(db, farm)
        for (i in 1..61) {
            db.feedIssues().insert(FeedIssueEntity("feed-" + i, farm, "item-1", null, 1_000L, 20_700L + i))
            db.water().insert(WaterRecordEntity("water-" + i, farm, "Borehole", 2_000L, 20_700L + i))
            db.lifecycle().insertPurchase(PurchaseEntity("purchase-" + i, farm, "supplier-1", "item-1", 1_000L, 100L, "USD", 20_700L + i))
        }
        db.feedIssues().insert(FeedIssueEntity("other-feed", otherFarm, "item-1", null, 900_000L, 20_700L))
        db.water().insert(WaterRecordEntity("other-water", otherFarm, "Borehole", 900_000L, 20_700L))
        db.lifecycle().insertPurchase(PurchaseEntity("other-purchase", otherFarm, "supplier-1", "item-1", 1_000L, 900_000L, "USD", 20_700L))
        assertEquals(50, ops.recentFeed().size)
        assertEquals(61, ops.feedIssueCount())
        assertEquals(61_000L, ops.feedTotalsByItem().single().quantityMilli)
        assertEquals(50, ops.recentWater().size)
        assertEquals(61, ops.waterRecordCount())
        assertEquals(122_000L, ops.waterTotalsBySource().single().litresMilli)
        assertEquals(50, ops.purchases().size)
        assertEquals(61, ops.allPurchases().size)
        assertEquals(6_100L, ops.allPurchases().sumOf { it.amountMinor })
    }
}
