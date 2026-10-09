package com.farmos.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.AnimalEntity
import com.farmos.core.database.AnimalGroupEntity
import com.farmos.core.database.ApplicationState
import com.farmos.core.database.COMMAND_PAYLOAD_KEY
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.FarmWorkerEntity
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.AnimalExitCommands
import com.farmos.data.herd.AttachmentCommands
import com.farmos.data.herd.BreedingDueCommands
import com.farmos.data.herd.LabourCommands
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.data.herd.StockCountCommands
import com.farmos.data.herd.TaskSeriesCommands
import com.farmos.data.herd.WorkerRegisterCommands
import com.farmos.domain.access.AccessDenied
import com.farmos.domain.access.AccountStatus
import com.farmos.domain.access.LocalRole
import com.farmos.domain.ops.AttachFile
import com.farmos.domain.ops.CompleteTaskOccurrence
import com.farmos.domain.ops.CreateFarmWorker
import com.farmos.domain.ops.CreateInventoryItem
import com.farmos.domain.ops.CreateTaskSeries
import com.farmos.domain.ops.EditTaskSeries
import com.farmos.domain.ops.MoveInventory
import com.farmos.domain.ops.PostStockCount
import com.farmos.domain.ops.RecordAnimalExit
import com.farmos.domain.ops.RecordCattleServiceV2
import com.farmos.domain.ops.RecordStockCountLine
import com.farmos.domain.ops.RecordWorkerLabour
import com.farmos.domain.ops.RejectStockCount
import com.farmos.domain.ops.ReverseAnimalExit
import com.farmos.domain.ops.StartStockCount
import com.farmos.domain.ops.StockCountAdjustment
import com.farmos.domain.ops.SubmitStockCount
import com.farmos.domain.ops.TaskRecurrenceSchedule
import com.farmos.domain.replication.MergeClass
import com.farmos.domain.replication.OperationBundle
import com.farmos.domain.replication.OperationEnvelope
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Real Room admission/rollback coverage for every standalone command family. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-rUS")
class StandaloneCommandAuthorityTest {
    private val farm = "70707070-7070-4070-8070-707070707070"
    private val otherFarm = "71717171-7171-4171-8171-717171717171"
    private val day = 20_300L
    private val time = day * 86_400_000L
    private val databases = mutableListOf<FarmOsDatabase>()
    private val json = Json { encodeDefaults = true }

