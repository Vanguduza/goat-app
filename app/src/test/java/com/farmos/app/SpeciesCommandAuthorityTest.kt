package com.farmos.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.AnimalEntity
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.journalLocalOperation
import com.farmos.core.database.toEnvelope
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.goat.GoatReplicationAppliers
import com.farmos.data.goat.RoomGoatRepository
import com.farmos.data.herd.HerdReplicationAppliers
import com.farmos.data.herd.RoomHerdRepository
import com.farmos.domain.access.AccessDenied
import com.farmos.domain.access.LocalRole
import com.farmos.domain.goat.GoatSex
import com.farmos.domain.goat.GoatStatus
import com.farmos.domain.goat.RecordGoatMating
import com.farmos.domain.goat.RecordGoatPregnancy
import com.farmos.domain.goat.RecordGoatWeight
import com.farmos.domain.goat.RegisterGoat
import com.farmos.domain.goat.SetGoatStatus
import com.farmos.domain.replication.OperationBundle
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Real Room admission, immutable retries, species scoping and preserved legacy receipt behavior. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-rUS")
class SpeciesCommandAuthorityTest {
    private val farm = "species-authority-farm"
    private val actor = "worker"
    private val device = "tablet"
    private lateinit var database: FarmOsDatabase
    private val replicas = mutableListOf<FarmOsDatabase>()
    private fun context(id: String) = LocalCommandContext(farm, actor, device, id, 1_790_000_000_000)
    private fun goat(id: String = "goat") = RegisterGoat(id, "G-$id", "Nala", GoatSex.FEMALE)

    @Before
    fun setUp() {
        database = newDatabase()
        seedCommandAuthority(database, farm, actor, device, LocalRole.WORKER)
    }

    @After
    fun tearDown() = replicas.forEach { it.close() }

