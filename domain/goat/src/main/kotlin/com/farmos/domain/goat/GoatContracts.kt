package com.farmos.domain.goat

import kotlinx.serialization.Serializable

@Serializable
data class RegisterGoat(
    val animalId: String,
    val tag: String,
    val name: String? = null,
    val sex: GoatSex,
    val dateOfBirthEpochDay: Long? = null,
)

@Serializable
enum class GoatSex { FEMALE, MALE }

@Serializable
data class RecordGoatWeight(
    val animalId: String,
    val measurementId: String,
    val weightGrams: Long,
    val measuredAtEpochMillis: Long,
)

sealed interface GoatValidationResult {
    data object Valid : GoatValidationResult
    data class Invalid(val code: String, val message: String) : GoatValidationResult
}

object GoatValidator {
    fun register(command: RegisterGoat): GoatValidationResult {
        if (command.tag.isBlank()) return GoatValidationResult.Invalid("TAG_REQUIRED", "Tag is required")
        if (command.tag.length > 64) return GoatValidationResult.Invalid("TAG_TOO_LONG", "Tag must be 64 characters or fewer")
        return GoatValidationResult.Valid
    }

    fun weight(command: RecordGoatWeight): GoatValidationResult {
        if (command.weightGrams <= 0L) return GoatValidationResult.Invalid("WEIGHT_INVALID", "Weight must be greater than zero")
        if (command.weightGrams > 300_000L) return GoatValidationResult.Invalid("WEIGHT_IMPLAUSIBLE", "Weight exceeds the configured goat safety bound")
        return GoatValidationResult.Valid
    }
}

interface GoatRepository {
    suspend fun registerGoat(command: RegisterGoat, context: LocalCommandContext): LocalCommandResult
    suspend fun recordWeight(command: RecordGoatWeight, context: LocalCommandContext): LocalCommandResult
    suspend fun getGoat(animalId: String): GoatSnapshot?
}

data class LocalCommandContext(
    val farmId: String,
    val actorId: String,
    val deviceId: String,
    val mutationId: String,
    val occurredAtEpochMillis: Long,
)

data class LocalCommandResult(
    val mutationId: String,
    val aggregateId: String,
    val locallyDurable: Boolean,
)

data class GoatSnapshot(
    val animalId: String,
    val farmId: String,
    val tag: String,
    val name: String?,
    val sex: GoatSex,
    val latestWeightGrams: Long?,
    val syncPending: Boolean,
)
