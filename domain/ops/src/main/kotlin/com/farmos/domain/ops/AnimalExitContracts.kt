package com.farmos.domain.ops

import kotlinx.serialization.Serializable

/**
 * Records that an active animal left the farm (D-022, resolution R6). [currency] is the farm currency when
 * the exit was recorded and accompanies any [priceMinor]. A sale exit does not post money by itself.
 * Replicated as `animal.exit_record.v1`.
 */
@Serializable
data class RecordAnimalExit(
    val exitId: String,
    val animalId: String,
    val kind: String,
    val occurredEpochDay: Long,
    val deathCause: String? = null,
    val reason: String? = null,
    val buyer: String? = null,
    val priceMinor: Long? = null,
    val currency: String? = null,
)

/** Reverses the animal's standing exit, returning it to active; both records are kept. Replicated as `animal.exit_reverse.v1`. */
@Serializable
data class ReverseAnimalExit(val reversalId: String, val animalId: String, val exitId: String, val reason: String, val occurredEpochDay: Long)
