package com.farmos.domain.goat

import com.farmos.core.model.LocalCommandContext
import com.farmos.core.model.LocalCommandResult
import com.farmos.core.model.SearchSource
import kotlinx.serialization.SerialName
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
enum class GoatStatus {
    @SerialName("active")
    ACTIVE,

    @SerialName("sold")
    SOLD,

    @SerialName("dead")
    DEAD,

    @SerialName("culled")
    CULLED,

    @SerialName("closed")
    CLOSED,
    ;

    fun wireValue(): String = when (this) {
        ACTIVE -> "active"
        SOLD -> "sold"
        DEAD -> "dead"
        CULLED -> "culled"
        CLOSED -> "closed"
    }

    companion object {
        fun fromWire(value: String): GoatStatus =
            entries.firstOrNull { it.wireValue() == value.lowercase() }
                ?: error("Unknown goat status $value")
    }
}

@Serializable
data class RecordGoatWeight(
    val animalId: String,
    val measurementId: String,
    val weightGrams: Long,
    val measuredAtEpochMillis: Long,
)

@Serializable
data class SetGoatStatus(
    val animalId: String,
    val status: GoatStatus,
)

@Serializable
data class RecordGoatMilk(
    val milkId: String,
    val animalId: String,
    val litresMilli: Long,
    val occurredEpochDay: Long,
)

@Serializable
data class RecordGoatBcs(
    val scoreId: String,
    val animalId: String,
    val scoreTenths: Int,
    val occurredEpochDay: Long,
)

@Serializable
data class RecordGoatScc(
    val recordId: String,
    val animalId: String,
    val cellsPerMl: Int,
    val dimDays: Int? = null,
    val occurredEpochDay: Long,
)

@Serializable
data class RecordGoatFamacha(
    val scoreId: String,
    val animalId: String,
    val score: Int,
    val occurredEpochDay: Long,
)

@Serializable
data class RecordGoatHeat(
    val heatId: String,
    val animalId: String,
    val occurredEpochDay: Long,
)

@Serializable
data class RecordGoatMating(
    val matingId: String,
    val damId: String,
    val sireId: String? = null,
    val method: String,
    val occurredEpochDay: Long,
    val pregCheckTaskId: String,
)

@Serializable
data class RecordGoatPregnancy(
    val checkId: String,
    val animalId: String,
    val result: String,
    val occurredEpochDay: Long,
)

@Serializable
data class PlanGoatLactation(
    val planId: String,
    val animalId: String,
    val kiddingId: String? = null,
    val occurredEpochDay: Long,
    val checkTaskId: String,
)

@Serializable
data class RegisterGoatKid(
    val animalId: String,
    val kiddingId: String,
    val tag: String,
    val name: String? = null,
    val sex: GoatSex,
    val dateOfBirthEpochDay: Long? = null,
    val pedigreeLinkId: String,
)

@Serializable
data class RecordGoatKidding(
    val kiddingId: String,
    val damAnimalId: String,
    val bornCount: Int,
    val liveCount: Int,
    val deadCount: Int,
    val occurredEpochDay: Long,
)

sealed interface GoatValidationResult {
    data object Valid : GoatValidationResult
    data class Invalid(val code: String, val message: String) : GoatValidationResult
}

object GoatValidator {
    fun register(
        command: RegisterGoat,
        todayEpochDay: Long = java.time.LocalDate.now().toEpochDay(),
    ): GoatValidationResult {
        if (command.tag.isBlank()) return GoatValidationResult.Invalid("TAG_REQUIRED", "Tag is required")
        if (command.tag.length > 64) return GoatValidationResult.Invalid("TAG_TOO_LONG", "Tag must be 64 characters or fewer")
        val dateOfBirth = command.dateOfBirthEpochDay
        if (dateOfBirth != null && dateOfBirth < 0L) {
            return GoatValidationResult.Invalid("DOB_INVALID", "Date of birth is not a valid calendar day")
        }
        if (dateOfBirth != null && dateOfBirth > todayEpochDay) {
            return GoatValidationResult.Invalid("DOB_FUTURE", "Date of birth cannot be in the future")
        }
        return GoatValidationResult.Valid
    }

    fun weight(command: RecordGoatWeight): GoatValidationResult {
        if (command.weightGrams <= 0L) return GoatValidationResult.Invalid("WEIGHT_INVALID", "Weight must be greater than zero")
        if (command.weightGrams > 300_000L) return GoatValidationResult.Invalid("WEIGHT_IMPLAUSIBLE", "Weight exceeds the configured goat safety bound")
        return GoatValidationResult.Valid
    }

    fun status(command: SetGoatStatus): GoatValidationResult {
        if (command.status !in setOf(GoatStatus.SOLD, GoatStatus.DEAD, GoatStatus.CULLED)) {
            return GoatValidationResult.Invalid("STATUS_INVALID", "Goat status must be sold, dead, or culled")
        }
        return GoatValidationResult.Valid
    }

    fun milk(command: RecordGoatMilk): GoatValidationResult {
        if (command.litresMilli <= 0L) {
            return GoatValidationResult.Invalid("MILK_INVALID", "Milk record needs litres")
        }
        return GoatValidationResult.Valid
    }

    fun bcs(command: RecordGoatBcs): GoatValidationResult {
        if (command.scoreTenths !in 10..50) {
            return GoatValidationResult.Invalid("BCS_RANGE", "Goat BCS must be 1 to 5")
        }
        return GoatValidationResult.Valid
    }

