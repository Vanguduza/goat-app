package com.farmos.data.goat

import androidx.room.withTransaction
import com.farmos.core.database.AnimalEntity
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.MeasurementEntity
import com.farmos.core.database.OutboxEntity
import com.farmos.core.model.SyncState
import com.farmos.domain.goat.GoatRepository
import com.farmos.domain.goat.GoatSearchResult
import com.farmos.domain.goat.GoatSex
import com.farmos.domain.goat.GoatSnapshot
import com.farmos.domain.goat.GoatValidationResult
import com.farmos.domain.goat.GoatValidator
import com.farmos.domain.goat.LocalCommandContext
import com.farmos.domain.goat.LocalCommandResult
import com.farmos.domain.goat.RecordGoatWeight
import com.farmos.domain.goat.RegisterGoat
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RoomGoatRepository(
    private val database: FarmOsDatabase,
    private val farmId: String,
    private val json: Json = Json { encodeDefaults = true },
) : GoatRepository {
    override suspend fun registerGoat(command: RegisterGoat, context: LocalCommandContext): LocalCommandResult {
        require(context.farmId == farmId) { "Farm context mismatch" }
        val validation = GoatValidator.register(command)
        require(validation is GoatValidationResult.Valid) {
            (validation as GoatValidationResult.Invalid).message
        }

        database.withTransaction {
            database.animals().insert(
                AnimalEntity(
                    id = command.animalId,
                    farmId = farmId,
                    tag = command.tag.trim(),
                    name = command.name?.trim()?.takeIf { it.isNotEmpty() },
                    speciesCode = "goat",
                    sex = command.sex.name,
                    status = "active",
                    dateOfBirthEpochDay = command.dateOfBirthEpochDay,
                    updatedAtEpochMillis = context.occurredAtEpochMillis,
                ),
            )
            database.outbox().insert(
                outbox(
                    context = context,
                    commandName = "goat.register.v1",
                    aggregateId = command.animalId,
                    expectedStreamVersion = 0,
                    payloadJson = json.encodeToString(command),
                ),
            )
        }
        return LocalCommandResult(context.mutationId, command.animalId, locallyDurable = true)
    }

    override suspend fun recordWeight(command: RecordGoatWeight, context: LocalCommandContext): LocalCommandResult {
        require(context.farmId == farmId) { "Farm context mismatch" }
        val validation = GoatValidator.weight(command)
        require(validation is GoatValidationResult.Valid) {
            (validation as GoatValidationResult.Invalid).message
        }
        requireNotNull(database.animals().get(farmId, command.animalId)) { "Goat not found" }

        database.withTransaction {
            database.measurements().insert(
                MeasurementEntity(
                    id = command.measurementId,
                    farmId = farmId,
                    animalId = command.animalId,
                    type = "weight",
                    valueLong = command.weightGrams,
                    unit = "g",
                    measuredAtEpochMillis = command.measuredAtEpochMillis,
                ),
            )
            database.outbox().insert(
                outbox(
                    context = context,
                    commandName = "goat.record_weight.v1",
                    aggregateId = command.animalId,
                    expectedStreamVersion = null,
                    payloadJson = json.encodeToString(command),
                ),
            )
        }
        return LocalCommandResult(context.mutationId, command.animalId, locallyDurable = true)
    }

    override suspend fun getGoat(animalId: String): GoatSnapshot? {
        val animal = database.animals().get(farmId, animalId) ?: return null
        val weight = database.measurements().latest(farmId, animalId, "weight")
        return GoatSnapshot(
            animalId = animal.id,
            farmId = animal.farmId,
            tag = animal.tag,
            name = animal.name,
            sex = GoatSex.valueOf(animal.sex),
            latestWeightGrams = weight?.valueLong,
            syncPending = database.outbox().hasPending(farmId, animalId),
        )
    }

    override suspend fun searchGoats(query: String, limit: Int): List<GoatSearchResult> =
        database.animals().searchBySpecies(
            farmId = farmId,
            speciesCode = "goat",
            query = query.trim(),
            limit = limit.coerceIn(1, 100),
        ).map { animal ->
            GoatSearchResult(
                animalId = animal.id,
                tag = animal.tag,
                name = animal.name,
                status = animal.status,
            )
        }

    private fun outbox(
        context: LocalCommandContext,
        commandName: String,
        aggregateId: String,
        expectedStreamVersion: Long?,
        payloadJson: String,
    ) = OutboxEntity(
        mutationId = context.mutationId,
        farmId = farmId,
        actorId = context.actorId,
        deviceId = context.deviceId,
        commandName = commandName,
        commandSchemaVersion = 1,
        aggregateType = "animal",
        aggregateId = aggregateId,
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
    )
}
