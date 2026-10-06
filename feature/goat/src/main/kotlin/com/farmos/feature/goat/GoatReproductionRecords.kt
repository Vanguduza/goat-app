package com.farmos.feature.goat

import com.farmos.domain.goat.GoatSex
import com.farmos.domain.goat.GoatSnapshot
import com.farmos.domain.goat.GoatStatus

/**
 * Breeding status derived only from recorded events, newest record wins. No gestation length,
 * due date or clinical inference is applied here: expected kidding needs the farm-level gestation
 * configuration, which is a separate contract.
 */
internal enum class GoatBreedingStatus(val label: String) {
    CONFIRMED_PREGNANT("Confirmed pregnant"),
    BRED_NOT_CHECKED("Bred, not checked"),
    OPEN("Checked open"),
    KIDDED("Kidded since last check"),
    NO_BREEDING_RECORD("No breeding record"),
}

internal data class GoatBreedingSummary(
    val status: GoatBreedingStatus,
    /** Day of the record that decided the status, or null when there is none. */
    val decidedOnEpochDay: Long?,
)

internal object GoatReproductionRecords {
    fun breedingSummary(goat: GoatSnapshot): GoatBreedingSummary {
        val latestCheck = goat.pregnancyHistory.maxWithOrNull(compareBy({ it.occurredEpochDay }, { it.checkId }))
        val latestMating = goat.matingHistory.maxWithOrNull(compareBy({ it.occurredEpochDay }, { it.matingId }))
        val latestKidding = goat.kiddingHistory.maxWithOrNull(compareBy({ it.occurredEpochDay }, { it.kiddingId }))
        val events = listOfNotNull(
            latestCheck?.let { check ->
                val status = if (check.result == "pregnant") GoatBreedingStatus.CONFIRMED_PREGNANT else GoatBreedingStatus.OPEN
                Triple(check.occurredEpochDay, 1, status)
            },
            latestMating?.let { Triple(it.occurredEpochDay, 0, GoatBreedingStatus.BRED_NOT_CHECKED) },
            latestKidding?.let { Triple(it.occurredEpochDay, 2, GoatBreedingStatus.KIDDED) },
        )
        // Same-day ordering: a mating precedes a check, and a kidding follows both.
        val decisive = events.maxWithOrNull(compareBy({ it.first }, { it.second }))
            ?: return GoatBreedingSummary(GoatBreedingStatus.NO_BREEDING_RECORD, null)
        return GoatBreedingSummary(decisive.third, decisive.first)
    }

    /** Active does only, grouped in a stable status order and sorted by tag within each group. */
    fun pregnancyBoard(herd: List<GoatSnapshot>): List<Pair<GoatBreedingStatus, List<Pair<GoatSnapshot, GoatBreedingSummary>>>> {
        val does = herd.filter { it.sex == GoatSex.FEMALE && it.status == GoatStatus.ACTIVE }
            .map { it to breedingSummary(it) }
        return GoatBreedingStatus.entries.mapNotNull { status ->
            does.filter { it.second.status == status }
                .sortedBy { it.first.tag }
                .takeIf { it.isNotEmpty() }
                ?.let { status to it }
        }
    }

    fun matingMethodLabel(method: String): String = when (method) {
        "natural" -> "Natural"
        "ai" -> "Artificial insemination"
        "hand_mating" -> "Hand mating"
        else -> method
    }

    fun relationLabel(relationType: String): String = when (relationType) {
        "dam" -> "Dam"
        "sire" -> "Sire"
        "genetic_dam" -> "Genetic dam"
        "recipient_dam" -> "Recipient dam"
        else -> relationType
    }
}
