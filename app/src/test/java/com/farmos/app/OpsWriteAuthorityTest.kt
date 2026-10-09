package com.farmos.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.ApplicationState
import com.farmos.core.database.COMMAND_PAYLOAD_KEY
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.toEnvelope
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.CustomerCommands
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.access.AccessDenied
import com.farmos.domain.access.LocalRole
import com.farmos.domain.ops.CreateFarmCustomer
import com.farmos.domain.ops.CreateFarmTask
import com.farmos.domain.ops.CompleteFarmTask
import com.farmos.domain.ops.CreateInventoryItem
import com.farmos.domain.ops.EnablePoultryKind
import com.farmos.domain.ops.MoveInventory
import com.farmos.domain.ops.RecordBudget
import com.farmos.domain.ops.RecordCustomerSale
import com.farmos.domain.ops.RecordMoney
import com.farmos.domain.ops.UpdateFarmCustomer
import com.farmos.domain.ops.SetInventoryReorder
import com.farmos.domain.replication.OperationBundle
import com.farmos.domain.replication.OperationEnvelope
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Real Room transactions, including failures after business writes and before journal commit. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-rUS")
class OpsWriteAuthorityTest {
    private val farm = "11111111-1111-4111-8111-111111111111"
    private val otherFarm = "22222222-2222-4222-8222-222222222222"
    private val databases = mutableListOf<FarmOsDatabase>()
    private val time = 1_790_000_000_000L

