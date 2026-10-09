package com.farmos.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.AnimalEntity
import com.farmos.core.database.AnimalIdentifierEntity
import com.farmos.core.database.ApplicationState
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.LocalAccountEntity
import com.farmos.core.database.ReplicationDeviceEntity
import com.farmos.core.database.toEnvelope
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.goat.GoatReplicationAppliers
import com.farmos.data.goat.RoomGoatRepository
import com.farmos.domain.access.AccessDenied
import com.farmos.domain.goat.AmendGoatIdentity
import com.farmos.domain.goat.GoatSex
import com.farmos.domain.goat.RecordGoatWeight
import com.farmos.domain.goat.RegisterGoat
import com.farmos.domain.replication.OperationBundle
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Identity corrections use the same immutable operation on both devices and fail visibly on conflict. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-rUS")
class GoatIdentityCommandsTest {
    private val farm = "11111111-1111-4111-8111-111111111111"
    private val otherFarm = "22222222-2222-4222-8222-222222222222"
    private val animalId = "goat-1"
    private val actor = "worker-1"
    private val leftDevice = "device-tablet"
    private val rightDevice = "device-phone"
    private lateinit var leftDb: FarmOsDatabase
    private lateinit var rightDb: FarmOsDatabase
    private lateinit var left: RoomReplicaEndpoint
    private lateinit var right: RoomReplicaEndpoint

    private fun database() =
        Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FarmOsDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    @Before
    fun setUp() = runBlocking {
        leftDb = database()
        rightDb = database()
        for ((db, localId) in listOf(leftDb to leftDevice, rightDb to rightDevice)) {
            db.localAccess().upsertAccount(account())
            for (id in listOf(leftDevice, rightDevice)) {
                db.replication().upsertDevice(
                    ReplicationDeviceEntity(farm, id, id, "ACTIVE", 0, null, id == localId),
                )
            }
        }
        left = RoomReplicaEndpoint(leftDb, farm, leftDevice, GoatReplicationAppliers.all)
        right = RoomReplicaEndpoint(rightDb, farm, rightDevice, GoatReplicationAppliers.all)
        RoomGoatRepository(leftDb, farm).registerGoat(
            RegisterGoat(animalId, "G-1", "Original", GoatSex.FEMALE),
            context("register", 1),
        )
    }

    @After
    fun tearDown() {
        leftDb.close()
        rightDb.close()
    }

    @Test
    fun localRetryAndRepeatedReplayKeepIdenticalIdentifierHistoryAndCurrentFields() = runBlocking {
        val goats = RoomGoatRepository(leftDb, farm)
        val first = AmendGoatIdentity(animalId, tag = "G-2", officialId = "OFFICIAL-1")
        val firstContext = context("identity-1", 2)
        goats.amendIdentity(first, firstContext)
        goats.amendIdentity(AmendGoatIdentity(animalId, name = "Updated", officialId = "OFFICIAL-2"), context("identity-2", 3))
        goats.amendIdentity(first, firstContext)
        // Historical replay does not borrow the receiver's current role for the origin's old command.
        rightDb.localAccess().upsertAccount(account().copy(status = "DISABLED"))

        val bundle = bundleFrom(leftDb, leftDevice)
        assertEquals(3, right.ingest(bundle).applied)
        assertEquals(3, right.ingest(bundle).duplicates)
        val firstOperation = leftDb.replication().operation(farm, firstContext.mutationId)!!.toEnvelope()
        GoatReplicationAppliers.all.getValue("goat.amend_identity.v1").apply(rightDb, firstOperation)

        val leftAnimal = leftDb.animals().get(farm, animalId)!!
        val rightAnimal = rightDb.animals().get(farm, animalId)!!
        assertEquals(leftAnimal, rightAnimal)
        assertEquals("G-2", rightAnimal.tag)
        assertEquals("Updated", rightAnimal.name)
        val original = leftDb.lifecycle().identifiersForAnimal(farm, animalId).sortedBy { it.id }
        val received = rightDb.lifecycle().identifiersForAnimal(farm, animalId).sortedBy { it.id }
        assertEquals(original, received)
        assertEquals(2, received.size)
        assertEquals(listOf("OFFICIAL-2"), received.filter { it.isActive }.map { it.value })
        assertEquals(3L, leftDb.replication().count(farm))
        assertEquals(3L, rightDb.replication().count(farm))
        assertEquals(0L, rightDb.outbox().countUnacknowledgedForFarm(farm))
    }

