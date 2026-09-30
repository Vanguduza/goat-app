package com.farmos.domain.ops

/** Species whose pregnancies the farm tracks to a due date. Poultry incubation is a hatch contract instead. */
enum class GestationSpecies(val code: String) {
    GOAT("goat"),
    SHEEP("sheep"),
    CATTLE("cattle"),
    RABBIT("rabbit"),
    ;

    companion object {
        fun of(code: String): GestationSpecies? = entries.firstOrNull { it.code == code }
    }
}

/** A gestation length in days: the earliest and latest expected birth around the typical length. */
data class GestationPeriod(val earliestDays: Int, val typicalDays: Int, val latestDays: Int) {
    init {
        require(earliestDays in 1..typicalDays) { "The earliest day must be between 1 and the typical length" }
        require(latestDays in typicalDays..MAX_DAYS) { "The latest day must be between the typical length and $MAX_DAYS" }
    }

    companion object {
        const val MAX_DAYS = 400
    }
}

/**
 * Owner decision D-019: typed gestation defaults held in one place — goat about 150 days, sheep about 147,
 * cattle about 283, rabbit 31 to 33 — each overridable per farm. The windows around the typical length
 * are defaults too; a farm sets its own on Species configuration.
 */
object GestationDefaults {
    val periods: Map<GestationSpecies, GestationPeriod> = mapOf(
        GestationSpecies.GOAT to GestationPeriod(145, 150, 155),
        GestationSpecies.SHEEP to GestationPeriod(142, 147, 152),
        GestationSpecies.CATTLE to GestationPeriod(276, 283, 290),
        GestationSpecies.RABBIT to GestationPeriod(31, 31, 33),
    )

    /** The farm's period for [species]: its own setting when it has one, otherwise the default. */
    fun period(species: GestationSpecies, farmOverrides: Map<GestationSpecies, GestationPeriod> = emptyMap()): GestationPeriod =
        farmOverrides[species] ?: periods.getValue(species)
}

/** Where an expected birth date came from. */
enum class DueSource {
    /** A birth has been recorded; nothing is predicted any more. */
    BORN,

    /** A due date stored on the record (for example a scanned or vet-given date) wins over any prediction. */
    STORED,

    /** Predicted from the service date and the farm's gestation period. */
    PREDICTED,
}

data class DueEstimate(
    val source: DueSource,
    val typicalEpochDay: Long,
    val earliestEpochDay: Long,
    val latestEpochDay: Long,
)

object DueDates {
    /**
     * The expected birth for one pregnancy. An actual birth supersedes any prediction; a stored due date
     * wins over the calculation; otherwise the date is the service day plus the farm's gestation period.
     */
    fun estimate(
        serviceEpochDay: Long,
        period: GestationPeriod,
        storedDueEpochDay: Long? = null,
        birthEpochDay: Long? = null,
    ): DueEstimate = when {
        birthEpochDay != null -> DueEstimate(DueSource.BORN, birthEpochDay, birthEpochDay, birthEpochDay)
        storedDueEpochDay != null -> DueEstimate(DueSource.STORED, storedDueEpochDay, storedDueEpochDay, storedDueEpochDay)
        else -> DueEstimate(
            DueSource.PREDICTED,
            typicalEpochDay = serviceEpochDay + period.typicalDays,
            earliestEpochDay = serviceEpochDay + period.earliestDays,
            latestEpochDay = serviceEpochDay + period.latestDays,
        )
    }
}
