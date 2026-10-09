package com.farmos.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.ApplicationState
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.journalLocalOperation
import com.farmos.core.database.toEnvelope
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.HerdReplicationAppliers
import com.farmos.data.herd.OpsReplicationAppliers
import com.farmos.data.herd.RoomHerdRepository
import com.farmos.data.herd.RoomOpsRepository
import com.farmos.domain.access.LocalRole
import com.farmos.domain.rabbit.RecordRabbitWeight
import com.farmos.domain.replication.OperationBundle
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Preserve both historical rabbit weight contracts without making map order decide a unit. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-rUS")
class RabbitWeightCompatibilityTest {
    private val farm = "rabbit-weight-farm"
    private val device = "tablet"
    private val actor = "worker"
    private val at = 1_790_000_000_000L
    private val databases = mutableListOf<FarmOsDatabase>()
    private fun database() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FarmOsDatabase::class.java)
        .allowMainThreadQueries().build().also { databases += it }
    private fun context(id: String) = LocalCommandContext(farm, actor, device, id, at)

    @After
    fun close() = databases.forEach { it.close() }

    private suspend fun origin(): FarmOsDatabase {
        val database = database()
        seedCommandAuthority(database, farm, actor, device, LocalRole.WORKER)
        val herd = RoomHerdRepository(database, farm, "rabbit")
        herd.register("rabbit", "R-1", "Clover", "FEMALE", null, context("register"))
        RoomOpsRepository(database, farm).recordRabbitWeight(
            RecordRabbitWeight("kg-weight", "rabbit", 2.25, at), context("kg"),
        )
        herd.recordWeight("rabbit", "grams-weight", 3_100, at, context("grams"))
        historicalGrams(database, "legacy-grams", false)
        return database
    }

    private suspend fun historicalGrams(database: FarmOsDatabase, id: String, ambiguous: Boolean) {
        val payload = JSONObject().put("animalId", "rabbit").put("measurementId", id)
            .put("weightGrams", 3_400).put("measuredAtEpochMillis", at + 1_000)
        if (ambiguous) payload.put("weightId", "other").put("weightKg", 99.0).put("weighedAtEpochMillis", at)
        database.journalLocalOperation(
            id, farm, "animal", "rabbit", actor, device, at, at, null,
            "rabbit.record_weight.v1", payload.toString(), 1,
        )
    }

    @Test
    fun bothApplierMapOrdersReplayKgLegacyGramsAndExplicitGramsV2WithoutConversion() = runBlocking {
        val source = origin()
        val newGrams = source.replication().operation(farm, "grams")!!
        assertEquals("rabbit.record_weight.v2", newGrams.operationType)
        assertEquals(2, newGrams.schemaVersion)
        val operations = source.replication().operationsInRange(farm, device, 1, 4).map { it.toEnvelope() }
        for (appliers in listOf(
            HerdReplicationAppliers.all + OpsReplicationAppliers.all,
            OpsReplicationAppliers.all + HerdReplicationAppliers.all,
        )) {
            val target = database()
            val receiver = RoomReplicaEndpoint(target, farm, "phone", appliers).apply { registerPairedDevice(device, "Tablet") }
            val bundle = OperationBundle.seal(farm, device, operations)
            assertEquals(4, receiver.ingest(bundle).applied)
            assertEquals(4, receiver.ingest(bundle).duplicates)
            assertEquals(2.25, target.lifecycle().latestRabbitWeight(farm, "rabbit")!!.weightKg, 0.0)
            assertEquals(
                listOf("grams-weight" to 3_100L, "legacy-grams" to 3_400L),
                target.measurements().history(farm, "rabbit", "weight").map { it.id to it.valueLong },
            )
            for (operation in operations) {
                assertEquals(ApplicationState.APPLIED.name, target.replicationApplications().get(farm, operation.operationId)!!.state)
            }
            assertEquals(1, target.lifecycle().rabbitWeightHistory(farm, "rabbit").size)
            assertEquals(4L, target.replication().count(farm))
            assertEquals(0L, target.outbox().countUnacknowledgedForFarm(farm))
        }
    }

    @Test
    fun ambiguousHistoricalUnitsStayInReviewWithoutChangingEitherWeightProjection() = runBlocking {
        val source = origin()
        historicalGrams(source, "ambiguous", true)
        val target = database()
        val receiver = RoomReplicaEndpoint(target, farm, "phone", OpsReplicationAppliers.all + HerdReplicationAppliers.all)
            .apply { registerPairedDevice(device, "Tablet") }
        val operations = source.replication().operationsInRange(farm, device, 1, 5).map { it.toEnvelope() }
        receiver.ingest(OperationBundle.seal(farm, device, operations))
        val application = target.replicationApplications().get(farm, "ambiguous")!!
        assertEquals(ApplicationState.FAILED.name, application.state)
        assertTrue(application.reason.orEmpty().contains("unit contract"))
        assertEquals(2.25, target.lifecycle().latestRabbitWeight(farm, "rabbit")!!.weightKg, 0.0)
        assertEquals(3_400L, target.measurements().latest(farm, "rabbit", "weight")!!.valueLong)
        assertEquals(5L, target.replication().count(farm))
    }
}
