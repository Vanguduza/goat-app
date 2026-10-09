package com.farmos.data.herd

import androidx.room.withTransaction
import com.farmos.core.database.AnimalEntity
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.OutboxEntity
import com.farmos.core.database.insertOutboxAndJournal
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
    /** Replays an operation received from another device: domain writes only, it is already journalled. */
    private val replaying: Boolean = false,
) {
    init { require(species in HerdReplicationAppliers.SPECIES) { "Unsupported species repository" } }

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
        enqueue(
            context, "$species.register.v1", animalId, 0, json.encodeToString(payload),
        ) {
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
        val payload = buildJsonObject {
            put("animalId", animalId)
            put("measurementId", measurementId)
            put("weightGrams", weightGrams)
            put("measuredAtEpochMillis", measuredAtEpochMillis)
        }
        // Keep an exact historical grams receipt replayable; new rabbit measurements have a
        // distinct version from the rabbit module's kg-based v1 command.
        val previous = if (species == "rabbit") database.replication().operation(farmId, context.mutationId) else null
        val weightCommand = if (species == "rabbit" && previous?.operationType != "rabbit.record_weight.v1") {
            RabbitWeightReplication.GRAMS_V2
        } else "$species.record_weight.v1"
        enqueue(
            context = context,
            commandName = weightCommand,
            aggregateId = animalId,
            expectedStreamVersion = nextExpectedStreamVersion(animalId),
            payloadJson = json.encodeToString(payload),
        ) {
            val animal = requireNotNull(database.animals().get(farmId, animalId)) { "Animal not found" }
            require(animal.speciesCode == species && animal.status == "active") {
                "Only an active $species record can take a new weight"
            }
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
            val animal = requireNotNull(database.animals().get(farmId, animalId)) { "Animal not found" }
            require(animal.speciesCode == species && animal.status == "active") {
                "Only an active $species record can change lifecycle status"
            }
            database.animals().updateStatus(farmId, animalId, status, context.occurredAtEpochMillis)
        }
        return LocalCommandResult(context.mutationId, animalId, true)
    }

    suspend fun list(): List<AnimalEntity> = database.animals().listBySpecies(farmId, species, 200)

    /** Exhaustive count of the animals [list] can show (every status except closed), past its 200-row bound. */
    suspend fun listedTotal(): Int = database.animals().countBySpecies(farmId, species)

    /** Exhaustive count of active animals of this species. */
    suspend fun activeCount(): Int = database.animals().herdCounts(farmId, species, "active", todayEpochDay = 0).active

    private suspend fun nextExpectedStreamVersion(animalId: String): Long {
        val authoritative = database.aggregateVersions().getVersion(farmId, "animal", animalId) ?: 0L
        val queued = database.outbox().countUnacknowledgedForAggregate(farmId, "animal", animalId)
        return authoritative + queued
    }

    private suspend fun journal(outbox: OutboxEntity) {
        if (!replaying) database.insertOutboxAndJournal(outbox)
    }

    private suspend fun enqueue(
        context: LocalCommandContext,
        commandName: String,
        aggregateId: String,
        expectedStreamVersion: Long?,
        payloadJson: String,
        localWrite: suspend () -> Unit,
    ) {
        require(context.farmId == farmId) { "Farm context mismatch" }
        database.withTransaction {
            val permission = OpsCommandPermissions.requiredFor(commandName, payloadJson)
            if (!replaying) database.requireLocalCommandAuthority(context, permission)
            val original = database.replication().operation(farmId, context.mutationId)
            if (original != null) {
                requireOriginalCommand(original, context, commandName, "animal", aggregateId, {
                    Json.parseToJsonElement(it) == Json.parseToJsonElement(payloadJson)
                })
                if (database.commandAlreadyApplied(original, replaying)) return@withTransaction
            } else {
                require(!replaying) { "A received animal change must be journalled before it is applied" }
            }
            if (!replaying) OpsCommandPermissions.requireLocalVersion(commandName)
            localWrite()
            journal(
                OutboxEntity(
                    mutationId = context.mutationId,
                    farmId = farmId,
                    actorId = context.actorId,
                    deviceId = context.deviceId,
                    commandName = commandName,
                    commandSchemaVersion = OpsCommandPermissions.schemaVersionFor(commandName),
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
