package com.farmos.domain.rabbit

import kotlinx.serialization.Serializable

object KudbatSemiIntensiveExcel {
    const val PACK_ID = "kudbat_semi_intensive_excel"
    const val NEST_IN_DAYS_AFTER_MATING = 28
    const val KINDLING_DAYS_AFTER_MATING = 32
    const val REBREED_DAYS_AFTER_MATING = 43
    const val NEST_OUT_DAYS_AFTER_KINDLING = 21
    const val WEAN_DAYS_AFTER_KINDLING = 35
    const val DOES_PER_CAGE = 11
    const val NEST_BOXES_PER_CAGE = 11

    fun schedule(matingEpochDay: Long): KudbatWaveDates {
        val kindling = matingEpochDay + KINDLING_DAYS_AFTER_MATING
        return KudbatWaveDates(
            matingEpochDay = matingEpochDay,
            nestInEpochDay = matingEpochDay + NEST_IN_DAYS_AFTER_MATING,
            kindlingEpochDay = kindling,
            nestOutEpochDay = kindling + NEST_OUT_DAYS_AFTER_KINDLING,
            rebreedEpochDay = matingEpochDay + REBREED_DAYS_AFTER_MATING,
            weanEpochDay = kindling + WEAN_DAYS_AFTER_KINDLING,
        )
    }

    fun nestBoxesRequired(doeCount: Int): Int = doeCount
}

data class KudbatWaveDates(
    val matingEpochDay: Long,
    val nestInEpochDay: Long,
    val kindlingEpochDay: Long,
    val nestOutEpochDay: Long,
    val rebreedEpochDay: Long,
    val weanEpochDay: Long,
)

@Serializable
data class CreateRabbitCage(
    val cageId: String,
    val code: String,
    val doeCapacity: Int = KudbatSemiIntensiveExcel.DOES_PER_CAGE,
)

@Serializable
data class CreateRabbitNestBox(
    val nestBoxId: String,
    val cageId: String,
    val code: String,
)

@Serializable
data class CreateRabbitWave(
    val waveId: String,
    val cageId: String,
    val doeCount: Int,
    val matingEpochDay: Long,
    val placeTaskId: String,
    val kindlingTaskId: String,
    val removeTaskId: String,
    val rebreedTaskId: String,
    val weanTaskId: String,
)

@Serializable
data class SetRabbitNestBoxStatus(
    val nestBoxId: String,
    val status: String,
)

@Serializable
data class RecordRabbitWean(
    val weanId: String,
    val waveId: String,
    val weanedCount: Int,
    val occurredEpochDay: Long,
)

@Serializable
data class RegisterRabbitKit(
    val kitId: String,
    val waveId: String,
    val tempLabel: String,
    val sex: String = "unknown",
)

@Serializable
data class PromoteRabbitKit(
    val kitId: String,
    val animalId: String,
    val tag: String,
    val sex: String? = null,
)

@Serializable
data class DecideRabbitRetention(
    val decisionId: String,
    val kitId: String,
    val decision: String,
    val occurredEpochDay: Long,
)

@Serializable
data class EnqueueRabbitWaitlist(
    val waitlistId: String,
    val contactName: String,
    val desiredSex: String? = null,
    val qty: Int,
)

@Serializable
data class FulfillRabbitWaitlist(
    val waitlistId: String,
    val kitId: String,
)

@Serializable
data class AgreeRabbitContract(
    val contractId: String,
    val waitlistId: String? = null,
    val buyerName: String,
    val animalId: String? = null,
    val amountMinor: Long,
    val currency: String = "USD",
    val occurredEpochDay: Long,
)

@Serializable
data class RecordRabbitGiStasis(
    val flagId: String,
    val animalId: String,
    val signs: String,
    val occurredEpochDay: Long,
    val taskId: String,
)

@Serializable
data class BindRabbitBedding(
    val beddingItemId: String? = null,
    val beddingQtyMilli: Long = 1000,
    val feedItemId: String? = null,
)

@Serializable
data class RecordRabbitMatingOutcome(
    val outcomeId: String,
    val waveId: String,
    val outcome: String,
    val occurredEpochDay: Long,
)

@Serializable
data class RecordRabbitMarketPlan(
    val planId: String,
    val kitId: String? = null,
    val waveId: String? = null,
    val targetWeightGrams: Int,
    val targetEpochDay: Long,
    val purpose: String,
)

object RabbitProgrammeValidator {
    fun cage(command: CreateRabbitCage): String? =
        if (command.code.isBlank()) "Cage code is required" else null

    fun nestBox(command: CreateRabbitNestBox): String? =
        if (command.code.isBlank()) "Nest box code is required" else null

    fun wave(command: CreateRabbitWave, availableBoxes: Long): String? {
        if (command.doeCount <= 0) return "Doe count is required"
        if (availableBoxes < command.doeCount) {
            return "Nest boxes must at least match the doe count for this wave"
        }
        return null
    }

    fun nestStatus(command: SetRabbitNestBoxStatus): String? =
        if (command.status !in setOf("assigned", "in_cage", "dirty", "sanitized", "available")) {
            "Nest box status is not allowed"
        } else {
            null
        }

    fun wean(command: RecordRabbitWean): String? =
        if (command.weanedCount < 0) "Wean needs a kit count" else null

    fun kit(command: RegisterRabbitKit): String? =
        if (command.tempLabel.isBlank() || command.sex !in setOf("male", "female", "unknown")) {
            "Kit needs a wave and a temporary label"
        } else {
            null
        }

    fun promote(command: PromoteRabbitKit): String? =
        if (command.tag.isBlank()) "Promote needs an ear tag" else null

    fun retention(command: DecideRabbitRetention): String? =
        if (command.decision !in setOf("keep_breeder", "grow_meat", "sale_pet", "cull", "undecided")) {
            "Retention needs a listed decision"
        } else {
            null
        }

    fun waitlist(command: EnqueueRabbitWaitlist): String? =
        if (command.contactName.isBlank() || command.qty <= 0) "Waitlist needs a buyer name and quantity" else null

    fun contract(command: AgreeRabbitContract): String? =
        if (command.buyerName.isBlank() || command.amountMinor <= 0L) "Contract needs a buyer and amount" else null

    fun plan(command: RecordRabbitMarketPlan): String? =
        if (command.targetWeightGrams <= 0 || command.purpose !in setOf("meat", "pet_sale", "show")) {
            "Market plan needs a target weight and purpose"
        } else {
            null
        }

    fun giStasis(command: RecordRabbitGiStasis): String? =
        if (command.signs.isBlank()) "GI stasis flag needs signs" else null

    fun bedding(command: BindRabbitBedding): String? =
        if (command.beddingQtyMilli <= 0L) "Bedding bind needs a quantity in milli-units" else null

    fun matingOutcome(command: RecordRabbitMatingOutcome): String? =
        if (command.outcome !in setOf("false_pregnancy", "open", "pregnant", "kindled")) {
            "Mating outcome must be false_pregnancy, open, pregnant, or kindled"
        } else {
            null
        }
}