    private fun database(role: LocalRole = LocalRole.WORKER): FarmOsDatabase =
        Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FarmOsDatabase::class.java)
            .allowMainThreadQueries().build().also {
                databases += it
                seedCommandAuthority(it, farm, "worker", "A", role)
            }

    private fun context(actor: String = "worker") =
        LocalCommandContext(farm, actor, "A", UUID.randomUUID().toString(), time)

    private suspend fun fixtures(db: FarmOsDatabase) {
        db.animals().insert(AnimalEntity("goat", farm, "G-1", null, "goat", "FEMALE", "active", null, updatedAtEpochMillis = time))
        db.animals().insert(AnimalEntity("cow", farm, "C-1", null, "cattle", "FEMALE", "active", null, updatedAtEpochMillis = time))
        db.groups().insert(AnimalGroupEntity("mob", farm, "sheep", "Ewes", 5))
        db.workers().upsert(FarmWorkerEntity("registered-worker", farm, "Farm worker", true, time, "manager"))
    }

    private fun series() = CreateTaskSeries("series", "ops", "CHECK", "Check troughs", "DAILY", startEpochDay = day)
    private fun service() = RecordCattleServiceV2("service", "cow", "natural", day, day + 280, "pd", "pen", "calving")
    private fun labour() = RecordWorkerLabour("labour", "registered-worker", "Farm worker", "FEED", 60, day)
    private fun attachment() = AttachFile("attachment", "animal", "goat", "a".repeat(64), 100, "image/png", "Evidence.png")
    private fun exit() = RecordAnimalExit("exit", "goat", "DEATH", day, deathCause = "UNKNOWN")

    private data class Attempt(val name: String, val write: suspend () -> Unit)

    private fun attempts(db: FarmOsDatabase, ctx: LocalCommandContext, replaying: Boolean) = listOf(
        Attempt("stock count") { StockCountCommands(db, farm, replaying).start(StartStockCount("count"), ctx) },
        Attempt("task series") { TaskSeriesCommands(db, farm, replaying).create(series(), ctx) },
        Attempt("breeding due") { BreedingDueCommands(db, farm, replaying).recordCattleService(service(), ctx) },
        Attempt("worker register") { WorkerRegisterCommands(db, farm, replaying).create(CreateFarmWorker("new-worker", "New worker"), ctx) },
        Attempt("labour") { LabourCommands(db, farm, replaying).record(labour(), ctx) },
        Attempt("attachment") { AttachmentCommands(db, farm, replaying).attach(attachment(), ctx) },
        Attempt("animal exit") { AnimalExitCommands(db, farm, replaying).record(exit(), ctx) },
    )

    private suspend fun assertNoBusinessChanges(db: FarmOsDatabase) {
        assertNull(db.stockCounts().get(farm, "count"))
        assertNull(db.taskSeries().get(farm, "series"))
        assertTrue(db.tasks().openForFarm(farm).isEmpty())
        assertNull(db.workers().get(farm, "new-worker"))
        assertTrue(db.labour().recent(farm, 10).isEmpty())
        assertNull(db.attachments().get(farm, "attachment"))
        assertTrue(db.animalExits().forAnimal(farm, "goat").isEmpty())
        assertEquals("active", db.animals().get(farm, "goat")?.status)
        assertEquals(0L, db.replication().count(farm))
        assertEquals(0L, db.replication().device(farm, "A")?.lastReportedOwnSequence)
    }

    @After
    fun close() = databases.forEach { it.close() }

    @Test
    fun aViewerCannotUseAnyStandaloneWriter(): Unit = runBlocking {
        val db = database(LocalRole.VIEWER)
        fixtures(db)
        attempts(db, context(), replaying = false).forEach { attempt ->
            assertThrows(attempt.name, AccessDenied::class.java) { runBlocking { attempt.write() } }
            assertNoBusinessChanges(db)
        }
    }

    @Test
    fun settingReplayTrueCannotCreateAnUnjournalledChange(): Unit = runBlocking {
        val db = database()
        fixtures(db)
        attempts(db, context(), replaying = true).forEach { attempt ->
            assertThrows(attempt.name, IllegalArgumentException::class.java) { runBlocking { attempt.write() } }
            assertNoBusinessChanges(db)
        }
    }

    @Test
    fun workersRecordWorkButCannotManageWorkersOrPlanIt(): Unit = runBlocking {
        val db = database()
        fixtures(db)
        assertThrows(AccessDenied::class.java) {
            runBlocking { WorkerRegisterCommands(db, farm).create(CreateFarmWorker("new-worker", "New worker"), context()) }
        }
        assertThrows(AccessDenied::class.java) { runBlocking { TaskSeriesCommands(db, farm).create(series(), context()) } }
        assertThrows(AccessDenied::class.java) { runBlocking { BreedingDueCommands(db, farm).recordCattleService(service(), context()) } }
        seedCommandAuthority(db, farm, "supervisor", "A", LocalRole.SUPERVISOR)
        WorkerRegisterCommands(db, farm).create(CreateFarmWorker("new-worker", "New worker"), context("supervisor"))
        TaskSeriesCommands(db, farm).create(series(), context("supervisor"))
        TaskSeriesCommands(db, farm).complete(CompleteTaskOccurrence("series", day), context())
        LabourCommands(db, farm).record(labour(), context())
        AttachmentCommands(db, farm).attach(attachment(), context())
        AnimalExitCommands(db, farm).record(exit(), context())
        assertEquals("done", db.tasks().get(farm, TaskRecurrenceSchedule.occurrenceId("series", day))?.status)
        assertEquals("dead", db.animals().get(farm, "goat")?.status)
        assertEquals(6L, db.replication().count(farm))
    }

    @Test
    fun disabledMissingForeignOrRevokedAuthorityCannotWriteOrConsumeSequence(): Unit = runBlocking {
        val db = database()
        fixtures(db)
        val account = requireNotNull(db.localAccess().account(farm, "worker"))
        db.localAccess().upsertAccount(account.copy(status = AccountStatus.DISABLED.name))
        assertThrows(AccessDenied::class.java) { runBlocking { StockCountCommands(db, farm).start(StartStockCount("count"), context()) } }
        db.localAccess().upsertAccount(account)
        assertThrows(AccessDenied::class.java) { runBlocking { StockCountCommands(db, farm).start(StartStockCount("count"), context("missing")) } }
        seedCommandAuthority(db, otherFarm, "foreign", "foreign-device", LocalRole.OWNER)
        assertThrows(AccessDenied::class.java) { runBlocking { StockCountCommands(db, farm).start(StartStockCount("count"), context("foreign")) } }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { StockCountCommands(db, farm).start(StartStockCount("count"), context().copy(farmId = otherFarm)) }
        }
        val device = requireNotNull(db.replication().device(farm, "A"))
        for (bad in listOf(device.copy(isLocal = false), device.copy(status = "REVOKED"), device.copy(revokedAfterSequence = 0L))) {
            db.replication().upsertDevice(bad)
            assertThrows(AccessDenied::class.java) { runBlocking { StockCountCommands(db, farm).start(StartStockCount("count"), context()) } }
        }
        db.replication().upsertDevice(device)
        assertNoBusinessChanges(db)
        assertEquals(0L, db.replication().count(otherFarm))
    }

    private suspend fun countedStock(db: FarmOsDatabase): StockCountCommands {
        seedCommandAuthority(db, farm, "manager", "A", LocalRole.MANAGER)
        val ops = RoomOpsRepository(db, farm)
        ops.createItem(CreateInventoryItem("mash", "MASH", "Layer mash"), context("manager"))
        ops.move(MoveInventory("receive", "mash", "receive", 5_000, time), context())
        val counts = StockCountCommands(db, farm)
        counts.start(StartStockCount("count"), context())
        counts.recordLine(RecordStockCountLine("count", "mash", 6_000, 5_000), context())
        counts.submit(SubmitStockCount("count"), context())
        return counts
    }

    @Test
    fun workersCannotPostOrRejectAndAnAcceptedPostRetriesOnce(): Unit = runBlocking {
        val db = database()
        val counts = countedStock(db)
        val post = PostStockCount("count", listOf(StockCountAdjustment("mash", 1_000)))
        val before = db.replication().count(farm)
        assertThrows(AccessDenied::class.java) { runBlocking { counts.post(post, context()) } }
        assertThrows(AccessDenied::class.java) { runBlocking { counts.reject(RejectStockCount("count", "Review"), context()) } }
        assertEquals("SUBMITTED", db.stockCounts().get(farm, "count")?.status)
        assertEquals(5_000L, db.inventory().item(farm, "mash")?.quantityMilli)
        assertEquals(before, db.replication().count(farm))
        val manager = context("manager")
        counts.post(post, manager)
        counts.post(post, manager)
        assertEquals(6_000L, db.inventory().item(farm, "mash")?.quantityMilli)
        assertEquals("POSTED", db.stockCounts().get(farm, "count")?.status)
        assertEquals(before + 1, db.replication().count(farm))
    }

    @Test
    fun aLaterStockFailureRollsBackEarlierAdjustmentsAndTheJournal(): Unit = runBlocking {
        val db = database()
        val counts = countedStock(db)
        val before = db.replication().count(farm)
        val sequence = db.replication().device(farm, "A")?.lastReportedOwnSequence
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                counts.post(PostStockCount("count", listOf(StockCountAdjustment("mash", 1_000), StockCountAdjustment("missing", 100))), context("manager"))
            }
        }
        assertEquals(5_000L, db.inventory().item(farm, "mash")?.quantityMilli)
        assertEquals("SUBMITTED", db.stockCounts().get(farm, "count")?.status)
        assertEquals(before, db.replication().count(farm))
        assertEquals(sequence, db.replication().device(farm, "A")?.lastReportedOwnSequence)
    }

    @Test
    fun acceptedTaskEditRetriesAfterCompletionWithoutChangingHistory(): Unit = runBlocking {
        val db = database()
        seedCommandAuthority(db, farm, "supervisor", "A", LocalRole.SUPERVISOR)
        val commands = TaskSeriesCommands(db, farm)
        commands.create(series(), context("supervisor"))
        val edit = EditTaskSeries("series", day, "THIS", title = "Check the north trough")
        val original = context("supervisor")
        commands.edit(edit, original)
        commands.complete(CompleteTaskOccurrence("series", day), context())
        val before = db.replication().count(farm)
        commands.edit(edit, original)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { commands.edit(edit.copy(title = "Rewritten"), original) } }
        val row = db.tasks().get(farm, TaskRecurrenceSchedule.occurrenceId("series", day))
        assertEquals("done", row?.status)
        assertEquals("Check the north trough", row?.title)
        assertEquals(before, db.replication().count(farm))
    }

    @Test
    fun acceptedBreedingCaptureRetriesAfterAnExitWithoutRegeneratingWork(): Unit = runBlocking {
        val db = database()
        fixtures(db)
        seedCommandAuthority(db, farm, "manager", "A", LocalRole.MANAGER)
        val original = context("manager")
        val commands = BreedingDueCommands(db, farm)
        commands.recordCattleService(service(), original)
        AnimalExitCommands(db, farm).record(RecordAnimalExit("cow-exit", "cow", "DEATH", day, deathCause = "UNKNOWN"), context())
        val before = db.replication().count(farm)
        val tasks = db.tasks().openForFarm(farm)
        commands.recordCattleService(service(), original)
        assertEquals(tasks, db.tasks().openForFarm(farm))
        assertEquals(3, tasks.size)
        assertEquals("dead", db.animals().get(farm, "cow")?.status)
        assertEquals(before, db.replication().count(farm))
        assertEquals(2, db.replication().operation(farm, original.mutationId)?.schemaVersion)
    }

    /** Admit a checksum-valid operation from a paired peer while its implementation is unavailable. */
    private suspend fun admit(
        db: FarmOsDatabase, type: String, entityType: String, entityId: String, payload: String,
    ): LocalCommandContext {
        val original = LocalCommandContext(farm, "origin-actor", "origin", UUID.randomUUID().toString(), time)
        val receiver = RoomReplicaEndpoint(db, farm, "A", emptyMap()).apply { registerPairedDevice("origin", "Origin") }
        val envelope = OperationEnvelope.seal(
            operationId = original.mutationId, farmId = farm, entityType = entityType, entityId = entityId,
            actorId = original.actorId, deviceId = original.deviceId, deviceSequence = 1,
            businessTimeEpochMillis = time, createdAtEpochMillis = time, baseVersion = null,
            operationType = type, mergeClass = MergeClass.APPEND_ONLY_EVENT,
            payload = mapOf(COMMAND_PAYLOAD_KEY to payload), schemaVersion = 1, provenance = "standalone-authority-test",
        )
        assertNull(receiver.ingest(OperationBundle.seal(farm, "origin", listOf(envelope))).rejectedReason)
        assertEquals(ApplicationState.AWAITING_APPLIER.name, db.replicationApplications().get(farm, original.mutationId)?.state)
        return original
    }

    @Test
    fun anAdmittedHistoricalReplayKeepsDefaultsAndDoesNotUseLaterRoles(): Unit = runBlocking {
        val db = database(LocalRole.VIEWER)
        fixtures(db)
        val command = labour()
        val original = admit(db, LabourCommands.RECORD, "labour_entry", command.entryId, JSONObject(json.encodeToString(command)).apply { remove("note") }.toString())
        seedCommandAuthority(db, farm, original.actorId, original.deviceId, LocalRole.VIEWER)
        val account = requireNotNull(db.localAccess().account(farm, original.actorId))
        db.localAccess().upsertAccount(account.copy(status = AccountStatus.DISABLED.name))
        val device = requireNotNull(db.replication().device(farm, original.deviceId))
        db.replication().upsertDevice(device.copy(status = "REVOKED", revokedAfterSequence = 1, isLocal = false))
        val worker = requireNotNull(db.workers().get(farm, "registered-worker"))
        db.workers().upsert(worker.copy(active = false))
        val replay = LabourCommands(db, farm, replaying = true)
        replay.record(command, original)
        assertEquals(60, db.labour().recent(farm, 10).single().minutes)
        assertEquals(1L, db.replication().count(farm))
        assertThrows(IllegalArgumentException::class.java) { runBlocking { replay.record(command.copy(minutes = 61), original) } }
        for (forged in listOf(original.copy(actorId = "other"), original.copy(deviceId = "other"), original.copy(occurredAtEpochMillis = time + 1))) {
            assertThrows(IllegalArgumentException::class.java) { runBlocking { replay.record(command, forged) } }
        }
        val application = requireNotNull(db.replicationApplications().get(farm, original.mutationId))
        db.replicationApplications().upsert(application.copy(state = ApplicationState.APPLIED.name))
        replay.record(command, original)
        assertEquals(1, db.labour().recent(farm, 10).size)
        assertEquals(1L, db.replication().count(farm))
    }

    @Test
    fun aSetAsideAttachmentCannotBeReplayedAndItsJournalIsKept(): Unit = runBlocking {
        val db = database()
        fixtures(db)
        val command = attachment()
        val original = admit(db, AttachmentCommands.ATTACH, "attachment", command.attachmentId, json.encodeToString(command))
        val application = requireNotNull(db.replicationApplications().get(farm, original.mutationId))
        db.replicationApplications().upsert(application.copy(state = ApplicationState.SET_ASIDE.name))
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { AttachmentCommands(db, farm, replaying = true).attach(command, original) }
        }
        assertNull(db.attachments().get(farm, command.attachmentId))
        assertNotNull(db.replication().operation(farm, original.mutationId))
    }

    @Test
    fun anUnappliedExitCorrectionRequiresManagementAndTheActualWaitingExit(): Unit = runBlocking {
        val db = database()
        fixtures(db)
        seedCommandAuthority(db, farm, "manager", "A", LocalRole.MANAGER)
        val correction = ReverseAnimalExit("correction", "goat", "exit", "Wrong observed exit", day)
        val commands = AnimalExitCommands(db, farm)
        assertThrows(AccessDenied::class.java) { runBlocking { commands.reverse(correction, context(), ofUnappliedExit = true) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { commands.reverse(correction, context("manager"), ofUnappliedExit = true) } }
        val original = admit(db, AnimalExitCommands.RECORD, "animal", "goat", json.encodeToString(exit()))
        commands.reverse(correction, context("manager"), ofUnappliedExit = true)
        assertEquals("active", db.animals().get(farm, "goat")?.status)
        assertEquals("REVERSAL", db.animalExits().forAnimal(farm, "goat").single().kind)
        assertNotNull(db.replication().operation(farm, original.mutationId))
        assertEquals(2L, db.replication().count(farm))
    }
}
