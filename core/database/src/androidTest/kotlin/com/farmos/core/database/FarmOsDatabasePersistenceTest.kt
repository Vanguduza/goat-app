package com.farmos.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.model.SyncState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FarmOsDatabasePersistenceTest {
    private lateinit var context: Context
    private val databaseName = "farm-os-persistence-test.db"

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
    fun goatWeightAndPendingOutboxSurviveDatabaseCloseAndReopen() = runBlocking {
        val farmId = "11111111-1111-4111-8111-111111111111"
        val animalId = "33333333-3333-4333-8333-333333333333"
        val database = openDatabase()

        database.animals().insert(
            AnimalEntity(
                id = animalId,
                farmId = farmId,
                tag = "A-RESTART-01",
                name = "Nala",
                speciesCode = "goat",
                sex = "FEMALE",
                status = "active",
                dateOfBirthEpochDay = 19_723,
                updatedAtEpochMillis = 1_700_000_000_000L,
            ),
        )
        database.measurements().insert(
            MeasurementEntity(
                id = "66666666-6666-4666-8666-666666666666",
                farmId = farmId,
                animalId = animalId,
                type = "weight",
                valueLong = 32_450L,
                unit = "g",
                measuredAtEpochMillis = 1_700_000_001_000L,
            ),
        )
        database.outbox().insert(
            OutboxEntity(
                mutationId = "55555555-5555-4555-8555-555555555555",
                farmId = farmId,
                actorId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                deviceId = "device-a",
                commandName = "goat.register.v1",
                commandSchemaVersion = 1,
                aggregateType = "animal",
                aggregateId = animalId,
                aggregateOrdinal = 1,
                expectedStreamVersion = 0,
                payloadJson = """{"animalId":"$animalId","tag":"A-RESTART-01","name":"Nala","sex":"FEMALE","dateOfBirthEpochDay":19723}""",
                occurredAtEpochMillis = 1_700_000_000_000L,
                createdAtEpochMillis = 1_700_000_000_000L,
                state = SyncState.PENDING.name,
                attemptCount = 0,
                nextAttemptAtEpochMillis = null,
                lastErrorCode = null,
                serverEventId = null,
                serverStreamVersion = null,
            ),
        )
        database.outbox().insert(
            OutboxEntity(
                mutationId = "77777777-7777-4777-8777-777777777777",
                farmId = farmId,
                actorId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                deviceId = "device-a",
                commandName = "goat.record_weight.v1",
                commandSchemaVersion = 1,
                aggregateType = "animal",
                aggregateId = animalId,
                aggregateOrdinal = 2,
                expectedStreamVersion = 1,
                payloadJson = """{"animalId":"$animalId","measurementId":"66666666-6666-4666-8666-666666666666","weightGrams":32450,"measuredAtEpochMillis":1700000001000}""",
                occurredAtEpochMillis = 1_700_000_001_000L,
                createdAtEpochMillis = 1_700_000_001_000L,
                state = SyncState.PENDING.name,
                attemptCount = 0,
                nextAttemptAtEpochMillis = null,
                lastErrorCode = null,
                serverEventId = null,
                serverStreamVersion = null,
            ),
        )

        database.close()

        val reopened = openDatabase()
        val animal = reopened.animals().get(farmId, animalId)
        val weight = reopened.measurements().latest(farmId, animalId, "weight")
        val unacknowledged = reopened.outbox().countUnacknowledgedForAggregate(
            farmId = farmId,
            aggregateType = "animal",
            aggregateId = animalId,
        )
        val dispatchable = reopened.outbox().pending(now = 1_700_000_010_000L, limit = 10)

        assertNotNull(animal)
        assertEquals("A-RESTART-01", animal?.tag)
        assertEquals(32_450L, weight?.valueLong)
        assertEquals(2L, unacknowledged)
        assertEquals(
            listOf("55555555-5555-4555-8555-555555555555"),
            dispatchable.map { it.mutationId },
        )
        assertTrue(context.getDatabasePath(databaseName).exists())

        reopened.close()
    }

    private fun openDatabase(): FarmOsDatabase = Room.databaseBuilder(
        context,
        FarmOsDatabase::class.java,
        databaseName,
    )
        .addMigrations(FarmOsDatabase.MIGRATION_1_2)
        .build()
}
