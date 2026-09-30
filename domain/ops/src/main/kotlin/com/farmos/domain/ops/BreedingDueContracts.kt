package com.farmos.domain.ops

import kotlinx.serialization.Serializable

/**
 * A cattle service that carries its expected calving day, set on the recording device from the farm's
 * own cattle gestation period (owner decision D-019). Replicated as `cattle.record_service.v2`; every
 * device replays the carried day, so the generated calving work is the same everywhere whatever the
 * receiving device's settings were at the time.
 */
@Serializable
data class RecordCattleServiceV2(
    val serviceId: String,
    val animalId: String,
    val method: String,
    val occurredEpochDay: Long,
    val expectedCalvingEpochDay: Long,
    val pdTaskId: String,
    val paddockTaskId: String,
    val calvingTaskId: String,
)

/** A sheep joining that carries its expected lambing start, from the farm's sheep gestation period. */
@Serializable
data class RecordSheepJoiningV2(
    val joiningId: String,
    val groupId: String,
    val startedEpochDay: Long,
    val expectedLambingEpochDay: Long,
    val scanTaskId: String,
    val preLambTaskId: String,
    val paddockTaskId: String,
    val lambingTaskId: String,
)

object BreedingDueSchedule {
    /** Pregnancy diagnosis after a cattle service; not gestation-dependent. */
    const val CATTLE_PD_DAYS_AFTER_SERVICE = 32L

    /** Calving paddock / close-up pen before the expected calving. */
    const val CATTLE_PADDOCK_DAYS_BEFORE_CALVING = 21L

    /** Pregnancy scanning after ram-in; not gestation-dependent. */
    const val SHEEP_SCAN_DAYS_AFTER_JOINING = 70L

    /** Pre-lambing vaccination / nutrition and lambing paddock set-up before the expected lambing start. */
    const val SHEEP_PRE_LAMB_DAYS_BEFORE_LAMBING = 7L

    /** The fixed days the v1 commands used, kept so their operations replay exactly as recorded. */
    const val V1_CATTLE_CALVING_DAYS = 280L
    const val V1_SHEEP_LAMBING_DAYS = 147L

    private fun dueAfter(startEpochDay: Long, dueEpochDay: Long) = dueEpochDay - startEpochDay in 1..GestationPeriod.MAX_DAYS.toLong()

    fun cattleService(command: RecordCattleServiceV2): String? = when {
        command.method !in setOf("ai", "natural", "et") -> "Cattle service must be ai, natural, or et"
        !dueAfter(command.occurredEpochDay, command.expectedCalvingEpochDay) -> "Expected calving must fall after the service, within ${GestationPeriod.MAX_DAYS} days"
        else -> null
    }

    fun joining(command: RecordSheepJoiningV2): String? = when {
        command.groupId.isBlank() -> "Joining needs a sheep mob"
        !dueAfter(command.startedEpochDay, command.expectedLambingEpochDay) -> "Expected lambing must fall after ram-in, within ${GestationPeriod.MAX_DAYS} days"
        else -> null
    }
}
