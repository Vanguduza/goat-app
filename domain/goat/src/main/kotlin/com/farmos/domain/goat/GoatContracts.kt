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
    suspend fun listGoats(limit: Int = 100): List<GoatSnapshot>
    suspend fun searchGoats(query: String, limit: Int = 20): List<GoatSearchResult>
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
    val status: String = "active",
    val dateOfBirthEpochDay: Long? = null,
    val latestWeightGrams: Long?,
    val averageDailyGainGrams: Long? = null,
    val weightHistory: List<WeightSample> = emptyList(),
    val syncPending: Boolean,
)

data class WeightSample(
    val measurementId: String,
    val weightGrams: Long,
    val measuredAtEpochMillis: Long,
)

object GoatGrowth {
    private const val MILLIS_PER_DAY = 86_400_000L

    fun averageDailyGainGrams(samples: List<WeightSample>): Long? {
        val ordered = samples.sortedBy { it.measuredAtEpochMillis }
        if (ordered.size < 2) return null
        val deltaMillis = ordered.last().measuredAtEpochMillis - ordered.first().measuredAtEpochMillis
        if (deltaMillis <= 0L) return null
        val deltaGrams = ordered.last().weightGrams - ordered.first().weightGrams
        return (deltaGrams * MILLIS_PER_DAY) / deltaMillis
    }
}

data class GoatSearchResult(
    val animalId: String,
    val tag: String,
    val name: String?,
    val status: String,
    val source: SearchSource = SearchSource.LOCAL,
)

enum class SearchSource { LOCAL, MEILISEARCH }
