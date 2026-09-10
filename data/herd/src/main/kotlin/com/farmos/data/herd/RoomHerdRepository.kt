package com.farmos.data.herd

import androidx.room.withTransaction
import com.farmos.core.database.AnimalEntity
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.OutboxEntity
import com.farmos.core.model.LocalCommandContext
import com.farmos.core.model.LocalCommandResult
import com.farmos.core.model.SyncState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class RoomHerdRepository(
    private val database: FarmOsDatabase,
    private val farmId: String,
    private val species: String,
    private val json: Json = Json { encodeDefaults = true },
) {
    suspend fun register(
        animalId: String,
        tag: String,
        name: String?,
        sex: String,
        poultryKindCode: String?,
        context: LocalCommandContext,
    ): LocalCommandResult {
        require(context.farmId == farmId)
        if (tag.isBlank()) error("Tag is required")
        if (species == "poultry") {
            val kind = poultryKindCode?.trim().orEmpty()
            if (kind !in POULTRY_KINDS) {
                error("Poultry kind must be chicken, duck, muscovy, guinea_fowl, turkey, goose, quail, pigeon, or farm_defined")
            }
        }
        val payload = buildJsonObject {
            put("animalId", animalId)
            put("tag", tag.trim())
            if (!name.isNullOrBlank()) put("name", name.trim())
            put("sex", sex)
            if (species == "poultry") put("poultryKindCode", poultryKindCode!!.trim())
        }
        database.withTransaction {
            database.animals().insert(
                AnimalEntity(
                    id = animalId,
                    farmId = farmId,
                    tag = tag.trim(),
                    name = name?.trim()?.takeIf { it.isNotEmpty() },
                    speciesCode = species,
                    sex = sex,
                    status = "active",
                    dateOfBirthEpochDay = null,
                    poultryKindCode = poultryKindCode?.trim()?.takeIf { species == "poultry" },
                    updatedAtEpochMillis = context.occurredAtEpochMillis,
                ),
            )
            database.outbox().insert(
                OutboxEntity(
                    mutationId = context.mutationId,
                    farmId = farmId,
                    actorId = context.actorId,
                    deviceId = context.deviceId,
                    commandName = "$species.register.v1",
                    commandSchemaVersion = 1,
                    aggregateType = "animal",
                    aggregateId = animalId,
                    aggregateOrdinal = database.outbox().nextAggregateOrdinal(farmId, "animal", animalId),
                    expectedStreamVersion = 0,
                    payloadJson = json.encodeToString(payload),
                    occurredAtEpochMillis = context.occurredAtEpochMillis,
                    createdAtEpochMillis = System.currentTimeMillis(),
                    state = SyncState.PENDING.name,
                    attemptCount = 0,
                    nextAttemptAtEpochMillis = null,
                    lastErrorCode = null,
                    serverEventId = null,
                    serverStreamVersion = null,
                ),
            )
        }
        return LocalCommandResult(context.mutationId, animalId, true)
    }

    suspend fun recordWeight(
        animalId: String,
        measurementId: String,
        weightGrams: Long,
        measuredAtEpochMillis: Long,
        context: LocalCommandContext,
    ): LocalCommandResult {
        require(context.farmId == farmId)
        if (weightGrams <= 0L) error("Weight must be greater than zero")
        val animal = requireNotNull(database.animals().get(farmId, animalId)) { "Animal not found" }
        require(animal.speciesCode == species && animal.status == "active") {
            "Only an active $species record can take a new weight"
        }
        val payload = buildJsonObject {
            put("animalId", animalId)
            put("measurementId", measurementId)
            put("weightGrams", weightGrams)
            put("measuredAtEpochMillis", measuredAtEpochMillis)
        }
        enqueue(
            context = context,
            commandName = "$species.record_weight.v1",
            aggregateId = animalId,
            expectedStreamVersion = nextExpectedStreamVersion(animalId),
            payloadJson = json.encodeToString(payload),
        ) {
            database.measurements().insert(
                com.farmos.core.database.MeasurementEntity(
                    id = measurementId,
                    farmId = farmId,
                    animalId = animalId,
                    type = "weight",
                    valueLong = weightGrams,
                    unit = "g",
                    measuredAtEpochMillis = measuredAtEpochMillis,
                ),
            )
        }
        return LocalCommandResult(context.mutationId, animalId, true)
    }

    suspend fun setStatus(
        animalId: String,
        status: String,
        context: LocalCommandContext,
    ): LocalCommandResult {
        require(context.farmId == farmId)
        if (status !in setOf("sold", "dead", "culled")) error("Status must be sold, dead, or culled")
        val animal = requireNotNull(database.animals().get(farmId, animalId)) { "Animal not found" }
        require(animal.speciesCode == species && animal.status == "active") {
            "Only an active $species record can change lifecycle status"
        }
        val payload = buildJsonObject {
            put("animalId", animalId)
            put("status", status)
        }
        enqueue(
            context = context,
            commandName = "$species.set_status.v1",
            aggregateId = animalId,
            expectedStreamVersion = nextExpectedStreamVersion(animalId),
            payloadJson = json.encodeToString(payload),
        ) {
            database.animals().updateStatus(farmId, animalId, status, context.occurredAtEpochMillis)
        }
        return LocalCommandResult(context.mutationId, animalId, true)
    }

    suspend fun list(): List<AnimalEntity> = database.animals().listBySpecies(farmId, species, 200)

    private suspend fun nextExpectedStreamVersion(animalId: String): Long {
        val authoritative = database.aggregateVersions().getVersion(farmId, "animal", animalId) ?: 0L
        val queued = database.outbox().countUnacknowledgedForAggregate(farmId, "animal", animalId)
        return authoritative + queued
    }

    private suspend fun enqueue(
        context: LocalCommandContext,
        commandName: String,
        aggregateId: String,
        expectedStreamVersion: Long?,
        payloadJson: String,
        localWrite: suspend () -> Unit,
    ) {
        database.withTransaction {
            localWrite()
            database.outbox().insert(
                OutboxEntity(
                    mutationId = context.mutationId,
                    farmId = farmId,
                    actorId = context.actorId,
                    deviceId = context.deviceId,
                    commandName = commandName,
                    commandSchemaVersion = 1,
                    aggregateType = "animal",
                    aggregateId = aggregateId,
                    aggregateOrdinal = database.outbox().nextAggregateOrdinal(farmId, "animal", aggregateId),
                    expectedStreamVersion = expectedStreamVersion,
                    payloadJson = payloadJson,
                    occurredAtEpochMillis = context.occurredAtEpochMillis,
                    createdAtEpochMillis = System.currentTimeMillis(),
                    state = SyncState.PENDING.name,
                    attemptCount = 0,
                    nextAttemptAtEpochMillis = null,
                    lastErrorCode = null,
                    serverEventId = null,
                    serverStreamVersion = null,
                ),
            )
        }
    }

    companion object {
        val POULTRY_KINDS = setOf(
            "chicken",
            "duck",
            "muscovy",
            "guinea_fowl",
            "turkey",
            "goose",
            "quail",
            "pigeon",
            "farm_defined",
        )
    }
}
