package com.farmos.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.LocalAccountEntity
import com.farmos.core.database.LocalFarmEntity
import com.farmos.core.database.ReplicationDeviceEntity
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomHerdRepository
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.access.AccessDenied
import com.farmos.domain.ops.RecordMoney
import com.farmos.domain.ops.CreateFarmTask
import com.farmos.domain.rabbit.CreateRabbitCage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FanoutOfflineDurabilityTest {
    private lateinit var context: Context
    private val databaseName = "farm-os-fanout-offline-test.db"
    private val farmId = "11111111-1111-4111-8111-111111111111"
    private val actorId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun rabbitTaskAndCageWritesRemainDurableAndQueuedAcrossDatabaseReopen() = runBlocking {
        val rabbitId = "22222222-2222-4222-8222-222222222222"
        val taskId = "33333333-3333-4333-8333-333333333333"
        val cageId = "44444444-4444-4444-8444-444444444444"

        val database = openDatabase()
        seedOwner(database)
        val rabbits = RoomHerdRepository(database, farmId, "rabbit")
        val ops = RoomOpsRepository(database, farmId)

        rabbits.register(
            animalId = rabbitId,
            tag = "R-001",
            name = "Doe One",
            sex = "FEMALE",
            poultryKindCode = null,
            context = commandContext("55555555-5555-4555-8555-555555555555", 1_700_000_000_000L),
        )
        ops.createTask(
            CreateFarmTask(
                taskId = taskId,
                moduleCode = "rabbit",
                taskCode = "CHECK_NEST",
                title = "Check nest box",
                dueEpochDay = 20_000L,
            ),
            commandContext("66666666-6666-4666-8666-666666666666", 1_700_000_001_000L),
        )
        ops.createCage(
            CreateRabbitCage(cageId = cageId, code = "CAGE-A"),
            commandContext("77777777-7777-4777-8777-777777777777", 1_700_000_002_000L),
        )

        assertEquals(3L, database.outbox().countUnacknowledgedForFarm(farmId))
        database.close()

        val reopened = openDatabase()
        assertNotNull(reopened.animals().get(farmId, rabbitId))
        assertEquals(listOf(taskId), reopened.tasks().openForFarm(farmId).map { it.id })
        assertEquals(listOf(cageId), reopened.rabbitProgramme().cages(farmId).map { it.id })
        assertEquals(3L, reopened.outbox().countUnacknowledgedForFarm(farmId))

        reopened.close()
    }

    @Test
    fun deniedLocalMoneyWriteLeavesNoBusinessOrJournalStateAfterReopen(): Unit = runBlocking {
        val database = openDatabase()
        try {
            seedOwner(database)
            val account = requireNotNull(database.localAccess().account(farmId, actorId))
            database.localAccess().upsertAccount(account.copy(role = "WORKER"))
            val ops = RoomOpsRepository(database, farmId)
            val command = RecordMoney("denied-money", "expense", "feed", 100L, "USD", 20_000L)
            assertTrue(runCatching {
                ops.recordMoney(command, commandContext("denied-mutation", 1_700_000_004_000L))
            }.exceptionOrNull() is AccessDenied)
            assertTrue(runCatching {
                ops.recordMoney(command, commandContext("unknown-actor-mutation", 1_700_000_004_000L).copy(actorId = "missing"))
            }.exceptionOrNull() is AccessDenied)
        } finally {
            database.close()
        }
        val reopened = openDatabase()
        try {
            assertTrue(reopened.money().recent(farmId, 10).isEmpty())
            assertEquals(0L, reopened.outbox().countUnacknowledgedForFarm(farmId))
            assertEquals(0L, reopened.replication().count(farmId))
            assertEquals(0L, reopened.replication().device(farmId, "fanout-device")?.lastReportedOwnSequence)
        } finally {
            reopened.close()
        }
    }

    @Test
    fun journalFailureRollsBackRoomMoneyAndOutboxAndTheSameMutationRetriesAfterRestart(): Unit = runBlocking {
        val mutation = commandContext("atomic-money-mutation", 1_700_000_005_000L)
        val command = RecordMoney("atomic-money", "expense", "feed", 100L, "USD", 20_000L)
        val database = openDatabase()
        try {
            seedOwner(database)
            database.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER reject_money_journal BEFORE INSERT ON replication_operations " +
                    "WHEN NEW.operationType = 'money.record.v1' BEGIN SELECT RAISE(ABORT, 'journal fixture'); END",
            )
            assertTrue(runCatching { RoomOpsRepository(database, farmId).recordMoney(command, mutation) }.isFailure)
        } finally {
            database.close()
        }
        val reopened = openDatabase()
        try {
            assertTrue(reopened.money().recent(farmId, 10).isEmpty())
            assertEquals(0L, reopened.outbox().countUnacknowledgedForFarm(farmId))
            assertEquals(0L, reopened.replication().count(farmId))
            assertEquals(0L, reopened.replication().device(farmId, "fanout-device")?.lastReportedOwnSequence)
            reopened.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_money_journal")
            RoomOpsRepository(reopened, farmId).recordMoney(command, mutation)
            RoomOpsRepository(reopened, farmId).recordMoney(command, mutation)
            assertEquals(1, reopened.money().recent(farmId, 10).size)
            assertEquals(1L, reopened.outbox().countUnacknowledgedForFarm(farmId))
            assertEquals(1L, reopened.replication().count(farmId))
            assertEquals(1L, reopened.replication().operation(farmId, mutation.mutationId)?.deviceSequence)
        } finally {
            reopened.close()
        }
    }

    private fun seedOwner(database: FarmOsDatabase) {
        database.localAccess().insertFarmIfAbsent(LocalFarmEntity(farmId, "Offline durability farm", 1L))
        database.localAccess().upsertAccount(
            LocalAccountEntity(actorId, farmId, "owner", "Owner", "OWNER", "ACTIVE", "PIN", "test-only-hash",
                0, null, null, 1L),
        )
        database.replicationBlocking().upsertDevice(
            ReplicationDeviceEntity(farmId, "fanout-device", "Farm device", "ACTIVE", 0L, null, true),
        )
    }

    private fun commandContext(mutationId: String, occurredAt: Long) = LocalCommandContext(
        mutationId = mutationId,
        farmId = farmId,
        actorId = actorId,
        deviceId = "fanout-device",
        occurredAtEpochMillis = occurredAt,
    )

    private fun openDatabase(): FarmOsDatabase = Room.databaseBuilder(
        context,
        FarmOsDatabase::class.java,
        databaseName,
    )
        .addMigrations(*FarmOsDatabase.ALL_MIGRATIONS)
        .build()
}
