package com.farmos.data.goat

import androidx.room.withTransaction
import com.farmos.core.database.AnimalEntity
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.KiddingEntity
import com.farmos.core.database.MeasurementEntity
import com.farmos.core.database.SyncCursorEntity
import com.farmos.core.network.PulledDomainEvent
import com.farmos.core.network.SupabasePullClient
import com.farmos.core.network.UnsupportedServerEvent
import com.farmos.domain.goat.RecordGoatFamacha
import com.farmos.domain.goat.RecordGoatKidding
import com.farmos.domain.goat.RecordGoatWeight
import com.farmos.domain.goat.RegisterGoat
import com.farmos.domain.goat.SetGoatStatus
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

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
                val payload = json.decodeFromJsonElement(RegisterGoat.serializer(), event.payload)
                require(payload.animalId == event.aggregateId) { "Goat event aggregate mismatch" }
                val existing = database.animals().get(farmId, payload.animalId)
                database.animals().upsertFromServer(
                    AnimalEntity(
                        id = payload.animalId,
                        farmId = farmId,
                        tag = payload.tag,
                        name = payload.name,
                        speciesCode = "goat",
                        sex = payload.sex.name,
                        status = existing?.status ?: "active",
                        dateOfBirthEpochDay = payload.dateOfBirthEpochDay,
                        updatedAtEpochMillis = Instant.parse(event.recordedAt).toEpochMilli(),
                    ),
                )
            }
            "goat.weight_recorded.v1" -> {
                val payload = json.decodeFromJsonElement(RecordGoatWeight.serializer(), event.payload)
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
            "goat.scc_recorded.v1" -> {
                val payload = json.decodeFromJsonElement(com.farmos.domain.goat.RecordGoatScc.serializer(), event.payload)
                database.lifecycle().upsertGoatScc(
                    com.farmos.core.database.GoatSccEntity(
                        payload.recordId, farmId, payload.animalId, payload.cellsPerMl, payload.dimDays, payload.occurredEpochDay,
                    ),
                )
            }
            "goat.bcs_recorded.v1" -> {
                val payload = json.decodeFromJsonElement(com.farmos.domain.goat.RecordGoatBcs.serializer(), event.payload)
                database.lifecycle().upsertGoatBcs(
                    com.farmos.core.database.GoatBcsEntity(
                        payload.scoreId, farmId, payload.animalId, payload.scoreTenths, payload.occurredEpochDay,
                    ),
                )
            }
            "goat.milk_recorded.v1" -> {
                val payload = json.decodeFromJsonElement(com.farmos.domain.goat.RecordGoatMilk.serializer(), event.payload)
                database.lifecycle().upsertMilk(
                    com.farmos.core.database.GoatMilkEntity(
                        id = payload.milkId,
                        farmId = farmId,
                        animalId = payload.animalId,
                        litresMilli = payload.litresMilli,
                        occurredEpochDay = payload.occurredEpochDay,
                    ),
                )
            }
            "goat.famacha_recorded.v1" -> {
                val payload = json.decodeFromJsonElement(RecordGoatFamacha.serializer(), event.payload)
                database.famacha().upsertFromServer(
                    com.farmos.core.database.FamachaScoreEntity(
                        id = payload.scoreId,
                        farmId = farmId,
                        animalId = payload.animalId,
                        score = payload.score,
                        occurredEpochDay = payload.occurredEpochDay,
                    ),
                )
            }
            "goat.kidded.v1" -> {
                val payload = json.decodeFromJsonElement(RecordGoatKidding.serializer(), event.payload)
                database.kidding().upsertFromServer(
                    KiddingEntity(
                        id = payload.kiddingId,
                        farmId = farmId,
                        damId = payload.damAnimalId,
                        bornCount = payload.bornCount,
                        liveCount = payload.liveCount,
                        deadCount = payload.deadCount,
                        occurredEpochDay = payload.occurredEpochDay,
                    ),
                )
            }
            "goat.heat_recorded.v1" -> {
                val payload = json.decodeFromJsonElement(com.farmos.domain.goat.RecordGoatHeat.serializer(), event.payload)
                database.lifecycle().upsertHeat(
                    com.farmos.core.database.GoatHeatEntity(payload.heatId, farmId, payload.animalId, payload.occurredEpochDay),
                )
            }
            "goat.mating_recorded.v1" -> {
                val payload = json.decodeFromJsonElement(com.farmos.domain.goat.RecordGoatMating.serializer(), event.payload)
                database.lifecycle().upsertMating(
                    com.farmos.core.database.GoatMatingEntity(payload.matingId, farmId, payload.damId, payload.sireId, payload.method, payload.occurredEpochDay),
                )
                database.tasks().upsertFromServer(
                    com.farmos.core.database.TaskEntity(
                        payload.pregCheckTaskId, farmId, "goat", "PREG_CHECK", "Pregnancy check",
                        payload.occurredEpochDay + 45, "open", payload.damId, null, null,
                        Instant.parse(event.recordedAt).toEpochMilli(),
                    ),
                )
            }
            "goat.kid_registered.v1" -> {
                val payload = json.decodeFromJsonElement(com.farmos.domain.goat.RegisterGoatKid.serializer(), event.payload)
                val recordedAt = Instant.parse(event.recordedAt).toEpochMilli()
                val existing = database.animals().get(farmId, payload.animalId)
                database.animals().upsertFromServer(
                    AnimalEntity(
                        id = payload.animalId,
                        farmId = farmId,
                        tag = payload.tag,
                        name = payload.name,
                        speciesCode = "goat",
                        sex = payload.sex.name,
                        status = existing?.status ?: "active",
                        dateOfBirthEpochDay = payload.dateOfBirthEpochDay,
                        updatedAtEpochMillis = recordedAt,
                    ),
                )
                val damId = database.kidding().get(farmId, payload.kiddingId)?.damId
                if (damId != null) {
                    database.lifecycle().upsertKid(
                        com.farmos.core.database.GoatKidEntity(payload.animalId, farmId, payload.animalId, payload.kiddingId, damId),
                    )
                    database.lifecycle().upsertPedigree(
                        com.farmos.core.database.PedigreeRelationEntity(payload.pedigreeLinkId, farmId, payload.animalId, damId, "dam"),
                    )
                }
            }
            "goat.lactation_planned.v1" -> {
                val payload = json.decodeFromJsonElement(com.farmos.domain.goat.PlanGoatLactation.serializer(), event.payload)
                database.lifecycle().upsertLactation(
                    com.farmos.core.database.GoatLactationPlanEntity(payload.planId, farmId, payload.animalId, payload.kiddingId, payload.occurredEpochDay),
                )
                database.tasks().upsertFromServer(
                    com.farmos.core.database.TaskEntity(
                        payload.checkTaskId, farmId, "goat", "LACTATION_CHECK", "Lactation follow-up",
                        payload.occurredEpochDay + 7, "open", payload.animalId, null, null,
                        Instant.parse(event.recordedAt).toEpochMilli(),
                    ),
                )
            }
            "goat.pregnancy_checked.v1" -> {
                val payload = json.decodeFromJsonElement(com.farmos.domain.goat.RecordGoatPregnancy.serializer(), event.payload)
                database.lifecycle().upsertPregnancy(
                    com.farmos.core.database.GoatPregnancyEntity(payload.checkId, farmId, payload.animalId, payload.result, payload.occurredEpochDay),
                )
            }
            "goat.status_changed.v1" -> {
                val payload = json.decodeFromJsonElement(SetGoatStatus.serializer(), event.payload)
                require(payload.animalId == event.aggregateId) { "Status event aggregate mismatch" }
                requireNotNull(database.animals().get(farmId, payload.animalId)) {
                    "Status event for unknown goat ${payload.animalId}"
                }
                database.animals().updateStatus(
                    farmId = farmId,
                    animalId = payload.animalId,
                    status = payload.status.wireValue(),
                    updatedAtEpochMillis = Instant.parse(event.recordedAt).toEpochMilli(),
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
