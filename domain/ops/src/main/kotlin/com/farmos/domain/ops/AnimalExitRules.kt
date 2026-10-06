package com.farmos.domain.ops

/** Owner decision D-022 (resolution R6): how an animal leaves the farm. */
enum class AnimalExitKind(val status: String) {
    DEATH("dead"),
    CULL("culled"),
    SALE("sold"),
}

/** Broad death cause categories. They record what was observed; they are not a diagnosis. */
enum class DeathCause(val label: String) {
    ILLNESS("Illness"),
    INJURY("Injury or accident"),
    PREDATION("Predation"),
    BIRTH_RELATED("Birth-related"),
    UNKNOWN("Unknown"),
    OTHER("Other"),
}

/** One recorded exit or reversal, as the rules need it. [reversesExitId] is set on a reversal only. */
data class AnimalExitEvent(
    val exitId: String,
    val kind: AnimalExitKind?,
    val occurredEpochDay: Long,
    val recordedAtEpochMillis: Long,
    val reversesExitId: String? = null,
)

object AnimalExitRules {
    /** The exit that stands: the latest recorded exit that has not been reversed, or null for an animal still on the farm. */
    fun standing(events: List<AnimalExitEvent>): AnimalExitEvent? {
        val reversed = events.mapNotNull { it.reversesExitId }.toSet()
        return events.filter { it.kind != null && it.exitId !in reversed }
            .maxWithOrNull(compareBy<AnimalExitEvent>({ it.recordedAtEpochMillis }, { it.exitId }))
    }

    /** The lifecycle status the events give an animal: its standing exit's status, or active. */
    fun status(events: List<AnimalExitEvent>): String = standing(events)?.kind?.status ?: "active"

    fun exitError(kind: AnimalExitKind, occurredEpochDay: Long, todayEpochDay: Long, deathCause: String?, reason: String?, buyer: String?, priceMinor: Long?): String? = when {
        occurredEpochDay > todayEpochDay -> "An exit cannot be dated in the future"
        kind == AnimalExitKind.DEATH && DeathCause.entries.none { it.name == deathCause } -> "Choose what the animal died of, or Unknown"
        kind == AnimalExitKind.CULL && reason.isNullOrBlank() -> "Say why the animal is culled"
        kind == AnimalExitKind.SALE && buyer.isNullOrBlank() -> "Enter the buyer"
        priceMinor != null && priceMinor < 0 -> "A price cannot be negative"
        kind != AnimalExitKind.SALE && priceMinor != null -> "Only a sale has a price"
        else -> null
    }

    fun reversalError(reason: String?): String? = if (reason.isNullOrBlank()) "Say why the exit is reversed" else null
}
