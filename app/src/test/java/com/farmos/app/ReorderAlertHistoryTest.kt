package com.farmos.app

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.ApplicationState
import com.farmos.core.database.COMMAND_PAYLOAD_KEY
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.InventoryItemEntity
import com.farmos.core.database.ReorderAlertEntity
import com.farmos.core.database.journalLocalOperation
import com.farmos.core.database.toEnvelope
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.access.AccessDenied
import com.farmos.domain.access.LocalRole
import com.farmos.domain.ops.RecordReorderAlert
import com.farmos.domain.replication.OperationBundle
import com.farmos.domain.replication.OperationEnvelope
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Immutable alert observations, not a reconstruction from the receiver's present inventory. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-rUS")
class ReorderAlertHistoryTest {
    private val farm = "11111111-1111-4111-8111-111111111111"
    private val time = 1_790_000_000_000L
    private val databases = mutableListOf<FarmOsDatabase>()

    private fun database(device: String) = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(), FarmOsDatabase::class.java,
    ).allowMainThreadQueries().build().also { db ->
        databases += db
        seedCommandAuthority(db, farm, "worker", device, LocalRole.WORKER)
        seedCommandAuthority(db, farm, "viewer", device, LocalRole.VIEWER)
    }

    private fun context() = LocalCommandContext(farm, "worker", "A", UUID.randomUUID().toString(), time)
    private fun command(id: String = "alert") = RecordReorderAlert(id, "item", 20_700L)

    private suspend fun stock(db: FarmOsDatabase, onHand: Long, threshold: Long) {
        db.inventory().insertItem(
            InventoryItemEntity(
                id = "item", farmId = farm, sku = "FEED", name = "Feed", unit = "kg",
                quantityMilli = onHand, updatedAtEpochMillis = time, reorderMilli = threshold,
            ),
        )
    }

    private fun changeStock(db: FarmOsDatabase, onHand: Long, threshold: Long) {
        db.openHelper.writableDatabase.execSQL(
            "UPDATE inventory_items SET quantityMilli = ?, reorderMilli = ? WHERE farmId = ? AND id = ?",
            arrayOf(onHand, threshold, farm, "item"),
        )
    }

    private fun snapshot(db: FarmOsDatabase, id: String = "alert"): Pair<Long, Long>? =
        db.openHelper.readableDatabase.query(
            "SELECT onHandMilli, reorderMilli FROM inventory_reorder_alerts WHERE farmId = ? AND id = ?",
            arrayOf(farm, id),
        ).use { if (it.moveToFirst()) it.getLong(0) to it.getLong(1) else null }

    private fun target(db: FarmOsDatabase, device: String) =
        RoomReplicaEndpoint(db, farm, device, replicationAppliers).apply { registerPairedDevice("A", "Origin") }

    @After
    fun close() = databases.forEach { it.close() }

    @Test
    fun exactRetryKeepsTheSealedSnapshotAfterReplenishmentAndNewAlertsStillNeedLowStock(): Unit = runBlocking {
        val db = database("A")
        stock(db, 5_000L, 10_000L)
        val ops = RoomOpsRepository(db, farm)
        val admitted = context()
        ops.recordReorderAlert(command(), admitted)
        val original = requireNotNull(db.replication().operation(farm, admitted.mutationId))
        assertEquals("inventory.record_reorder.v2", original.operationType)
        assertEquals(2, original.schemaVersion)
        val payload = Json.parseToJsonElement(original.payloadJson).jsonObject
        assertEquals(5_000L, payload.getValue("onHandMilli").jsonPrimitive.long)
        assertEquals(10_000L, payload.getValue("reorderMilli").jsonPrimitive.long)

        changeStock(db, 20_000L, 2_000L)
        ops.recordReorderAlert(command(), admitted)
        assertEquals(5_000L to 10_000L, snapshot(db))
        assertEquals(original, db.replication().operation(farm, admitted.mutationId))
        assertTrue(runCatching {
            ops.recordReorderAlert(command().copy(occurredEpochDay = 20_701L), admitted)
        }.isFailure)
        assertTrue(runCatching { ops.recordReorderAlert(command("above"), context()) }.isFailure)
        changeStock(db, 0L, 0L)
        assertTrue(runCatching { ops.recordReorderAlert(command("no-threshold"), context()) }.isFailure)
        assertNull(snapshot(db, "above"))
        assertNull(snapshot(db, "no-threshold"))
        assertEquals(1L, db.replication().count(farm))
        assertEquals(1L, db.outbox().countUnacknowledgedForFarm(farm))
        assertEquals(1L, db.replication().device(farm, "A")?.lastReportedOwnSequence)
    }

    @Test
    fun deliveryUsesOnlySealedMeasurementsWithDifferentOrMissingCurrentStock(): Unit = runBlocking {
        val origin = database("A")
        stock(origin, 5_000L, 10_000L)
        val admitted = context()
        RoomOpsRepository(origin, farm).recordReorderAlert(command(), admitted)
        val operation = requireNotNull(origin.replication().operation(farm, admitted.mutationId)).toEnvelope()
        val bundle = OperationBundle.seal(farm, "A", listOf(operation))
        val receiver = database("B")
        stock(receiver, 50_000L, 1_000L)
        val actor = requireNotNull(receiver.localAccess().account(farm, "worker"))
        receiver.localAccess().upsertAccount(actor.copy(status = "DISABLED"))
        val endpoint = target(receiver, "B")
        assertNull(endpoint.ingest(bundle).rejectedReason)
        assertEquals(ApplicationState.APPLIED.name, receiver.replicationApplications().get(farm, admitted.mutationId)?.state)
        assertEquals(5_000L to 10_000L, snapshot(receiver))
        assertEquals(50_000L, receiver.inventory().item(farm, "item")?.quantityMilli)
        assertEquals(1_000L, receiver.inventory().item(farm, "item")?.reorderMilli)
        assertNull(endpoint.ingest(bundle).rejectedReason)
        assertEquals(1L, receiver.replication().count(farm))
        assertEquals(0L, receiver.outbox().countUnacknowledgedForFarm(farm))

        val beforeItemDelivery = database("C")
        assertNull(target(beforeItemDelivery, "C").ingest(bundle).rejectedReason)
        assertNull(beforeItemDelivery.inventory().item(farm, "item"))
        assertEquals(5_000L to 10_000L, snapshot(beforeItemDelivery))
    }

    @Test
    fun appliedLegacyRowsStayUnchangedButAnUnappliedV1NeverInventsItsMissingSnapshot(): Unit = runBlocking {
        val origin = database("A")
        stock(origin, 50_000L, 1_000L)
        val admitted = context()
        origin.withTransaction {
            origin.lifecycle().insertReorderAlert(ReorderAlertEntity("alert", farm, "item", 5_000L, 10_000L, 20_700L))
            origin.journalLocalOperation(
                operationId = admitted.mutationId, farmId = farm, entityType = "inventory_item", entityId = "item",
                actorId = admitted.actorId, deviceId = "A", businessTimeEpochMillis = time, createdAtEpochMillis = time,
                baseVersion = 0L, operationType = "inventory.record_reorder.v1",
                payloadJson = Json.encodeToString(command()), schemaVersion = 1,
            )
        }
        val original = requireNotNull(origin.replication().operation(farm, admitted.mutationId))
        RoomOpsRepository(origin, farm).recordReorderAlert(command(), admitted)
        assertEquals(5_000L to 10_000L, snapshot(origin))
        assertEquals(original, origin.replication().operation(farm, admitted.mutationId))
        assertEquals(1L, origin.replication().count(farm))
        assertTrue(runCatching {
            RoomOpsRepository(origin, farm).recordReorderAlert(command().copy(occurredEpochDay = 20_701L), admitted)
        }.isFailure)

        val receiver = database("B")
        stock(receiver, 9_000L, 12_000L)
        val bundle = OperationBundle.seal(farm, "A", listOf(original.toEnvelope()))
        target(receiver, "B").ingest(bundle)
        val application = requireNotNull(receiver.replicationApplications().get(farm, original.operationId))
        assertEquals(ApplicationState.FAILED.name, application.state)
        assertTrue(application.reason.orEmpty().contains("immutable stock snapshot"))
        assertNull(snapshot(receiver))
        assertEquals(original.toEnvelope(), receiver.replication().operation(farm, original.operationId)?.toEnvelope())
        assertEquals(0L, receiver.outbox().countUnacknowledgedForFarm(farm))
    }

    @Test
    fun aReceivedV2CannotClaimLowStockWithInconsistentSealedNumbers(): Unit = runBlocking {
        val origin = database("A")
        stock(origin, 5_000L, 10_000L)
        val admitted = context()
        RoomOpsRepository(origin, farm).recordReorderAlert(command(), admitted)
        val original = requireNotNull(origin.replication().operation(farm, admitted.mutationId)).toEnvelope()
        val fields = Json.parseToJsonElement(original.payload.getValue(COMMAND_PAYLOAD_KEY)).jsonObject
        val falseSnapshot = JsonObject(fields + ("onHandMilli" to JsonPrimitive(20_000L)))
        val altered = OperationEnvelope.seal(
            operationId = original.operationId, farmId = original.farmId, entityType = original.entityType,
            entityId = original.entityId, actorId = original.actorId, deviceId = original.deviceId,
            deviceSequence = original.deviceSequence, businessTimeEpochMillis = original.businessTimeEpochMillis,
            createdAtEpochMillis = original.createdAtEpochMillis, baseVersion = original.baseVersion,
            operationType = original.operationType, mergeClass = original.mergeClass,
            payload = mapOf(COMMAND_PAYLOAD_KEY to falseSnapshot.toString()), schemaVersion = original.schemaVersion,
            provenance = "inconsistent-alert-snapshot-fixture",
        )
        val receiver = database("B")
        stock(receiver, 1_000L, 10_000L)
        target(receiver, "B").ingest(OperationBundle.seal(farm, "A", listOf(altered)))
        assertNull(snapshot(receiver))
        val application = requireNotNull(receiver.replicationApplications().get(farm, altered.operationId))
        assertEquals(ApplicationState.FAILED.name, application.state)
        assertTrue(application.reason.orEmpty().contains("at or below"))
        assertEquals(altered, receiver.replication().operation(farm, altered.operationId)?.toEnvelope())
    }

    @Test
    fun deniedAuthorityAndJournalFailureLeaveNoAlertSnapshotOrConsumedSequence(): Unit = runBlocking {
        val db = database("A")
        stock(db, 5_000L, 10_000L)
        val ops = RoomOpsRepository(db, farm)
        val admitted = context()
        for (actor in listOf("viewer", "missing")) {
            assertTrue(runCatching {
                ops.recordReorderAlert(command(), admitted.copy(actorId = actor))
            }.exceptionOrNull() is AccessDenied)
        }
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_alert_journal BEFORE INSERT ON replication_operations " +
                "WHEN NEW.operationType = 'inventory.record_reorder.v2' BEGIN SELECT RAISE(ABORT, 'journal fixture'); END",
        )
        val stockBeforeFailure = requireNotNull(db.inventory().item(farm, "item"))
        val journalFailure = runCatching { ops.recordReorderAlert(command(), admitted) }.exceptionOrNull()
        assertTrue(
            "Expected the injected SQLite journal failure, got $journalFailure",
            generateSequence(journalFailure) { it.cause }.any {
                it is SQLiteException && it.message.orEmpty().contains("journal fixture")
            },
        )
        assertNull(snapshot(db))
        assertEquals(stockBeforeFailure, db.inventory().item(farm, "item"))
        assertEquals(0L, db.replication().count(farm))
        assertEquals(0L, db.outbox().countUnacknowledgedForFarm(farm))
        assertEquals(0L, db.replication().device(farm, "A")?.lastReportedOwnSequence)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_alert_journal")
        changeStock(db, 3_000L, 8_000L)
        ops.recordReorderAlert(command(), admitted)
        ops.recordReorderAlert(command(), admitted)
        assertEquals(3_000L to 8_000L, snapshot(db))
        assertEquals(1L, db.replication().operation(farm, admitted.mutationId)?.deviceSequence)
        assertEquals(1L, db.replication().count(farm))
    }
}
