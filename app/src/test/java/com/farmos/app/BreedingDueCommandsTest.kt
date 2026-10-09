package com.farmos.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.AnimalEntity
import com.farmos.core.database.AnimalGroupEntity
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.model.LocalCommandContext
import com.farmos.domain.access.LocalRole
import com.farmos.data.herd.BreedingDueCommands
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.ops.GestationPeriod
import com.farmos.domain.ops.GestationSpecies
import com.farmos.domain.ops.RecordCattleService
import com.farmos.domain.ops.RecordCattleServiceV2
import com.farmos.domain.ops.RecordSheepJoiningV2
import com.farmos.domain.replication.LocalPeerTransport
import com.farmos.domain.replication.SyncSession
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Owner decision D-019: generated calving and lambing work follows the farm's own gestation period. The
 * recording device carries the expected day in the v2 command, so every device replays the same dates.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-rUS")
class BreedingDueCommandsTest {
    private val farm = "77777777-7777-4777-8777-777777777777"
    private val databases = mutableListOf<FarmOsDatabase>()

    private fun database(device: String = "A") = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FarmOsDatabase::class.java)
        .allowMainThreadQueries()
        .build()
        .also { db ->
            databases += db
            runBlocking {
                seedCommandAuthority(db, farm, "manager-$device", device, LocalRole.MANAGER)
                seedCommandAuthority(db, farm, "owner-1", device, LocalRole.OWNER)
            }
        }

    @After
    fun tearDown() = databases.forEach { it.close() }

    private fun id() = UUID.randomUUID().toString()

    private fun context(device: String) = LocalCommandContext(farm, "manager-$device", device, id(), 1_790_000_000_000)

    private fun endpoint(db: FarmOsDatabase, device: String, peer: String) =
        RoomReplicaEndpoint(db, farm, device, replicationAppliers).apply { registerPairedDevice(peer, peer) }

    private suspend fun FarmOsDatabase.fixtures() {
        animals().insert(AnimalEntity(id = "cow-1", farmId = farm, tag = "C-1", name = null, speciesCode = "cattle", sex = "FEMALE", status = "active", dateOfBirthEpochDay = null, updatedAtEpochMillis = 1L))
        groups().insert(AnimalGroupEntity("mob-1", farm, "sheep", "Ewes", 50))
    }

    private suspend fun FarmOsDatabase.dueDays(): Map<String, Long> = tasks().openForFarm(farm).associate { it.taskCode to it.dueOnEpochDay }

    @Test
    fun theCarriedDayDrivesTheWorkAndReplaysUnchangedOnAnotherDevice(): Unit = runBlocking {
        val aDb = database().apply { fixtures() }
        val bDb = database("B").apply { fixtures() }
        aDb.setFarmGestation(farm, GestationSpecies.CATTLE, GestationPeriod(278, 285, 292), "owner-1", "A")
        aDb.setFarmGestation(farm, GestationSpecies.SHEEP, GestationPeriod(145, 150, 155), "owner-1", "A")
        // Device B holds a different cattle period of its own before it hears from A.
        bDb.setFarmGestation(farm, GestationSpecies.CATTLE, GestationPeriod(265, 270, 275), "owner-1", "B")

        val served = 20_000L
        val calving = served + aDb.gestationPeriod(farm, GestationSpecies.CATTLE).typicalDays
        BreedingDueCommands(aDb, farm).recordCattleService(RecordCattleServiceV2(id(), "cow-1", "ai", served, calving, "pd", "calving-pen", "calving"), context("A"))
        val lambing = served + aDb.gestationPeriod(farm, GestationSpecies.SHEEP).typicalDays
        BreedingDueCommands(aDb, farm).recordJoining(RecordSheepJoiningV2(id(), "mob-1", served, lambing, "scan", "pre-lamb", "lamb-pen", "lambing"), context("A"))

        val expected = mapOf(
            "PD" to 20_032L,
            "CALVING_PADDOCK" to 20_264L,
            "EXPECTED_CALVING" to 20_285L,
            "SCAN" to 20_070L,
            "PRE_LAMB" to 20_143L,
            "LAMBING_PADDOCK" to 20_143L,
            "EXPECTED_LAMBING" to 20_150L,
        )
        assertEquals(expected, aDb.dueDays())
        // Journalled for farm replication only: no server-era outbox row.
        assertEquals(0L, aDb.outbox().countUnacknowledgedForFarm(farm))
        assertEquals(
            listOf(BreedingDueCommands.CATTLE_SERVICE_V2, BreedingDueCommands.SHEEP_JOINING_V2),
            aDb.replication().operationsInRange(farm, "A", 1, Long.MAX_VALUE).map { it.operationType }.filter { it.endsWith(".v2") },
        )

        // Device B replays the carried days exactly, whatever its own gestation settings.
        SyncSession.run(endpoint(bDb, "B", "A"), LocalPeerTransport(endpoint(aDb, "A", "B")), remoteDeviceId = "A")
        assertEquals(expected, bDb.dueDays())
    }

    @Test
    fun anExpectedDayOutsideTheGestationRangeIsRefused() {
        val db = database()
        runBlocking { db.fixtures() }
        assertThrows(IllegalStateException::class.java) {
            runBlocking { BreedingDueCommands(db, farm).recordCattleService(RecordCattleServiceV2(id(), "cow-1", "ai", 20_000, 19_990, "p", "q", "r"), context("A")) }
        }
        assertThrows(IllegalStateException::class.java) {
            runBlocking { BreedingDueCommands(db, farm).recordJoining(RecordSheepJoiningV2(id(), "mob-1", 20_000, 20_600, "s", "t", "u", "v"), context("A")) }
        }
        assertEquals(emptyMap<String, Long>(), runBlocking { db.dueDays() })
    }

    @Test
    fun v1OperationsKeepTheirRecordedFixedDays(): Unit = runBlocking {
        val db = database().apply { fixtures() }
        RoomOpsRepository(db, farm).recordCattleService(RecordCattleService(id(), "cow-1", "natural", 20_000, "pd", "pen", "calving"), context("A"))
        assertEquals(mapOf("PD" to 20_032L, "CALVING_PADDOCK" to 20_259L, "EXPECTED_CALVING" to 20_280L), db.dueDays())
    }
}
