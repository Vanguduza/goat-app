package com.farmos.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.ApplicationState
import com.farmos.core.database.COMMAND_PAYLOAD_KEY
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.toEnvelope
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.access.AccessDenied
import com.farmos.domain.access.LocalRole
import com.farmos.domain.ops.CreateAnimalGroup
import com.farmos.domain.ops.CreateFormularyItem
import com.farmos.domain.ops.RecordHealthTreatment
import com.farmos.domain.ops.RecordHealthVaccination
import com.farmos.domain.ops.RecordPoultryVaccination
import com.farmos.domain.replication.MergeClass
import com.farmos.domain.replication.OperationBundle
import com.farmos.domain.replication.OperationEnvelope
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-rUS")
class FormularyApprovalTest {
    private val farm = "11111111-1111-4111-8111-111111111111"
    private val databases = mutableListOf<FarmOsDatabase>()
    private val time = 1_790_000_000_000L

    private fun database(device: String) = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(), FarmOsDatabase::class.java,
    ).allowMainThreadQueries().build().also { db ->
        databases += db
        seedCommandAuthority(db, farm, "owner", device, LocalRole.OWNER)
        seedCommandAuthority(db, farm, "manager", device, LocalRole.MANAGER)
        seedCommandAuthority(db, farm, "worker", device, LocalRole.WORKER)
    }

    private fun context(actor: String = "owner", device: String = "A") =
        LocalCommandContext(farm, actor, device, UUID.randomUUID().toString(), time)

    private fun product(id: String, species: String = "goat") =
        CreateFormularyItem(id, "Recorded vaccine", species, "vaccine", 7, null, null)

    @After
    fun close() = databases.forEach { it.close() }

    @Test
    fun newOwnerEntriesAreDraftsAndRemainUnapprovedAfterReplication(): Unit = runBlocking {
        val origin = database("A")
        val receiver = database("B")
        val ops = RoomOpsRepository(origin, farm)
        val admitted = context()
        ops.createFormulary(product("draft"), admitted)
        val row = requireNotNull(origin.formulary().get(farm, "draft"))
        assertFalse(row.vetApproved)
        assertTrue(ops.approvedFormulary().isEmpty())
        val operation = requireNotNull(origin.replication().operation(farm, admitted.mutationId))
        assertEquals("formulary.item_create.v2", operation.operationType)
        assertEquals(2, operation.schemaVersion)
        assertFalse(Json.decodeFromString<CreateFormularyItem>(operation.payloadJson).vetApproved)
        ops.createFormulary(product("draft"), admitted)
        assertEquals(1L, origin.replication().count(farm))
        assertEquals(1L, origin.outbox().countUnacknowledgedForFarm(farm))
        val target = RoomReplicaEndpoint(receiver, farm, "B", replicationAppliers).apply { registerPairedDevice("A", "Origin") }
        val bundle = OperationBundle.seal(farm, "A", listOf(operation.toEnvelope()))
        assertNull(target.ingest(bundle).rejectedReason)
        assertEquals(ApplicationState.APPLIED.name, receiver.replicationApplications().get(farm, operation.operationId)?.state)
        assertEquals(row, receiver.formulary().get(farm, "draft"))
        assertTrue(RoomOpsRepository(receiver, farm).approvedFormulary().isEmpty())
        assertNull(target.ingest(bundle).rejectedReason)
        assertEquals(1L, receiver.replication().count(farm))
        assertEquals(0L, receiver.outbox().countUnacknowledgedForFarm(farm))
    }

    @Test
    fun historicalV1ApprovalKeepsItsRecordedMeaningAndCannotBeForgedAsANewLocalWrite(): Unit = runBlocking {
        val db = database("B")
        val legacyContext = context(device = "A")
        val command = product("legacy")
        val operation = OperationEnvelope.seal(
            operationId = legacyContext.mutationId, farmId = farm, entityType = "formulary_item", entityId = command.itemId,
            actorId = legacyContext.actorId, deviceId = "A", deviceSequence = 1L,
            businessTimeEpochMillis = time, createdAtEpochMillis = time, baseVersion = 0L,
            operationType = "formulary.item_create.v1", mergeClass = MergeClass.APPEND_ONLY_EVENT,
            payload = mapOf(COMMAND_PAYLOAD_KEY to Json.encodeToString(command)),
            schemaVersion = 1, provenance = "accepted-legacy-formulary-fixture",
        )
        val target = RoomReplicaEndpoint(db, farm, "B", replicationAppliers).apply { registerPairedDevice("A", "Historical origin") }
        assertNull(target.ingest(OperationBundle.seal(farm, "A", listOf(operation))).rejectedReason)
        assertTrue(requireNotNull(db.formulary().get(farm, "legacy")).vetApproved)
        assertEquals(operation, db.replication().operation(farm, operation.operationId)?.toEnvelope())
        RoomOpsRepository(db, farm, replaying = true).createFormulary(command, legacyContext)
        assertEquals(1L, db.replication().count(farm))
        assertTrue(runCatching {
            RoomOpsRepository(db, farm, replaying = true).createFormulary(product("forged"), context(device = "B"))
        }.isFailure)
        assertNull(db.formulary().get(farm, "forged"))
        RoomOpsRepository(db, farm).createFormulary(product("new"), context(device = "B"))
        assertFalse(requireNotNull(db.formulary().get(farm, "new")).vetApproved)
        assertTrue(requireNotNull(db.formulary().get(farm, "legacy")).vetApproved)
    }

    @Test
    fun receivedV2ApprovalClaimsFailWithoutChangingTheAcceptedOriginal(): Unit = runBlocking {
        val db = database("B")
        val source = context(device = "A")
        val command = product("false-approval").copy(vetApproved = true)
        val operation = OperationEnvelope.seal(
            operationId = source.mutationId, farmId = farm, entityType = "formulary_item", entityId = command.itemId,
            actorId = source.actorId, deviceId = "A", deviceSequence = 1L,
            businessTimeEpochMillis = time, createdAtEpochMillis = time, baseVersion = 0L,
            operationType = "formulary.item_create.v2", mergeClass = MergeClass.APPEND_ONLY_EVENT,
            payload = mapOf(COMMAND_PAYLOAD_KEY to Json.encodeToString(command)),
            schemaVersion = 2, provenance = "invalid-draft-approval-fixture",
        )
        val target = RoomReplicaEndpoint(db, farm, "B", replicationAppliers).apply {
            registerPairedDevice("A", "Origin")
        }
        target.ingest(OperationBundle.seal(farm, "A", listOf(operation)))
        assertNull(db.formulary().get(farm, command.itemId))
        assertEquals(ApplicationState.FAILED.name, db.replicationApplications().get(farm, operation.operationId)?.state)
        assertEquals(operation, db.replication().operation(farm, operation.operationId)?.toEnvelope())
        assertEquals(0L, db.outbox().countUnacknowledgedForFarm(farm))
        assertTrue(runCatching {
            RoomOpsRepository(db, farm, replaying = true).createFormulary(command.copy(vetApproved = false), source)
        }.isFailure)
        assertNull(db.formulary().get(farm, command.itemId))
    }

    @Test
    fun ownerCreationDoesNotAuthorizeTreatmentOrEitherVaccinationPath(): Unit = runBlocking {
        val db = database("A")
        val ops = RoomOpsRepository(db, farm)
        ops.createFormulary(product("draft-poultry", "poultry"), context())
        ops.createGroup(CreateAnimalGroup("flock", "poultry", "Layers", 10), context())
        val before = db.replication().count(farm)
        val denied = listOf<suspend () -> Unit>(
            { ops.recordTreatment(
                RecordHealthTreatment(treatmentId = "treatment", speciesCode = "poultry", formularyItemId = "draft-poultry",
                    reason = "Recorded care", occurredAtEpochMillis = time), context(),
            ) },
            { ops.recordVaccination(
                RecordHealthVaccination(vaccinationId = "health-vaccine", speciesCode = "poultry", groupId = "flock",
                    formularyItemId = "draft-poultry", occurredAtEpochMillis = time), context(),
            ) },
            { ops.recordVaccination(
                RecordPoultryVaccination("poultry-vaccine", "flock", "chicken", "draft-poultry", 20_700L), context(),
            ) },
        )
        denied.forEach { action ->
            val failure = runCatching { action() }.exceptionOrNull()
            assertNotNull(failure)
            assertTrue(failure?.message.orEmpty().contains("vet-approved"))
        }
        assertTrue(db.treatments().recent(farm, 10).isEmpty())
        assertTrue(db.vaccinations().recent(farm, 10).isEmpty())
        assertEquals(before, db.replication().count(farm))
        assertEquals(before, db.outbox().countUnacknowledgedForFarm(farm))
    }

    @Test
    fun workersAndManagersCannotCreateApprovalRecordsAndDraftsNeverEnterTheClinicalSelector(): Unit = runBlocking {
        val db = database("A")
        val ops = RoomOpsRepository(db, farm)
        for (actor in listOf("worker", "manager")) {
            assertTrue(runCatching { ops.createFormulary(product(actor), context(actor)) }.exceptionOrNull() is AccessDenied)
        }
        assertTrue(ops.formularyItems().isEmpty())
        assertEquals(0L, db.replication().count(farm))
        ops.createFormulary(product("draft"), context())
        val resources = loadHealthModuleResources(ops)
        assertTrue(resources.formulary.single().contains("Draft · not vet approved"))
        assertTrue(resources.formularyOptions.isEmpty())
        assertFalse(requireNotNull(db.formulary().get(farm, "draft")).vetApproved)
    }
}
