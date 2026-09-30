package com.farmos.data.herd

import androidx.room.withTransaction
import com.farmos.core.database.AnimalExitEntity
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.journalLocalOperation
import com.farmos.core.model.LocalCommandContext
import com.farmos.core.model.LocalCommandResult
import com.farmos.domain.ops.AnimalExitEvent
import com.farmos.domain.ops.AnimalExitKind
import com.farmos.domain.ops.AnimalExitRules
import com.farmos.domain.ops.FarmCurrency
import com.farmos.domain.ops.RecordAnimalExit
import com.farmos.domain.ops.ReverseAnimalExit
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Animal exits (owner decision D-022, resolution R6): death, cull and sale are append-only events and a
 * mistake is corrected by a reversal event; no record is changed or deleted. The animal's status is its
 * standing exit, or active. Journalled for farm replication; a conflicting exit recorded on another device
 * offline fails to apply and surfaces for review.
 */
class AnimalExitCommands(
    private val database: FarmOsDatabase,
    private val farmId: String,
    /** Replays an operation received from another device: domain writes only, it is already journalled. */
    private val replaying: Boolean = false,
    private val json: Json = Json { encodeDefaults = true },
) {
    suspend fun record(command: RecordAnimalExit, context: LocalCommandContext): LocalCommandResult {
        val kind = AnimalExitKind.entries.firstOrNull { it.name == command.kind } ?: error("Choose death, cull or sale")
        // "Today" from the business time in UTC plus one day, so a device whose local date is ahead of UTC is not refused.
        val today = Instant.ofEpochMilli(context.occurredAtEpochMillis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() + 1
        AnimalExitRules.exitError(kind, command.occurredEpochDay, today, command.deathCause, command.reason, command.buyer, command.priceMinor)?.let { error(it) }
        if (command.priceMinor != null) require(command.currency != null && FarmCurrency.isRecordable(command.currency)) { "A price needs the farm currency" }
        val animal = requireNotNull(database.animals().get(farmId, command.animalId)) { "Animal not found" }
        require(animal.status == "active") { "Only an animal still on the farm can leave it" }
        journal(context, RECORD, command.animalId, json.encodeToString(command)) {
            database.animalExits().insert(
                AnimalExitEntity(
                    command.exitId, farmId, command.animalId, kind.name, command.occurredEpochDay, command.deathCause,
                    command.reason?.trim(), command.buyer?.trim(), command.priceMinor, command.currency, null, context.actorId, context.occurredAtEpochMillis,
                ),
            )
            project(command.animalId, context.occurredAtEpochMillis)
        }
        return LocalCommandResult(context.mutationId, command.exitId, true)
    }

    suspend fun reverse(command: ReverseAnimalExit, context: LocalCommandContext): LocalCommandResult {
        AnimalExitRules.reversalError(command.reason)?.let { error(it) }
        val standing = AnimalExitRules.standing(events(command.animalId))
        require(standing?.exitId == command.exitId) { "Only the animal's current exit can be reversed" }
        journal(context, REVERSE, command.animalId, json.encodeToString(command)) {
            database.animalExits().insert(
                AnimalExitEntity(
                    command.reversalId, farmId, command.animalId, REVERSAL, command.occurredEpochDay, null,
                    command.reason.trim(), null, null, null, command.exitId, context.actorId, context.occurredAtEpochMillis,
                ),
            )
            project(command.animalId, context.occurredAtEpochMillis)
        }
        return LocalCommandResult(context.mutationId, command.reversalId, true)
    }

    private suspend fun events(animalId: String): List<AnimalExitEvent> = database.animalExits().forAnimal(farmId, animalId).map {
        AnimalExitEvent(it.id, AnimalExitKind.entries.firstOrNull { kind -> kind.name == it.kind }, it.occurredEpochDay, it.recordedAtEpochMillis, it.reversesExitId)
    }

    /** The animal's status follows its standing exit. */
    private suspend fun project(animalId: String, at: Long) {
        database.animals().updateStatus(farmId, animalId, AnimalExitRules.status(events(animalId)), at)
    }

    private suspend fun journal(context: LocalCommandContext, commandName: String, animalId: String, payloadJson: String, localWrite: suspend () -> Unit) {
        require(context.farmId == farmId) { "Farm context mismatch" }
        if (replaying) return database.withTransaction { localWrite() }
        database.withTransaction {
            localWrite()
            database.journalLocalOperation(
                operationId = context.mutationId,
                farmId = farmId,
                entityType = "animal",
                entityId = animalId,
                actorId = context.actorId,
                deviceId = context.deviceId,
                businessTimeEpochMillis = context.occurredAtEpochMillis,
                createdAtEpochMillis = System.currentTimeMillis(),
                baseVersion = null,
                operationType = commandName,
                payloadJson = payloadJson,
                schemaVersion = 1,
            )
        }
    }

    companion object {
        const val RECORD = "animal.exit_record.v1"
        const val REVERSE = "animal.exit_reverse.v1"
        private const val REVERSAL = "REVERSAL"
    }
}
