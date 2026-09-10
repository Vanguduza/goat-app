package com.farmos.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomHerdRepository
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.ops.CreateFarmTask
import com.farmos.domain.rabbit.CreateRabbitCage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