    private fun database(device: String = "A") = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(), FarmOsDatabase::class.java,
    ).allowMainThreadQueries().build().also { db ->
        databases += db
        for (role in LocalRole.entries) seedCommandAuthority(db, farm, role.name.lowercase(), device, role)
    }

    private fun context(actor: String = "manager", device: String = "A") =
        LocalCommandContext(farm, actor, device, UUID.randomUUID().toString(), time)

    private fun money(id: String) = RecordMoney(id, "expense", "feed", 1_000L, "USD", 20_700L)

    private suspend fun assertNoPost(db: FarmOsDatabase, journalCount: Long = 0L, sequence: Long = 0L) {
        assertTrue(db.money().recent(farm, 10).isEmpty())
        assertTrue(db.sales().recent(farm, 10).isEmpty())
        assertEquals(journalCount, db.replication().count(farm))
        assertEquals(0L, db.outbox().countUnacknowledgedForFarm(farm))
        assertEquals(sequence, db.replication().device(farm, "A")?.lastReportedOwnSequence)
    }

    @After
    fun close() = databases.forEach { it.close() }

    @Test
    fun missingDisabledForeignAndReRoledActorsCannotConsumeASequence(): Unit = runBlocking {
        val db = database()
        val ops = RoomOpsRepository(db, farm)
        seedCommandAuthority(db, otherFarm, "foreign", "foreign-device", LocalRole.OWNER)
        val manager = requireNotNull(db.localAccess().account(farm, "manager"))
        val capturedContext = context()
        db.localAccess().upsertAccount(manager.copy(role = LocalRole.VIEWER.name))
        assertTrue(runCatching { ops.recordMoney(money("re-roled"), capturedContext) }.exceptionOrNull() is AccessDenied)
        db.localAccess().upsertAccount(manager.copy(status = "DISABLED"))
        assertTrue(runCatching { ops.recordMoney(money("disabled"), context()) }.exceptionOrNull() is AccessDenied)
        db.localAccess().upsertAccount(manager.copy(role = "UNKNOWN_ROLE"))
        assertTrue(runCatching { ops.recordMoney(money("unknown-role"), context()) }.exceptionOrNull() is AccessDenied)
        for (actor in listOf("missing", "foreign", "viewer", "worker", "supervisor")) {
            assertTrue(actor, runCatching { ops.recordMoney(money(actor), context(actor)) }.exceptionOrNull() is AccessDenied)
        }
        assertTrue(runCatching {
            ops.recordMoney(money("wrong-farm"), context("owner").copy(farmId = otherFarm))
        }.exceptionOrNull() is IllegalArgumentException)
        assertNoPost(db)
        assertEquals(0L, db.replication().count(otherFarm))
        assertTrue(db.money().recent(otherFarm, 10).isEmpty())
    }

    @Test
    fun unknownNonlocalRetiredAndRevokedDevicesCannotWriteEvenForAnOwner(): Unit = runBlocking {
        val db = database()
        val ops = RoomOpsRepository(db, farm)
        val active = requireNotNull(db.replication().device(farm, "A"))
        for (device in listOf(
            active.copy(isLocal = false),
            active.copy(status = "RETIRED"),
            active.copy(status = "LOST_REVOKED", revokedAfterSequence = 0L),
            active.copy(revokedAfterSequence = 0L),
        )) {
            db.replication().upsertDevice(device)
            assertTrue(runCatching { ops.recordMoney(money(UUID.randomUUID().toString()), context("owner")) }.exceptionOrNull() is AccessDenied)
            assertNoPost(db)
        }
        assertTrue(runCatching {
            ops.recordMoney(money("unknown-device"), context("owner", "unknown"))
        }.exceptionOrNull() is AccessDenied)
        assertNull(db.replication().device(farm, "unknown"))
        assertNoPost(db)
    }

    @Test
    fun workersStillReceiveAndIssueStockAndCompleteWorkButCannotPostMoneyOrConfigureTheFarm(): Unit = runBlocking {
        val db = database()
        val ops = RoomOpsRepository(db, farm)
        ops.createItem(CreateInventoryItem("feed", "FEED", "Feed"), context())
        ops.move(MoveInventory("receive", "feed", "receive", 5_000L, time), context("worker"))
        ops.move(MoveInventory("issue", "feed", "issue", 1_000L, time), context("worker"))
        ops.createTask(CreateFarmTask("task", "goat", "CHECK", "Check water", 20_700L), context("supervisor"))
        ops.completeTask(CompleteFarmTask("task"), context("worker"))
        val before = db.replication().count(farm)
        val denied = listOf<suspend () -> Unit>(
            { ops.recordMoney(money("worker-money"), context("worker")) },
            { ops.recordBudget(RecordBudget("budget", "feed-usd", "Feed", "expense", "feed", "2026-10", "2026-10", 1_000L), context("worker")) },
            { ops.setReorder(SetInventoryReorder("feed", 1_000L), context("worker")) },
            { ops.createTask(CreateFarmTask("worker-task", "goat", "CHECK", "Assign work", 20_700L), context("worker")) },
            { ops.enablePoultryKind(EnablePoultryKind("duck"), context("worker")) },
        )
        denied.forEach { assertTrue(runCatching { it() }.exceptionOrNull() is AccessDenied) }
        assertEquals(4_000L, db.inventory().item(farm, "feed")?.quantityMilli)
        assertEquals(listOf("task"), ops.completedTasks().map { it.id })
        assertTrue(ops.budgets().isEmpty())
        assertTrue(db.money().recent(farm, 10).isEmpty())
        assertEquals(before, db.replication().count(farm))
        assertEquals(before, db.outbox().countUnacknowledgedForFarm(farm))
        assertEquals(before, db.replication().device(farm, "A")?.lastReportedOwnSequence)
    }

    @Test
    fun currentCustomerSalesCannotBypassMoneyAuthorityAndExactRetriesDoNotDoublePost(): Unit = runBlocking {
        val db = database()
        val customers = CustomerCommands(db, farm)
        customers.create(CreateFarmCustomer("customer", "Farm buyer"), context())
        val sale = RecordCustomerSale("sale", "customer", "Farm buyer", "live_goat", 1_000L, 5_000L, "USD", 20_700L)
        seedCommandAuthority(db, otherFarm, "foreign", "foreign-device", LocalRole.OWNER)
        val owner = requireNotNull(db.localAccess().account(farm, "owner"))
        db.localAccess().upsertAccount(owner.copy(status = "DISABLED"))
        for (actor in listOf("missing", "foreign", "owner", "worker", "viewer", "supervisor")) {
            assertTrue(actor, runCatching { customers.recordSale(sale, context(actor)) }.exceptionOrNull() is AccessDenied)
            assertTrue(actor, runCatching {
                customers.create(CreateFarmCustomer("denied-" + actor, "Denied buyer"), context(actor))
            }.exceptionOrNull() is AccessDenied)
        }
        assertNoPost(db, journalCount = 1L, sequence = 1L)
        val admitted = context()
        customers.recordSale(sale, admitted)
        customers.recordSale(sale, admitted)
        assertEquals(1, db.sales().recent(farm, 10).size)
        assertEquals(1, db.money().recent(farm, 10).size)
        assertEquals(2L, db.replication().count(farm))
        assertEquals(2L, db.replication().device(farm, "A")?.lastReportedOwnSequence)
        customers.update(UpdateFarmCustomer("customer", active = false), context())
        customers.recordSale(sale, admitted)
        assertEquals(1, db.sales().recent(farm, 10).size)
        assertEquals(3L, db.replication().count(farm))
        assertTrue(runCatching { customers.recordSale(sale.copy(saleId = "new-closed-customer-sale"), context()) }.isFailure)
        assertEquals(3L, db.replication().count(farm))
    }

    @Test
    fun replayBindsPayloadActorDeviceTimeAndEntityWithoutReauthorizingLaterAccountChanges(): Unit = runBlocking {
        val origin = database()
        val receiver = database("B")
        val admitted = context()
        val command = money("historical")
        RoomOpsRepository(origin, farm).recordMoney(command, admitted)
        val operation = requireNotNull(origin.replication().operation(farm, admitted.mutationId)).toEnvelope()
        val pending = RoomReplicaEndpoint(receiver, farm, "B").apply { registerPairedDevice("A", "Origin") }
        val bundle = OperationBundle.seal(farm, "A", listOf(operation))
        assertNull(pending.ingest(bundle).rejectedReason)
        val replay = RoomOpsRepository(receiver, farm, replaying = true)
        val altered = listOf(
            admitted.copy(actorId = "owner"),
            admitted.copy(deviceId = "B"),
            admitted.copy(occurredAtEpochMillis = time + 1L),
            admitted.copy(mutationId = UUID.randomUUID().toString()),
        )
        altered.forEach { assertTrue(runCatching { replay.recordMoney(command, it) }.isFailure) }
        assertTrue(runCatching { replay.recordMoney(command.copy(amountMinor = 2_000L), admitted) }.isFailure)
        assertTrue(runCatching { replay.recordMoney(command.copy(recordId = "other-record"), admitted) }.isFailure)
        assertTrue(receiver.money().recent(farm, 10).isEmpty())
        val oldActor = requireNotNull(receiver.localAccess().account(farm, "manager"))
        receiver.localAccess().upsertAccount(oldActor.copy(role = "VIEWER", status = "DISABLED"))
        val applying = RoomReplicaEndpoint(receiver, farm, "B", replicationAppliers)
        assertNull(applying.ingest(bundle).rejectedReason)
        assertEquals(ApplicationState.APPLIED.name, receiver.replicationApplications().get(farm, admitted.mutationId)?.state)
        assertEquals(origin.money().recent(farm, 10), receiver.money().recent(farm, 10))
        replay.recordMoney(command, admitted)
        assertEquals(1, receiver.money().recent(farm, 10).size)
        assertEquals(1L, receiver.replication().count(farm))
        assertEquals(0L, receiver.outbox().countUnacknowledgedForFarm(farm))
    }

    @Test
    fun customerReplayNeedsTheOriginalAdmissionAndKeepsAnOlderPayloadWithOmittedDefaults(): Unit = runBlocking {
        val origin = database()
        val receiver = database("B")
        val command = CreateFarmCustomer("historical-customer", "Known buyer")
        val admitted = context()
        CustomerCommands(origin, farm).create(command, admitted)
        val original = requireNotNull(origin.replication().operation(farm, admitted.mutationId)).toEnvelope()
        val historicalPayload = Json.encodeToString(command)
        assertFalse(historicalPayload.contains("phone"))
        val operation = OperationEnvelope.seal(
            operationId = original.operationId, farmId = original.farmId,
            entityType = original.entityType, entityId = original.entityId, actorId = original.actorId,
            deviceId = original.deviceId, deviceSequence = original.deviceSequence,
            businessTimeEpochMillis = original.businessTimeEpochMillis, createdAtEpochMillis = original.createdAtEpochMillis,
            baseVersion = original.baseVersion, operationType = original.operationType, mergeClass = original.mergeClass,
            payload = mapOf(COMMAND_PAYLOAD_KEY to historicalPayload), schemaVersion = original.schemaVersion,
            provenance = "historical-default-omission-fixture",
        )
        val pending = RoomReplicaEndpoint(receiver, farm, "B").apply { registerPairedDevice("A", "Origin") }
        val bundle = OperationBundle.seal(farm, "A", listOf(operation))
        assertNull(pending.ingest(bundle).rejectedReason)
        val replay = CustomerCommands(receiver, farm, replaying = true)
        assertTrue(runCatching { replay.create(command.copy(name = "Forged buyer"), admitted) }.isFailure)
        assertTrue(runCatching { replay.create(command, admitted.copy(actorId = "owner")) }.isFailure)
        assertTrue(runCatching { replay.create(command, admitted.copy(mutationId = "not-journalled")) }.isFailure)
        assertTrue(receiver.customers().all(farm).isEmpty())
        val actor = requireNotNull(receiver.localAccess().account(farm, "manager"))
        receiver.localAccess().upsertAccount(actor.copy(status = "DISABLED"))
        assertNull(RoomReplicaEndpoint(receiver, farm, "B", replicationAppliers).ingest(bundle).rejectedReason)
        replay.create(command, admitted)
        assertEquals(origin.customers().all(farm), receiver.customers().all(farm))
        assertEquals(1L, receiver.replication().count(farm))
    }

    @Test
    fun failureInJournalInsertionRollsBackMoneyOutboxAndTheAllocatedSequence(): Unit = runBlocking {
        val db = database()
        val ops = RoomOpsRepository(db, farm)
        val admitted = context()
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_money_journal BEFORE INSERT ON replication_operations " +
                "WHEN NEW.operationType = 'money.record.v1' BEGIN SELECT RAISE(ABORT, 'journal fixture'); END",
        )
        assertTrue(runCatching { ops.recordMoney(money("atomic"), admitted) }.isFailure)
        assertNoPost(db)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_money_journal")
        ops.recordMoney(money("atomic"), admitted)
        assertEquals(1, db.money().recent(farm, 10).size)
        assertEquals(1L, db.outbox().countUnacknowledgedForFarm(farm))
        assertEquals(1L, db.replication().operation(farm, admitted.mutationId)?.deviceSequence)
    }

    @Test
    fun customerJournalFailureRollsBackSaleAndMoneyTogetherThenRetryUsesTheSameIdentity(): Unit = runBlocking {
        val db = database()
        val customers = CustomerCommands(db, farm)
        customers.create(CreateFarmCustomer("customer", "Farm buyer"), context())
        val admitted = context()
        val sale = RecordCustomerSale("atomic-sale", "customer", "Farm buyer", "live_goat", 1_000L, 5_000L, "USD", 20_700L)
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_sale_journal BEFORE INSERT ON replication_operations " +
                "WHEN NEW.operationType = 'sale.record.v2' BEGIN SELECT RAISE(ABORT, 'journal fixture'); END",
        )
        assertTrue(runCatching { customers.recordSale(sale, admitted) }.isFailure)
        assertNoPost(db, journalCount = 1L, sequence = 1L)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_sale_journal")
        customers.recordSale(sale, admitted)
        customers.recordSale(sale, admitted)
        assertEquals(1, db.sales().recent(farm, 10).size)
        assertEquals(1, db.money().recent(farm, 10).size)
        assertEquals(2L, db.replication().operation(farm, admitted.mutationId)?.deviceSequence)
        assertEquals(2L, db.replication().count(farm))
        assertEquals(0L, db.outbox().countUnacknowledgedForFarm(farm))
    }
}