    fun scc(command: RecordGoatScc): GoatValidationResult {
        if (command.cellsPerMl <= 0) {
            return GoatValidationResult.Invalid("SCC_COUNT", "SCC needs cells per millilitre")
        }
        return GoatValidationResult.Valid
    }

    fun famacha(command: RecordGoatFamacha): GoatValidationResult {
        if (command.score !in 1..5) {
            return GoatValidationResult.Invalid("FAMACHA_RANGE", "FAMACHA score must be 1 to 5")
        }
        return GoatValidationResult.Valid
    }

    fun heat(command: RecordGoatHeat): GoatValidationResult =
        if (command.animalId.isBlank()) GoatValidationResult.Invalid("HEAT_DOE", "Heat needs a doe")
        else GoatValidationResult.Valid

    fun mating(command: RecordGoatMating): GoatValidationResult =
        if (command.method !in setOf("natural", "ai", "hand_mating")) {
            GoatValidationResult.Invalid("MATING_METHOD", "Mating method must be natural, ai, or hand_mating")
        } else {
            GoatValidationResult.Valid
        }

    fun pregnancy(command: RecordGoatPregnancy): GoatValidationResult =
        if (command.result !in setOf("pregnant", "open")) {
            GoatValidationResult.Invalid("PREG_RESULT", "Pregnancy check must be pregnant or open")
        } else {
            GoatValidationResult.Valid
        }

    fun lactation(command: PlanGoatLactation): GoatValidationResult =
        if (command.animalId.isBlank() || command.checkTaskId.isBlank()) {
            GoatValidationResult.Invalid("LACTATION_DOE", "Lactation plan needs a doe")
        } else {
            GoatValidationResult.Valid
        }

    fun kid(command: RegisterGoatKid): GoatValidationResult =
        if (command.tag.isBlank() || command.kiddingId.isBlank()) {
            GoatValidationResult.Invalid("KID_IDENTITY", "Kid record needs a tag and a kidding")
        } else {
            GoatValidationResult.Valid
        }

    fun kidding(command: RecordGoatKidding): GoatValidationResult {
        if (command.bornCount <= 0 || command.liveCount < 0 || command.deadCount < 0) {
            return GoatValidationResult.Invalid("KIDDING_COUNTS", "Kidding counts must add up and be greater than zero")
        }
        if (command.liveCount + command.deadCount != command.bornCount) {
            return GoatValidationResult.Invalid("KIDDING_COUNTS", "Kidding counts must add up and be greater than zero")
        }
        return GoatValidationResult.Valid
    }
}

interface GoatRepository {
    suspend fun registerGoat(command: RegisterGoat, context: LocalCommandContext): LocalCommandResult
    suspend fun recordWeight(command: RecordGoatWeight, context: LocalCommandContext): LocalCommandResult
    suspend fun setStatus(command: SetGoatStatus, context: LocalCommandContext): LocalCommandResult
    suspend fun recordKidding(command: RecordGoatKidding, context: LocalCommandContext): LocalCommandResult
    suspend fun recordFamacha(command: RecordGoatFamacha, context: LocalCommandContext): LocalCommandResult
    suspend fun recordMilk(command: RecordGoatMilk, context: LocalCommandContext): LocalCommandResult
    suspend fun recordBcs(command: RecordGoatBcs, context: LocalCommandContext): LocalCommandResult
    suspend fun recordScc(command: RecordGoatScc, context: LocalCommandContext): LocalCommandResult
    suspend fun recordHeat(command: RecordGoatHeat, context: LocalCommandContext): LocalCommandResult
    suspend fun recordMating(command: RecordGoatMating, context: LocalCommandContext): LocalCommandResult
    suspend fun recordPregnancy(command: RecordGoatPregnancy, context: LocalCommandContext): LocalCommandResult
    suspend fun planLactation(command: PlanGoatLactation, context: LocalCommandContext): LocalCommandResult
    suspend fun registerKid(command: RegisterGoatKid, context: LocalCommandContext): LocalCommandResult
    suspend fun getGoat(animalId: String): GoatSnapshot?
    suspend fun listGoats(limit: Int = 100): List<GoatSnapshot>
    suspend fun searchGoats(query: String, limit: Int = 20): List<GoatSearchResult>
    suspend fun pendingSyncCount(): Long
}

data class GoatSnapshot(
    val animalId: String,
    val farmId: String,
    val tag: String,
    val name: String?,
    val sex: GoatSex,
    val status: GoatStatus = GoatStatus.ACTIVE,
    val dateOfBirthEpochDay: Long? = null,
    val latestWeightGrams: Long?,
    val averageDailyGainGrams: Long? = null,
    val weightHistory: List<WeightSample> = emptyList(),
    val kiddingHistory: List<KiddingSample> = emptyList(),
    val famachaHistory: List<FamachaSample> = emptyList(),
    val milkHistory: List<MilkSample> = emptyList(),
    val bcsHistory: List<BcsSample> = emptyList(),
    val sccHistory: List<SccSample> = emptyList(),
    val syncPending: Boolean,
)

data class SccSample(
    val recordId: String,
    val cellsPerMl: Int,
    val dimDays: Int?,
    val occurredEpochDay: Long,
)

data class BcsSample(
    val scoreId: String,
    val scoreTenths: Int,
    val occurredEpochDay: Long,
)

data class MilkSample(
    val milkId: String,
    val litresMilli: Long,
    val occurredEpochDay: Long,
)

data class FamachaSample(
    val scoreId: String,
    val score: Int,
    val occurredEpochDay: Long,
)

data class KiddingSample(
    val kiddingId: String,
    val bornCount: Int,
    val liveCount: Int,
    val deadCount: Int,
    val occurredEpochDay: Long,
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
