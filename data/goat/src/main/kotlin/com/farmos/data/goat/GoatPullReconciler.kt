package com.farmos.data.goat

import androidx.room.withTransaction
import com.farmos.core.database.AnimalEntity
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.MeasurementEntity
import com.farmos.core.database.SyncCursorEntity
import com.farmos.core.network.PulledDomainEvent
import com.farmos.core.network.SupabasePullClient
import com.farmos.domain.goat.RecordGoatWeight
import com.farmos.domain.goat.RegisterGoat
import java.time.Instant
import kotlinx.serialization.decodeFromJsonElement
import kotlinx.serialization.json.Json

class GoatPullReconciler(
    private val database: FarmOsDatabase,
    private val pullClient: SupabasePullClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun reconcile(farmId: String, pageSize: Int = 200): PullResult {
        var cursor = database.syncCursors().get(farmId) ?: 0L
        var applied = 0
        var pages = 0

        while (true) {
            val events = pullClient.pull(farmId, cursor, pageSize)
            if (events.isEmpty()) break

            database.withTransaction {
                for (event in events) {
                    applyEvent(farmId, event)
                    database.aggregateVersions().advance(
                        farmId = farmId,
                        aggregateType = event.aggregateType,
                        aggregateId = event.aggregateId,
                        streamVersion = event.streamVersion,
                        updatedAtEpochMillis = now(),
                    )
                    cursor = maxOf(cursor, event.changeCursor)
                    applied++
                }
                database.syncCursors().upsert(
                    SyncCursorEntity(
                        farmId = farmId,
                        changeCursor = cursor,
                        updatedAtEpochMillis = now(),
                    ),
                )
            }

            pages++
            if (events.size < pageSize) break
        }
        return PullResult(appliedEvents = applied, pages = pages, finalCursor = cursor)
    }

    private suspend fun applyEvent(farmId: String, event: PulledDomainEvent) {
        when (event.eventType) {
            "goat.registered.v1" -> {
                val payload = json.decodeFromJsonElement<RegisterGoat>(event.payload)
                require(payload.animalId == event.aggregateId) { "Goat event aggregate mismatch" }
                database.animals().upsertFromServer(
                    AnimalEntity(
                        id = payload.animalId,
                        farmId = farmId,
                        tag = payload.tag,
                        name = payload.name,
                        speciesCode = "goat",
                        sex = payload.sex.name,
                        status = "active",
                        dateOfBirthEpochDay = payload.dateOfBirthEpochDay,
                        updatedAtEpochMillis = Instant.parse(event.recordedAt).toEpochMilli(),
                    ),
                )
            }
            "goat.weight_recorded.v1" -> {
                val payload = json.decodeFromJsonElement<RecordGoatWeight>(event.payload)
                require(payload.animalId == event.aggregateId) { "Weight event aggregate mismatch" }
                database.measurements().upsertFromServer(
                    MeasurementEntity(
                        id = payload.measurementId,
                        farmId = farmId,
                        animalId = payload.animalId,
                        type = "weight",
                        valueLong = payload.weightGrams,
                        unit = "g",
                        measuredAtEpochMillis = payload.measuredAtEpochMillis,
                    ),
                )
            }
            else -> throw UnsupportedServerEvent(
                "Unsupported event ${event.eventType} schema=${event.schemaVersion}; cursor was not advanced",
            )
        }
    }
}

data class PullResult(
    val appliedEvents: Int,
    val pages: Int,
    val finalCursor: Long,
)

class UnsupportedServerEvent(message: String) : IllegalStateException(message)
