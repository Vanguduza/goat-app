package com.farmos.data.goat

import androidx.room.withTransaction
import com.farmos.core.database.AnimalEntity
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.FamachaScoreEntity
import com.farmos.core.database.GoatMilkEntity
import com.farmos.core.database.KiddingEntity
import com.farmos.core.database.MeasurementEntity
import com.farmos.core.database.OutboxEntity
import com.farmos.core.model.SyncState
import com.farmos.domain.goat.GoatGrowth
import com.farmos.domain.goat.GoatRepository
import com.farmos.domain.goat.GoatSearchResult
import com.farmos.domain.goat.GoatSex
import com.farmos.domain.goat.GoatSnapshot
import com.farmos.domain.goat.GoatValidationResult
import com.farmos.domain.goat.GoatValidator
import com.farmos.core.model.LocalCommandContext
import com.farmos.core.model.LocalCommandResult
import com.farmos.domain.goat.FamachaSample
import com.farmos.domain.goat.GoatStatus
import com.farmos.domain.goat.KiddingSample
import com.farmos.domain.goat.BcsSample
import com.farmos.domain.goat.MilkSample
import com.farmos.domain.goat.RecordGoatBcs
import com.farmos.domain.goat.RecordGoatScc
import com.farmos.domain.goat.RecordGoatHeat
import com.farmos.domain.goat.RecordGoatMating
import com.farmos.domain.goat.RecordGoatPregnancy
import com.farmos.domain.goat.PlanGoatLactation
import com.farmos.domain.goat.RegisterGoatKid
import com.farmos.domain.goat.SccSample
import com.farmos.domain.goat.RecordGoatFamacha
import com.farmos.domain.goat.RecordGoatKidding
import com.farmos.domain.goat.RecordGoatMilk
import com.farmos.domain.goat.RecordGoatWeight
import com.farmos.domain.goat.RegisterGoat
import com.farmos.domain.goat.SetGoatStatus
import com.farmos.domain.goat.WeightSample
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
            val aggregateOrdinal = database.outbox().nextAggregateOrdinal(
                farmId = farmId,
                aggregateType = ANIMAL_AGGREGATE,
                aggregateId = command.animalId,
            )
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
                    aggregateOrdinal = aggregateOrdinal,
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
        val animal = requireNotNull(database.animals().get(farmId, command.animalId)) { "Goat not found" }
        require(animal.status == GoatStatus.ACTIVE.wireValue()) {
            "Only an active goat can take a new weight"
        }

        database.withTransaction {
            val aggregateOrdinal = database.outbox().nextAggregateOrdinal(
                farmId = farmId,
                aggregateType = ANIMAL_AGGREGATE,
                aggregateId = command.animalId,
            )
            val expectedStreamVersion = nextExpectedStreamVersion(command.animalId)

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
                    aggregateOrdinal = aggregateOrdinal,
                    expectedStreamVersion = expectedStreamVersion,
                    payloadJson = json.encodeToString(command),
                ),
            )
        }
        return LocalCommandResult(context.mutationId, command.animalId, locallyDurable = true)
    }

    override suspend fun setStatus(command: SetGoatStatus, context: LocalCommandContext): LocalCommandResult {
        require(context.farmId == farmId) { "Farm context mismatch" }
        val validation = GoatValidator.status(command)
        require(validation is GoatValidationResult.Valid) {
            (validation as GoatValidationResult.Invalid).message
        }
        val animal = requireNotNull(database.animals().get(farmId, command.animalId)) { "Goat not found" }
        require(animal.status == GoatStatus.ACTIVE.wireValue()) {
            "Only an active goat can change lifecycle status"
        }

        database.withTransaction {
            val aggregateOrdinal = database.outbox().nextAggregateOrdinal(
                farmId = farmId,
                aggregateType = ANIMAL_AGGREGATE,
                aggregateId = command.animalId,
            )
            database.animals().updateStatus(
                farmId = farmId,
                animalId = command.animalId,
                status = command.status.wireValue(),
                updatedAtEpochMillis = context.occurredAtEpochMillis,
            )
            database.outbox().insert(
                outbox(
                    context = context,
                    commandName = "goat.set_status.v1",
                    aggregateId = command.animalId,
                    aggregateOrdinal = aggregateOrdinal,
                    expectedStreamVersion = nextExpectedStreamVersion(command.animalId),
                    payloadJson = json.encodeToString(command),
                ),
            )
        }
        return LocalCommandResult(context.mutationId, command.animalId, locallyDurable = true)
    }

    override suspend fun recordKidding(command: RecordGoatKidding, context: LocalCommandContext): LocalCommandResult {
        require(context.farmId == farmId) { "Farm context mismatch" }
        val validation = GoatValidator.kidding(command)
        require(validation is GoatValidationResult.Valid) {
            (validation as GoatValidationResult.Invalid).message
        }
        val dam = requireNotNull(database.animals().get(farmId, command.damAnimalId)) { "Doe not found" }
        require(dam.speciesCode == "goat" && dam.sex == GoatSex.FEMALE.name && dam.status == GoatStatus.ACTIVE.wireValue()) {
            "Active doe not found"
        }

        database.withTransaction {
            val aggregateOrdinal = database.outbox().nextAggregateOrdinal(
                farmId = farmId,
                aggregateType = ANIMAL_AGGREGATE,
                aggregateId = command.damAnimalId,
            )
            database.kidding().insert(
                KiddingEntity(
                    id = command.kiddingId,
                    farmId = farmId,
                    damId = command.damAnimalId,
                    bornCount = command.bornCount,
                    liveCount = command.liveCount,
                    deadCount = command.deadCount,
                    occurredEpochDay = command.occurredEpochDay,
                ),
            )
            database.outbox().insert(
                outbox(
                    context = context,
                    commandName = "goat.record_kidding.v1",
                    aggregateId = command.damAnimalId,
                    aggregateOrdinal = aggregateOrdinal,
                    expectedStreamVersion = nextExpectedStreamVersion(command.damAnimalId),
                    payloadJson = json.encodeToString(command),
                ),
            )
        }
        return LocalCommandResult(context.mutationId, command.damAnimalId, locallyDurable = true)
    }

    override suspend fun recordFamacha(command: RecordGoatFamacha, context: LocalCommandContext): LocalCommandResult {
        require(context.farmId == farmId) { "Farm context mismatch" }
        val validation = GoatValidator.famacha(command)
        require(validation is GoatValidationResult.Valid) {
            (validation as GoatValidationResult.Invalid).message
        }
        val goat = requireNotNull(database.animals().get(farmId, command.animalId)) { "Goat not found" }
        require(goat.speciesCode == "goat" && goat.status == GoatStatus.ACTIVE.wireValue()) {
            "Active goat not found"
        }
        database.withTransaction {
            val aggregateOrdinal = database.outbox().nextAggregateOrdinal(
                farmId = farmId,
                aggregateType = ANIMAL_AGGREGATE,
                aggregateId = command.animalId,
            )
            database.famacha().insert(
                FamachaScoreEntity(
                    id = command.scoreId,
                    farmId = farmId,
                    animalId = command.animalId,
                    score = command.score,
                    occurredEpochDay = command.occurredEpochDay,
                ),
            )
            database.outbox().insert(
                outbox(
                    context = context,
                    commandName = "goat.record_famacha.v1",
                    aggregateId = command.animalId,
                    aggregateOrdinal = aggregateOrdinal,
                    expectedStreamVersion = nextExpectedStreamVersion(command.animalId),
                    payloadJson = json.encodeToString(command),
                ),
            )
        }
        return LocalCommandResult(context.mutationId, command.animalId, locallyDurable = true)
    }

    override suspend fun recordMilk(command: RecordGoatMilk, context: LocalCommandContext): LocalCommandResult {
        require(context.farmId == farmId) { "Farm context mismatch" }
        val validation = GoatValidator.milk(command)
        require(validation is GoatValidationResult.Valid) {
            (validation as GoatValidationResult.Invalid).message
        }
        val goat = requireNotNull(database.animals().get(farmId, command.animalId)) { "Active doe not found" }
        require(goat.speciesCode == "goat" && goat.sex == GoatSex.FEMALE.name && goat.status == GoatStatus.ACTIVE.wireValue()) {
            "Active doe not found"
        }
        database.withTransaction {
            val aggregateOrdinal = database.outbox().nextAggregateOrdinal(
                farmId = farmId,
                aggregateType = ANIMAL_AGGREGATE,
                aggregateId = command.animalId,
            )
            database.lifecycle().insertMilk(
                GoatMilkEntity(
                    id = command.milkId,
                    farmId = farmId,
                    animalId = command.animalId,
                    litresMilli = command.litresMilli,
                    occurredEpochDay = command.occurredEpochDay,
                ),
            )
            database.outbox().insert(
                outbox(
                    context = context,
                    commandName = "goat.record_milk.v1",
                    aggregateId = command.animalId,
                    aggregateOrdinal = aggregateOrdinal,
                    expectedStreamVersion = nextExpectedStreamVersion(command.animalId),
                    payloadJson = json.encodeToString(command),
                ),
            )
        }
        return LocalCommandResult(context.mutationId, command.animalId, locallyDurable = true)
    }

    override suspend fun recordBcs(command: RecordGoatBcs, context: LocalCommandContext): LocalCommandResult {
        require(context.farmId == farmId) { "Farm context mismatch" }
        val validation = GoatValidator.bcs(command)
        require(validation is GoatValidationResult.Valid) {
            (validation as GoatValidationResult.Invalid).message
        }
        val goat = requireNotNull(database.animals().get(farmId, command.animalId)) { "Goat not found" }
        require(goat.speciesCode == "goat") { "Goat not found" }
        database.withTransaction {
            val aggregateOrdinal = database.outbox().nextAggregateOrdinal(
                farmId = farmId,
                aggregateType = ANIMAL_AGGREGATE,
                aggregateId = command.animalId,
            )
            database.lifecycle().insertGoatBcs(
                com.farmos.core.database.GoatBcsEntity(
                    command.scoreId, farmId, command.animalId, command.scoreTenths, command.occurredEpochDay,
                ),
            )
            database.outbox().insert(
                outbox(
                    context = context,
                    commandName = "goat.record_bcs.v1",
                    aggregateId = command.animalId,
                    aggregateOrdinal = aggregateOrdinal,
                    expectedStreamVersion = nextExpectedStreamVersion(command.animalId),
                    payloadJson = json.encodeToString(command),
                ),
            )
        }
        return LocalCommandResult(context.mutationId, command.animalId, locallyDurable = true)
    }

    override suspend fun recordScc(command: RecordGoatScc, context: LocalCommandContext): LocalCommandResult {
        require(context.farmId == farmId) { "Farm context mismatch" }
        val validation = GoatValidator.scc(command)
        require(validation is GoatValidationResult.Valid) {
            (validation as GoatValidationResult.Invalid).message
        }
        val goat = requireNotNull(database.animals().get(farmId, command.animalId)) { "Goat not found" }
        require(goat.speciesCode == "goat") { "Goat not found" }
        database.withTransaction {
            val aggregateOrdinal = database.outbox().nextAggregateOrdinal(
                farmId = farmId,
                aggregateType = ANIMAL_AGGREGATE,
                aggregateId = command.animalId,
            )
            database.lifecycle().insertGoatScc(
                com.farmos.core.database.GoatSccEntity(
                    command.recordId, farmId, command.animalId, command.cellsPerMl, command.dimDays, command.occurredEpochDay,
                ),
            )
            database.outbox().insert(
                outbox(
                    context = context,
                    commandName = "goat.record_scc.v1",
                    aggregateId = command.animalId,
                    aggregateOrdinal = aggregateOrdinal,
                    expectedStreamVersion = nextExpectedStreamVersion(command.animalId),
                    payloadJson = json.encodeToString(command),
                ),
            )
        }
        return LocalCommandResult(context.mutationId, command.animalId, locallyDurable = true)
    }

    override suspend fun recordHeat(command: RecordGoatHeat, context: LocalCommandContext): LocalCommandResult {
        require(context.farmId == farmId) { "Farm context mismatch" }
        val validation = GoatValidator.heat(command)
        require(validation is GoatValidationResult.Valid) { (validation as GoatValidationResult.Invalid).message }
        val goat = requireNotNull(database.animals().get(farmId, command.animalId)) { "Heat needs a doe" }
        require(goat.speciesCode == "goat" && goat.sex == "FEMALE") { "Heat needs a doe" }
        database.withTransaction {
            val aggregateOrdinal = database.outbox().nextAggregateOrdinal(farmId, ANIMAL_AGGREGATE, command.animalId)
            database.lifecycle().insertHeat(com.farmos.core.database.GoatHeatEntity(command.heatId, farmId, command.animalId, command.occurredEpochDay))
            database.outbox().insert(outbox(context, "goat.record_heat.v1", command.animalId, aggregateOrdinal, nextExpectedStreamVersion(command.animalId), json.encodeToString(command)))
        }
        return LocalCommandResult(context.mutationId, command.animalId, locallyDurable = true)
    }

    override suspend fun recordMating(command: RecordGoatMating, context: LocalCommandContext): LocalCommandResult {
        require(context.farmId == farmId) { "Farm context mismatch" }
        val validation = GoatValidator.mating(command)
        require(validation is GoatValidationResult.Valid) { (validation as GoatValidationResult.Invalid).message }
        val doe = requireNotNull(database.animals().get(farmId, command.damId)) { "Mating needs a doe" }
        require(doe.speciesCode == "goat" && doe.sex == "FEMALE") { "Mating needs a doe" }
        database.withTransaction {
            val aggregateOrdinal = database.outbox().nextAggregateOrdinal(farmId, ANIMAL_AGGREGATE, command.damId)
            database.lifecycle().insertMating(com.farmos.core.database.GoatMatingEntity(command.matingId, farmId, command.damId, command.sireId, command.method, command.occurredEpochDay))
            database.tasks().insert(com.farmos.core.database.TaskEntity(command.pregCheckTaskId, farmId, "goat", "PREG_CHECK", "Pregnancy check", command.occurredEpochDay + 45, "open", command.damId, null, null, context.occurredAtEpochMillis))
            database.outbox().insert(outbox(context, "goat.record_mating.v1", command.damId, aggregateOrdinal, nextExpectedStreamVersion(command.damId), json.encodeToString(command)))
        }
        return LocalCommandResult(context.mutationId, command.damId, locallyDurable = true)
    }

    override suspend fun recordPregnancy(command: RecordGoatPregnancy, context: LocalCommandContext): LocalCommandResult {
        require(context.farmId == farmId) { "Farm context mismatch" }
        val validation = GoatValidator.pregnancy(command)
        require(validation is GoatValidationResult.Valid) { (validation as GoatValidationResult.Invalid).message }
        requireNotNull(database.animals().get(farmId, command.animalId)) { "Pregnancy check needs a goat" }
        database.withTransaction {
            val aggregateOrdinal = database.outbox().nextAggregateOrdinal(farmId, ANIMAL_AGGREGATE, command.animalId)
            database.lifecycle().insertPregnancy(com.farmos.core.database.GoatPregnancyEntity(command.checkId, farmId, command.animalId, command.result, command.occurredEpochDay))
            database.outbox().insert(outbox(context, "goat.record_pregnancy.v1", command.animalId, aggregateOrdinal, nextExpectedStreamVersion(command.animalId), json.encodeToString(command)))
        }
        return LocalCommandResult(context.mutationId, command.animalId, locallyDurable = true)
    }

    override suspend fun planLactation(command: PlanGoatLactation, context: LocalCommandContext): LocalCommandResult {
        require(context.farmId == farmId) { "Farm context mismatch" }
        val validation = GoatValidator.lactation(command)
        require(validation is GoatValidationResult.Valid) { (validation as GoatValidationResult.Invalid).message }
        val doe = requireNotNull(database.animals().get(farmId, command.animalId)) { "Lactation plan needs a doe" }
        require(doe.speciesCode == "goat" && doe.sex == "FEMALE") { "Lactation plan needs a doe" }
        database.withTransaction {
            val aggregateOrdinal = database.outbox().nextAggregateOrdinal(farmId, ANIMAL_AGGREGATE, command.animalId)
            database.lifecycle().insertLactation(
                com.farmos.core.database.GoatLactationPlanEntity(command.planId, farmId, command.animalId, command.kiddingId, command.occurredEpochDay),
            )
            database.tasks().insert(
                com.farmos.core.database.TaskEntity(
                    command.checkTaskId, farmId, "goat", "LACTATION_CHECK", "Lactation follow-up",
                    command.occurredEpochDay + 7, "open", command.animalId, null, null, context.occurredAtEpochMillis,
                ),
            )
            database.outbox().insert(outbox(context, "goat.plan_lactation.v1", command.animalId, aggregateOrdinal, nextExpectedStreamVersion(command.animalId), json.encodeToString(command)))
        }
        return LocalCommandResult(context.mutationId, command.animalId, locallyDurable = true)
    }

    override suspend fun registerKid(command: RegisterGoatKid, context: LocalCommandContext): LocalCommandResult {
        require(context.farmId == farmId) { "Farm context mismatch" }
        val validation = GoatValidator.kid(command)
        require(validation is GoatValidationResult.Valid) { (validation as GoatValidationResult.Invalid).message }
        val kidding = requireNotNull(database.kidding().get(farmId, command.kiddingId)) { "Kid record needs a kidding on this farm" }
        require(database.lifecycle().kidCount(farmId, command.kiddingId) < kidding.liveCount) {
            "Kid records cannot exceed live kids from that kidding"
        }
        database.withTransaction {
            val aggregateOrdinal = database.outbox().nextAggregateOrdinal(farmId, ANIMAL_AGGREGATE, command.animalId)
            database.animals().insert(
                AnimalEntity(
                    id = command.animalId,
                    farmId = farmId,
                    tag = command.tag.trim(),
                    name = command.name?.trim()?.takeIf { it.isNotEmpty() },
                    speciesCode = "goat",
                    sex = command.sex.name,
                    status = "active",
                    dateOfBirthEpochDay = command.dateOfBirthEpochDay ?: kidding.occurredEpochDay,
                    updatedAtEpochMillis = context.occurredAtEpochMillis,
                ),
            )
            database.lifecycle().insertKid(
                com.farmos.core.database.GoatKidEntity(command.animalId, farmId, command.animalId, command.kiddingId, kidding.damId),
            )
            database.lifecycle().insertPedigree(
                com.farmos.core.database.PedigreeRelationEntity(command.pedigreeLinkId, farmId, command.animalId, kidding.damId, "dam"),
            )
            database.outbox().insert(outbox(context, "goat.register_kid.v1", command.animalId, aggregateOrdinal, 0, json.encodeToString(command)))
        }
        return LocalCommandResult(context.mutationId, command.animalId, locallyDurable = true)
    }

    override suspend fun getGoat(animalId: String): GoatSnapshot? {
        val animal = database.animals().get(farmId, animalId) ?: return null
        val history = database.measurements().history(farmId, animalId, "weight").map { measurement ->
            WeightSample(
                measurementId = measurement.id,
                weightGrams = measurement.valueLong,
                measuredAtEpochMillis = measurement.measuredAtEpochMillis,
            )
        }
        val kiddingHistory = database.kidding().forDam(farmId, animalId).map { event ->
            KiddingSample(
                kiddingId = event.id,
                bornCount = event.bornCount,
                liveCount = event.liveCount,
                deadCount = event.deadCount,
                occurredEpochDay = event.occurredEpochDay,
            )
        }
        return GoatSnapshot(
            animalId = animal.id,
            farmId = animal.farmId,
            tag = animal.tag,
            name = animal.name,
            sex = GoatSex.valueOf(animal.sex),
            status = GoatStatus.fromWire(animal.status),
            dateOfBirthEpochDay = animal.dateOfBirthEpochDay,
            latestWeightGrams = history.lastOrNull()?.weightGrams,
            averageDailyGainGrams = GoatGrowth.averageDailyGainGrams(history),
            weightHistory = history,
            kiddingHistory = kiddingHistory,
            famachaHistory = database.famacha().forAnimal(farmId, animalId).map { score ->
                FamachaSample(score.id, score.score, score.occurredEpochDay)
            },
            milkHistory = database.lifecycle().milkFor(farmId, animalId).map { row ->
                MilkSample(row.id, row.litresMilli, row.occurredEpochDay)
            },
            bcsHistory = database.lifecycle().goatBcsFor(farmId, animalId).map { row ->
                BcsSample(row.id, row.scoreTenths, row.occurredEpochDay)
            },
            sccHistory = database.lifecycle().goatSccFor(farmId, animalId).map { row ->
                SccSample(row.id, row.cellsPerMl, row.dimDays, row.occurredEpochDay)
            },
            syncPending = database.outbox().hasPending(farmId, animalId),
        )
    }

    override suspend fun listGoats(limit: Int): List<GoatSnapshot> =
        database.animals().listBySpecies(
            farmId = farmId,
            speciesCode = "goat",
            limit = limit.coerceIn(1, 500),
        ).mapNotNull { getGoat(it.id) }

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

    override suspend fun pendingSyncCount(): Long =
        database.outbox().countUnacknowledgedForFarm(farmId)

    private suspend fun nextExpectedStreamVersion(animalId: String): Long {
        val authoritativeVersion = database.aggregateVersions().getVersion(
            farmId = farmId,
            aggregateType = ANIMAL_AGGREGATE,
            aggregateId = animalId,
        ) ?: 0L
        val queuedVersionAdvances = database.outbox().countUnacknowledgedForAggregate(
            farmId = farmId,
            aggregateType = ANIMAL_AGGREGATE,
            aggregateId = animalId,
        )
        return authoritativeVersion + queuedVersionAdvances
    }

    private fun outbox(
        context: LocalCommandContext,
        commandName: String,
        aggregateId: String,
        aggregateOrdinal: Long,
        expectedStreamVersion: Long?,
        payloadJson: String,
    ) = OutboxEntity(
        mutationId = context.mutationId,
        farmId = farmId,
        actorId = context.actorId,
        deviceId = context.deviceId,
        commandName = commandName,
        commandSchemaVersion = 1,
        aggregateType = ANIMAL_AGGREGATE,
        aggregateId = aggregateId,
        aggregateOrdinal = aggregateOrdinal,
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

    companion object {
        private const val ANIMAL_AGGREGATE = "animal"
    }
}
