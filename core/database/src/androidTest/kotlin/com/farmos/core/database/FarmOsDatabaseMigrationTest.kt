package com.farmos.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FarmOsDatabaseMigrationTest {
    private lateinit var context: Context
    private val databaseName = "farm-os-migration-chain-test.db"

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
    fun version1DatabaseMigratesThroughVersion13WithoutLosingFoundationData() = runBlocking {
        val farmId = "11111111-1111-4111-8111-111111111111"
        val animalId = "33333333-3333-4333-8333-333333333333"
        val mutationId = "55555555-5555-4555-8555-555555555555"
        createVersion1Fixture(farmId, animalId, mutationId)

        val migrated = Room.databaseBuilder(context, FarmOsDatabase::class.java, databaseName)
            .addMigrations(*FarmOsDatabase.ALL_MIGRATIONS)
            .build()

        // Force Room to execute the complete 1 -> 13 chain and validate the final schema.
        migrated.openHelper.writableDatabase

        val animal = migrated.animals().get(farmId, animalId)
        val weight = migrated.measurements().latest(farmId, animalId, "weight")
        val authoritativeVersion = migrated.aggregateVersions().getVersion(farmId, "animal", animalId)

        assertNotNull(animal)
        assertEquals("MIG-001", animal?.tag)
        assertEquals(31_250L, weight?.valueLong)
        assertEquals(2L, authoritativeVersion)

        // Representative tables from the later migration waves must be queryable after validation.
        assertEquals(0, migrated.tasks().openForFarm(farmId).size)
        assertEquals(0, migrated.lifecycle().kits(farmId).size)
        assertEquals(0, migrated.lifecycle().purchases(farmId, 10).size)

        migrated.close()
    }

    private fun createVersion1Fixture(farmId: String, animalId: String, mutationId: String) {
        val path = context.getDatabasePath(databaseName)
        path.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(path, null)
        try {
            db.execSQL(
                """
                CREATE TABLE animals (
                    id TEXT NOT NULL PRIMARY KEY,
                    farmId TEXT NOT NULL,
                    tag TEXT NOT NULL,
                    name TEXT,
                    speciesCode TEXT NOT NULL,
                    sex TEXT NOT NULL,
                    status TEXT NOT NULL,
                    dateOfBirthEpochDay INTEGER,
                    updatedAtEpochMillis INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE UNIQUE INDEX index_animals_farmId_id ON animals(farmId, id)")
            db.execSQL("CREATE UNIQUE INDEX index_animals_farmId_speciesCode_tag ON animals(farmId, speciesCode, tag)")
            db.execSQL("CREATE INDEX index_animals_farmId_speciesCode_status ON animals(farmId, speciesCode, status)")

            db.execSQL(
                """
                CREATE TABLE measurements (
                    id TEXT NOT NULL PRIMARY KEY,
                    farmId TEXT NOT NULL,
                    animalId TEXT NOT NULL,
                    type TEXT NOT NULL,
                    valueLong INTEGER NOT NULL,
                    unit TEXT NOT NULL,
                    measuredAtEpochMillis INTEGER NOT NULL,
                    FOREIGN KEY(farmId, animalId) REFERENCES animals(farmId, id) ON UPDATE NO ACTION ON DELETE NO ACTION
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX index_measurements_farmId_animalId_measuredAtEpochMillis ON measurements(farmId, animalId, measuredAtEpochMillis)")

            db.execSQL(
                """
                CREATE TABLE sync_outbox (
                    mutationId TEXT NOT NULL PRIMARY KEY,
                    farmId TEXT NOT NULL,
                    actorId TEXT NOT NULL,
                    deviceId TEXT NOT NULL,
                    commandName TEXT NOT NULL,
                    commandSchemaVersion INTEGER NOT NULL,
                    aggregateType TEXT NOT NULL,
                    aggregateId TEXT NOT NULL,
                    expectedStreamVersion INTEGER,
                    payloadJson TEXT NOT NULL,
                    occurredAtEpochMillis INTEGER NOT NULL,
                    createdAtEpochMillis INTEGER NOT NULL,
                    state TEXT NOT NULL,
                    attemptCount INTEGER NOT NULL,
                    nextAttemptAtEpochMillis INTEGER,
                    lastErrorCode TEXT,
                    serverEventId TEXT,
                    serverStreamVersion INTEGER
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX index_sync_outbox_farmId_state_nextAttemptAtEpochMillis ON sync_outbox(farmId, state, nextAttemptAtEpochMillis)")
            db.execSQL("CREATE INDEX index_sync_outbox_farmId_aggregateId ON sync_outbox(farmId, aggregateId)")

            db.execSQL(
                """
                CREATE TABLE sync_cursors (
                    farmId TEXT NOT NULL PRIMARY KEY,
                    changeCursor INTEGER NOT NULL,
                    updatedAtEpochMillis INTEGER NOT NULL
                )
                """.trimIndent(),
            )

            db.execSQL(
                "INSERT INTO animals VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf(animalId, farmId, "MIG-001", "Migration Goat", "goat", "FEMALE", "active", 19_723L, 1_700_000_000_000L),
            )
            db.execSQL(
                "INSERT INTO measurements VALUES (?, ?, ?, ?, ?, ?, ?)",
                arrayOf("66666666-6666-4666-8666-666666666666", farmId, animalId, "weight", 31_250L, "g", 1_700_000_001_000L),
            )
            db.execSQL(
                """
                INSERT INTO sync_outbox(
                    mutationId, farmId, actorId, deviceId, commandName, commandSchemaVersion,
                    aggregateType, aggregateId, expectedStreamVersion, payloadJson,
                    occurredAtEpochMillis, createdAtEpochMillis, state, attemptCount,
                    nextAttemptAtEpochMillis, lastErrorCode, serverEventId, serverStreamVersion
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf(
                    mutationId,
                    farmId,
                    "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                    "migration-device",
                    "goat.record_weight.v1",
                    1,
                    "animal",
                    animalId,
                    1L,
                    "{}",
                    1_700_000_001_000L,
                    1_700_000_001_000L,
                    "ACKNOWLEDGED",
                    1,
                    null,
                    null,
                    "77777777-7777-4777-8777-777777777777",
                    2L,
                ),
            )
            db.execSQL(
                "INSERT INTO sync_cursors VALUES (?, ?, ?)",
                arrayOf(farmId, 42L, 1_700_000_002_000L),
            )
            db.version = 1
        } finally {
            db.close()
        }
    }
}
