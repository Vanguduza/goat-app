package com.farmos.domain.rabbit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RabbitWeightValidatorTest {
    private val now = 1_700_000_000_000L

    private fun command(
        weightId: String = "w-1",
        animalId: String = "rabbit-1",
        weightKg: Double = 2.5,
        weighedAtEpochMillis: Long = now,
    ) = RecordRabbitWeight(weightId, animalId, weightKg, weighedAtEpochMillis)

    @Test
    fun `a sane weighing is valid`() {
        assertNull(RabbitProgrammeValidator.weight(command(), now))
    }

    @Test
    fun `a weight id is required`() {
        assertEquals("Rabbit weight needs an id", RabbitProgrammeValidator.weight(command(weightId = "  "), now))
    }

    @Test
    fun `a rabbit is required`() {
        assertEquals("Rabbit weight needs a rabbit", RabbitProgrammeValidator.weight(command(animalId = ""), now))
    }

    @Test
    fun `weight must be positive and within sane bounds`() {
        assertEquals("Rabbit weight must be positive", RabbitProgrammeValidator.weight(command(weightKg = 0.0), now))
        assertEquals("Rabbit weight must be positive", RabbitProgrammeValidator.weight(command(weightKg = -1.2), now))
        assertEquals(
            "Rabbit weight is outside sane bounds",
            RabbitProgrammeValidator.weight(command(weightKg = RabbitProgrammeValidator.MAX_RABBIT_WEIGHT_KG + 0.1), now),
        )
    }

    @Test
    fun `exactly the maximum weight is valid`() {
        // P2-2 (independent review 2026-10-08): pins the boundary — the validator
        // rejects only weightKg > MAX, so exactly MAX is valid.
        assertNull(RabbitProgrammeValidator.weight(command(weightKg = RabbitProgrammeValidator.MAX_RABBIT_WEIGHT_KG), now))
    }

    @Test
    fun `weighing time cannot be in the future`() {
        assertEquals(
            "Weighing time cannot be in the future",
            RabbitProgrammeValidator.weight(command(weighedAtEpochMillis = now + 1), now),
        )
    }
}
