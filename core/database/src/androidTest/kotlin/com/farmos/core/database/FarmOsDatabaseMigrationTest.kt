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
import org.junit.Assert.assertNull
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

    // Execute the full upgrade chain: a fresh database alone cannot prove migration compatibility.
    @Test
    fun version1DatabaseMigratesThroughVersion37WithoutLosingFoundationData() = runBlocking {
        val farmId = "11111111-1111-4111-8111-111111111111"
        val animalId = "33333333-3333-4333-8333-333333333333"
        val mutationId = "55555555-5555-4555-8555-555555555555"
        createVersion1Fixture(farmId, animalId, mutationId)

        val migrated = Room.databaseBuilder(context, FarmOsDatabase::class.java, databaseName)
            .addMigrations(*FarmOsDatabase.ALL_MIGRATIONS)
            .build()

        // Force Room to execute the complete 1 -> 37 chain and validate the final schema.
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
        // Version 14 adds the replication journal and device registry, empty for pre-journal data.
        assertEquals(0L, migrated.replication().count(farmId))
        assertEquals(0, migrated.replication().devices(farmId).size)
        // Version 15 adds local farms, accounts, owner recovery and access history.
        assertEquals(0, migrated.localAccess().farms().size)
        assertEquals(0, migrated.localAccess().accounts(farmId).size)
        // Version 16 adds farm settings; a migrated farm has no row and keeps the default currency.
        assertNull(migrated.farmSettings().get(farmId))
        // Version 17 tracks application of received operations; nothing has been received yet.
        assertEquals(0, migrated.replicationApplications().unapplied(farmId).size)
        // Version 18 adds change times to local accounts and the recovery hash.
        assertNull(migrated.localAccess().recovery(farmId))
        // Version 19 adds device public keys; no device is known yet.
        assertEquals(0, migrated.replication().devices(farmId).size)
        // Version 20 adds peer confirmations; nothing has been shared yet.
        assertNull(migrated.replicationPeerMarks().highestHeld(farmId))
        // Version 21 adds farm gestation periods; a migrated farm keeps the defaults.
        assertEquals(0, migrated.farmGestation().all(farmId).size)
        // Version 22 adds task series and task assignment columns; Room validates the migrated schema on open.
        assertEquals(0, migrated.taskSeries().active(farmId).size)
        // Version 23 adds stock counts (D-021).
        assertEquals(0, migrated.stockCounts().all(farmId).size)
        // Version 24 adds the worker register (R1).
        assertEquals(0, migrated.workers().all(farmId).size)
        // Version 25 adds animal exits (D-022); the migrated animal has none.
        assertEquals(0, migrated.animalExits().forAnimal(farmId, animalId).size)
        // Version 26 links labour entries to registered workers; the totals query joins the register.
        assertEquals(0, migrated.labour().totalsByWorkerName(farmId).size)
        // Version 27 adds the customer register and the customer on a sale.
        assertEquals(0, migrated.customers().all(farmId).size)
        // Version 28 links sales to animal sale exits; the migrated animal has none to settle.
        assertEquals(0, migrated.animalExits().unsettledSaleExits(farmId).size)
        // Version 29 adds attachment metadata (D-015); the migrated animal carries none.
        assertEquals(0, migrated.attachments().forOwner(farmId, "animal", animalId).size)
        // Version 30 adds general animal/group vaccinations (FTR-HEALTH-003); the migrated farm has none.
        assertEquals(0, migrated.vaccinations().count(farmId))
        // Version 31 adds water points and feed plans; the migrated farm has none.
        assertEquals(0, migrated.waterPoints().count(farmId))
        assertEquals(0, migrated.feedPlans().count(farmId))
        // Version 32 adds goat weanings (FOS-GOAT-042); the migrated farm has none.
        assertEquals(0, migrated.lifecycle().goatWeaningsFor(farmId, animalId).size)
        // Version 33 adds water point events (FOS-WATER-005..008); the migrated farm has none.
        assertEquals(0, migrated.waterPointEvents().countByKind(farmId, "inspection"))
        // Version 34 adds asset meter readings (FOS-ASSET-005); the migrated farm has none.
        assertEquals(0, migrated.assetMeters().forAsset(farmId, "no-such-asset").size)
        // Version 35 adds budget lines (FOS-FIN-010); the migrated farm has none.
        assertEquals(0, migrated.budgets().count(farmId))
        assertEquals(0, migrated.budgets().active(farmId).size)
        // Version 36 adds cattle heats (FOS-CATTLE-009) and unit preferences (FOS-ADMIN-010); the migrated farm has none.
        assertEquals(0, migrated.lifecycle().cattleHeatsFor(farmId, animalId).size)
        assertEquals(0, migrated.lifecycle().cattleHeatCount(farmId))
        assertEquals(0, migrated.unitPreferences().all(farmId).size)
        // Version 37 adds animal group memberships (FOS-GROUP-007); the migrated animal has no recorded group.
        assertNull(migrated.groupMemberships().get(farmId, animalId))
        assertEquals(0, migrated.groupMemberships().getMany(farmId, listOf(animalId)).size)
        // Version 37 adds rabbit weights (FOS-RABBIT-032); the migrated farm has none.
        assertNull(migrated.lifecycle().latestRabbitWeight(farmId, animalId))
        assertEquals(0, migrated.lifecycle().rabbitWeightHistory(farmId, animalId).size)
        assertEquals(0, migrated.lifecycle().rabbitWeights(farmId).size)

        migrated.close()
    }


    @Test
    fun version29LocalFarmMigratesWithoutLosingAccountsSettingsOrAttachments() = runBlocking {
        val farmId = "11111111-1111-4111-8111-111111111111"
        val animalId = "33333333-3333-4333-8333-333333333333"
        createVersion1Fixture(farmId, animalId, "55555555-5555-4555-8555-555555555555")
        val helper = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(29) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) = error("Expected a version-1 fixture")
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        var version = oldVersion
                        for (migration in FarmOsDatabase.ALL_MIGRATIONS.filter { it.endVersion <= newVersion }) {
                            assertEquals(version, migration.startVersion)
                            migration.migrate(db)
                            version = migration.endVersion
                        }
                        assertEquals(newVersion, version)
                    }
                }).build(),
        )
        try {
            val legacy = helper.writableDatabase
            legacy.execSQL("INSERT INTO local_farms(farmId,name,createdAtEpochMillis) VALUES(?,?,?)", arrayOf(farmId, "Existing local farm", 1L))
            legacy.execSQL(
                "INSERT INTO local_accounts(accountId,farmId,username,displayName,role,status,credentialKind,credentialHash,failedAttempts,createdAtEpochMillis,updatedAtEpochMillis) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                arrayOf("account-1", farmId, "owner", "Farm Owner", "OWNER", "ACTIVE", "PIN", "fixture-hash-only", 0, 1L, 1L),
            )
            legacy.execSQL(
                "INSERT INTO farm_settings(farmId,currencyCode,updatedAtEpochMillis,updatedByActorId) VALUES(?,?,?,?)",
                arrayOf(farmId, "ZAR", 1L, "account-1"),
            )
            legacy.execSQL(
                "INSERT INTO attachments(id,farmId,ownerType,ownerId,contentSha256,byteSize,mediaType,displayName,attachedAtEpochMillis,attachedByActorId) VALUES(?,?,?,?,?,?,?,?,?,?)",
                arrayOf("attachment-1", farmId, "animal", animalId, "a".repeat(64), 3L, "image/jpeg", "Existing photo", 1L, "account-1"),
            )
        } finally {
            helper.close()
        }
        val migrated = Room.databaseBuilder(context, FarmOsDatabase::class.java, databaseName)
            .addMigrations(*FarmOsDatabase.ALL_MIGRATIONS).build()
        try {
            migrated.openHelper.writableDatabase
            assertEquals("Existing local farm", migrated.localAccess().farms().single().name)
            assertEquals("owner", migrated.localAccess().accounts(farmId).single().username)
            assertEquals("ZAR", migrated.farmSettings().get(farmId)?.currencyCode)
            assertEquals("Existing photo", migrated.attachments().forOwner(farmId, "animal", animalId).single().displayName)
            assertEquals("MIG-001", migrated.animals().get(farmId, animalId)?.tag)
            assertEquals(0, migrated.lifecycle().goatWeaningsFor(farmId, animalId).size)
            assertEquals(0, migrated.lifecycle().cattleHeatsFor(farmId, animalId).size)
            assertEquals(0, migrated.lifecycle().rabbitWeights(farmId).size)
        } finally {
            migrated.close()
        }
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