    @Test
    fun competingDeviceEditsStayVisibleForReviewAndDoNotOverwriteEachOther() = runBlocking {
        right.ingest(bundleFrom(leftDb, leftDevice))
        RoomGoatRepository(leftDb, farm).amendIdentity(AmendGoatIdentity(animalId, name = "Tablet"), context("left-edit", 2))
        RoomGoatRepository(rightDb, farm).amendIdentity(
            AmendGoatIdentity(animalId, name = "Phone"),
            context("right-edit", 3, device = rightDevice),
        )
        right.ingest(bundleFrom(leftDb, leftDevice))
        left.ingest(bundleFrom(rightDb, rightDevice))
        right.applyPendingNow()
        left.applyPendingNow()

        assertEquals("Tablet", leftDb.animals().get(farm, animalId)!!.name)
        assertEquals("Phone", rightDb.animals().get(farm, animalId)!!.name)
        for ((db, rejectedId) in listOf(leftDb to "right-edit", rightDb to "left-edit")) {
            val application = db.replicationApplications().get(farm, rejectedId)!!
            assertEquals(ApplicationState.FAILED.name, application.state)
            assertTrue(application.reason.orEmpty().contains("Conflict:"))
            assertTrue(db.replicationApplications().unappliedForReview(farm, 20).any { it.operationId == rejectedId })
        }
        // A journalled but refused edit is not an accepted animal version.
        RoomGoatRepository(leftDb, farm).amendIdentity(AmendGoatIdentity(animalId, tag = "G-REVIEWED"), context("reviewed", 4))
        assertEquals(2L, leftDb.replication().operation(farm, "reviewed")!!.baseVersion ?: -1L)
        assertEquals("Tablet", leftDb.animals().get(farm, animalId)!!.name)
    }

    @Test
    fun theSealedAnimalBaseIsNotReplacedByTheReceivingDevicesCurrentVersion() = runBlocking {
        right.ingest(bundleFrom(leftDb, leftDevice))
        RoomGoatRepository(leftDb, farm).amendIdentity(AmendGoatIdentity(animalId, name = "Amended"), context("identity", 2))
        RoomGoatRepository(rightDb, farm).recordWeight(
            RecordGoatWeight(animalId, "weight-1", 25_000, 1_800_000_000_005),
            context("weight", 3, device = rightDevice),
        )

        right.ingest(bundleFrom(leftDb, leftDevice))

        assertEquals("Original", rightDb.animals().get(farm, animalId)!!.name)
        assertEquals(ApplicationState.FAILED.name, rightDb.replicationApplications().get(farm, "identity")!!.state)
        assertEquals(1L, rightDb.replication().operation(farm, "identity")!!.baseVersion ?: -1L)
    }

    @Test
    fun localPermissionUsesTheCurrentFarmAccountAndDeviceAndRollsBackEveryRejectedWrite() = runBlocking {
        val goats = RoomGoatRepository(leftDb, farm)
        val original = leftDb.animals().get(farm, animalId)
        for ((index, current) in listOf(account().copy(role = "VIEWER"), account().copy(status = "DISABLED")).withIndex()) {
            leftDb.localAccess().upsertAccount(current)
            val failure = failure {
                goats.amendIdentity(AmendGoatIdentity(animalId, tag = "DENIED", officialId = "DENIED"), context("denied-$index", 2))
            }
            assertTrue(failure is AccessDenied)
        }
        leftDb.localAccess().upsertAccount(account())
        assertTrue(failure {
            goats.amendIdentity(AmendGoatIdentity(animalId, name = "Unknown actor"), context("unknown", 3, accountId = "missing"))
        } is AccessDenied)
        val local = leftDb.replication().device(farm, leftDevice)!!
        leftDb.replication().upsertDevice(local.copy(status = "LOST_REVOKED", revokedAfterSequence = 1))
        assertTrue(failure {
            goats.amendIdentity(AmendGoatIdentity(animalId, name = "Revoked device"), context("revoked", 4))
        } is AccessDenied)

        assertEquals(original, leftDb.animals().get(farm, animalId))
        assertTrue(leftDb.lifecycle().identifiersForAnimal(farm, animalId).isEmpty())
        assertEquals(1L, leftDb.replication().count(farm))
        assertEquals(1L, leftDb.outbox().countUnacknowledgedForFarm(farm))
    }