    private fun newDatabase(): FarmOsDatabase =
        Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FarmOsDatabase::class.java)
            .allowMainThreadQueries().build().also { replicas += it }

    @Test
    fun currentViewerDisabledForeignAndRevokedAuthorityCannotRegisterAnySpecies() = runBlocking {
        val original = database.localAccess().account(farm, actor)!!
        for (account in listOf(original.copy(role = "VIEWER"), original.copy(status = "DISABLED"))) {
            database.localAccess().upsertAccount(account)
            assertTrue(failure { RoomGoatRepository(database, farm).registerGoat(goat(), context("denied-goat")) } is AccessDenied)
            for (species in HerdReplicationAppliers.SPECIES) {
                assertTrue(failure {
                    RoomHerdRepository(database, farm, species).register(
                        species, "T-$species", null, "FEMALE", if (species == "poultry") "chicken" else null, context("denied-$species"),
                    )
                } is AccessDenied)
            }
        }
        database.localAccess().upsertAccount(original)
        seedCommandAuthority(database, "foreign-farm", "foreign", "foreign-device", LocalRole.OWNER)
        assertTrue(failure {
            RoomGoatRepository(database, farm).registerGoat(goat(), context("foreign").copy(actorId = "foreign"))
        } is AccessDenied)
        val local = database.replication().device(farm, device)!!
        database.replication().upsertDevice(local.copy(status = "LOST_REVOKED", revokedAfterSequence = 0))
        assertTrue(failure { RoomGoatRepository(database, farm).registerGoat(goat(), context("revoked")) } is AccessDenied)
        assertEquals(0L, database.replication().count(farm))
        assertEquals(0L, database.outbox().countUnacknowledgedForFarm(farm))
        assertEquals(0L, database.replication().device(farm, device)!!.lastReportedOwnSequence)
        assertNull(database.animals().get(farm, "goat"))
    }

    @Test
    fun anAcceptedGoatRetryDoesNotRewriteLaterStateOrCreateAnotherOperation() = runBlocking {
        val goats = RoomGoatRepository(database, farm)
        val registration = goat()
        goats.registerGoat(registration, context("register"))
        goats.registerGoat(registration, context("register"))
        assertTrue(failure { goats.registerGoat(registration.copy(tag = "ALTERED"), context("register")) }.message.orEmpty().contains("Mutation id"))
        val weight = RecordGoatWeight("goat", "weight", 32_450, 1_790_000_000_000)
        goats.recordWeight(weight, context("weigh"))
        // A later lifecycle fact changes current state; the accepted weight remains the same receipt.
        database.animals().updateStatus(farm, "goat", "dead", 1_790_000_010_000)
        goats.recordWeight(weight, context("weigh"))
        assertEquals("dead", database.animals().get(farm, "goat")!!.status)
        assertEquals(32_450L, database.measurements().latest(farm, "goat", "weight")!!.valueLong)
        assertEquals(2L, database.replication().count(farm))
        assertEquals(2L, database.outbox().countUnacknowledgedForFarm(farm))
        assertEquals(2L, database.replication().device(farm, device)!!.lastReportedOwnSequence)
    }

    @Test
    fun goatBreedingChecksCurrentManagementAndSameFarmSpeciesInsideItsTransaction() = runBlocking {
        val goats = RoomGoatRepository(database, farm)
        goats.registerGoat(goat(), context("register"))
        val mating = RecordGoatMating("mating", "goat", null, "natural", 20_000, "check-task")
        assertTrue(failure { goats.recordMating(mating, context("worker-mating")) } is AccessDenied)
        val account = database.localAccess().account(farm, actor)!!
        database.localAccess().upsertAccount(account.copy(role = "MANAGER"))
        database.animals().insert(AnimalEntity("rabbit", farm, "R-1", null, "rabbit", "MALE", "active", null, updatedAtEpochMillis = 1))
        failure { goats.recordMating(mating.copy(sireId = "rabbit"), context("wrong-sire")) }
        failure { goats.recordPregnancy(RecordGoatPregnancy("check", "rabbit", "pregnant", 20_000), context("wrong-species")) }
        failure { goats.recordWeight(RecordGoatWeight("rabbit", "bad-weight", 3000, 1_790_000_000_000), context("wrong-weight")) }
        assertEquals(1L, database.replication().count(farm))
        assertNull(database.measurements().latest(farm, "rabbit", "weight"))
        goats.recordMating(mating, context("manager-mating"))
        goats.recordMating(mating, context("manager-mating"))
        assertEquals(2L, database.replication().count(farm))
    }

    @Test
    fun replayRequiresItsExactStoredReceiptButDoesNotBorrowTheReceiversCurrentRole() = runBlocking {
        val goats = RoomGoatRepository(database, farm)
        goats.registerGoat(goat(), context("register"))
        goats.recordWeight(RecordGoatWeight("goat", "weight", 32100, 1_790_000_000_000), context("weight"))
        val receiverDb = newDatabase()
        seedCommandAuthority(receiverDb, farm, actor, "phone", LocalRole.VIEWER)
        val receiver = RoomReplicaEndpoint(receiverDb, farm, "phone", GoatReplicationAppliers.all)
            .apply { registerPairedDevice(device, "Tablet") }
        val operations = database.replication().operationsInRange(farm, device, 1, 2).map { it.toEnvelope() }
        assertEquals(2, receiver.ingest(OperationBundle.seal(farm, device, operations)).applied)
        assertEquals(32100L, receiverDb.measurements().latest(farm, "goat", "weight")!!.valueLong)
        assertEquals(0L, receiverDb.outbox().countUnacknowledgedForFarm(farm))
        val operation = operations.last()
        failure {
            GoatReplicationAppliers.all.getValue(operation.operationType).apply(receiverDb, operation.copy(actorId = "forged"))
        }
        assertEquals(2L, receiverDb.replication().count(farm))
        val empty = newDatabase()
        failure { RoomGoatRepository(empty, farm, replaying = true).registerGoat(goat(), context("missing")) }
        failure { RoomHerdRepository(empty, farm, "sheep", replaying = true).register("sheep", "S-1", null, "FEMALE", null, context("missing-herd")) }
        assertNull(empty.animals().get(farm, "goat"))
        assertNull(empty.animals().get(farm, "sheep"))
    }

    @Test
    fun directStatusCannotCreateNewUnauditedExitsAndHistoricalStatusStillReplays() = runBlocking {
        val goats = RoomGoatRepository(database, farm)
        goats.registerGoat(goat(), context("register"))
        val change = SetGoatStatus("goat", GoatStatus.DEAD)
        assertTrue(failure { goats.setStatus(change, context("new-status")) } is AccessDenied)
        RoomHerdRepository(database, farm, "sheep").register("sheep", "S-1", null, "FEMALE", null, context("register-sheep"))
        assertTrue(failure { RoomHerdRepository(database, farm, "sheep").setStatus("sheep", "sold", context("new-sheep-status")) } is AccessDenied)
        assertEquals(2L, database.replication().count(farm))
        // Original v1 provenance is seeded as history; the current writer does not emit this command.
        database.journalLocalOperation(
            "legacy-status", farm, "animal", "goat", actor, device, 1_790_000_000_000, 1_790_000_000_000,
            1, "goat.set_status.v1", Json.encodeToString(change), 1,
        )
        val receiverDb = newDatabase()
        val receiver = RoomReplicaEndpoint(receiverDb, farm, "phone", GoatReplicationAppliers.all + HerdReplicationAppliers.all)
            .apply { registerPairedDevice(device, "Tablet") }
        val operations = database.replication().operationsInRange(farm, device, 1, 3).map { it.toEnvelope() }
        assertEquals(3, receiver.ingest(OperationBundle.seal(farm, device, operations)).applied)
        assertEquals("dead", receiverDb.animals().get(farm, "goat")!!.status)
        assertEquals("active", receiverDb.animals().get(farm, "sheep")!!.status)
    }

    private suspend fun failure(block: suspend () -> Unit): Throwable {
        try { block() } catch (failure: Throwable) { return failure }
        throw AssertionError("Expected the command to be refused")
    }
}