    @Test
    fun foreignFarmContextAccountAndAnimalCannotAmendThisFarmsIdentity() = runBlocking {
        val goats = RoomGoatRepository(leftDb, farm)
        leftDb.animals().insert(otherAnimal("foreign-goat", otherFarm))
        leftDb.localAccess().upsertAccount(account().copy(accountId = "foreign-worker", farmId = otherFarm))
        val original = leftDb.animals().get(farm, animalId)

        failure { goats.amendIdentity(AmendGoatIdentity(animalId, name = "Wrong farm"), context("farm-mismatch", 2).copy(farmId = otherFarm)) }
        assertTrue(failure {
            goats.amendIdentity(AmendGoatIdentity(animalId, name = "Foreign actor"), context("actor-mismatch", 3, accountId = "foreign-worker"))
        } is AccessDenied)
        failure { goats.amendIdentity(AmendGoatIdentity("foreign-goat", name = "Foreign animal"), context("animal-mismatch", 4)) }

        assertEquals(original, leftDb.animals().get(farm, animalId))
        assertEquals("Other", leftDb.animals().get(otherFarm, "foreign-goat")!!.name)
        assertEquals(1L, leftDb.replication().count(farm))
        assertEquals(0L, leftDb.replication().count(otherFarm))
    }

    @Test
    fun duplicateOfficialValueOnThisFarmRejectsTheWholeCorrectionButAnotherFarmDoesNot() = runBlocking {
        val goats = RoomGoatRepository(leftDb, farm)
        goats.amendIdentity(AmendGoatIdentity(animalId, officialId = "OLD"), context("old-identifier", 2))
        leftDb.animals().insert(otherAnimal("other-goat", farm))
        leftDb.animals().insert(otherAnimal("foreign-goat", otherFarm))
        leftDb.lifecycle().insertIdentifier(AnimalIdentifierEntity("other-id", farm, "other-goat", "eid", "DUPLICATE", true, 20_000))
        leftDb.lifecycle().insertIdentifier(AnimalIdentifierEntity("foreign-id", otherFarm, "foreign-goat", "official_id", "FOREIGN", true, 20_000))
        val original = leftDb.animals().get(farm, animalId)

        val rejected = failure {
            goats.amendIdentity(AmendGoatIdentity(animalId, tag = "WRONG", name = "Wrong", officialId = " duplicate "), context("duplicate", 3))
        }

        assertTrue(rejected.message.orEmpty().contains("already recorded on this farm"))
        assertEquals(original, leftDb.animals().get(farm, animalId))
        assertEquals(listOf("OLD"), leftDb.lifecycle().identifiersForAnimal(farm, animalId).filter { it.isActive }.map { it.value })
        assertEquals(2L, leftDb.replication().count(farm))
        goats.amendIdentity(AmendGoatIdentity(animalId, officialId = "FOREIGN"), context("allowed", 4))
        assertEquals(listOf("FOREIGN"), leftDb.lifecycle().identifiersForAnimal(farm, animalId).filter { it.isActive }.map { it.value })
    }

    @Test
    fun reusingAnOperationForDifferentContentOrReplayingWithoutItsJournalCannotWrite() = runBlocking {
        val command = AmendGoatIdentity(animalId, name = "Accepted", officialId = "OFFICIAL")
        val commandContext = context("identity", 2)
        val goats = RoomGoatRepository(leftDb, farm)
        goats.amendIdentity(command, commandContext)

        assertTrue(failure {
            goats.amendIdentity(command.copy(name = "Altered"), commandContext)
        }.message.orEmpty().contains("Mutation id"))
        assertTrue(failure {
            RoomGoatRepository(leftDb, farm, replaying = true).amendIdentity(command.copy(name = "Unjournalled"), context("missing-operation", 3))
        }.message.orEmpty().contains("must be journalled"))

        assertEquals("Accepted", leftDb.animals().get(farm, animalId)!!.name)
        assertEquals(1, leftDb.lifecycle().identifiersForAnimal(farm, animalId).size)
        assertEquals(2L, leftDb.replication().count(farm))
    }

    private suspend fun bundleFrom(db: FarmOsDatabase, device: String): OperationBundle =
        OperationBundle.seal(farm, device, db.replication().operationsInRange(farm, device, 1, Long.MAX_VALUE).map { it.toEnvelope() })

    private fun context(id: String, tick: Long, device: String = leftDevice, accountId: String = actor) =
        LocalCommandContext(farm, accountId, device, id, 1_800_000_000_000 + tick)

    private fun account() =
        LocalAccountEntity(actor, farm, "worker", "Worker", "WORKER", "ACTIVE", "PIN", "test-only-hash", 0, null, null, 1_800_000_000_000)

    private fun otherAnimal(id: String, onFarm: String) =
        AnimalEntity(id, onFarm, id, "Other", "goat", "FEMALE", "active", null, updatedAtEpochMillis = 1_800_000_000_000)

    private suspend fun failure(block: suspend () -> Unit): Throwable =
        requireNotNull(runCatching { block() }.exceptionOrNull()) { "Expected the command to be rejected" }
}
