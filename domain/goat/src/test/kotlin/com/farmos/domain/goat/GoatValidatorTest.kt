package com.farmos.domain.goat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GoatValidatorTest {
    @Test
    fun `blank tag is rejected`() {
        val result = GoatValidator.register(
            RegisterGoat(animalId = "a1", tag = "", sex = GoatSex.FEMALE),
        )
        assertIs<GoatValidationResult.Invalid>(result)
    }

    @Test
    fun `non-positive weight is rejected`() {
        val result = GoatValidator.weight(
            RecordGoatWeight(
                animalId = "a1",
                measurementId = "m1",
                weightGrams = 0,
                measuredAtEpochMillis = 1,
            ),
        )
        assertIs<GoatValidationResult.Invalid>(result)
    }

    @Test
    fun `valid field weight is accepted`() {
        val result = GoatValidator.weight(
            RecordGoatWeight(
                animalId = "a1",
                measurementId = "m1",
                weightGrams = 32_450,
                measuredAtEpochMillis = 1,
            ),
        )
        assertIs<GoatValidationResult.Valid>(result)
    }

    @Test
    fun `average daily gain uses whole grams over elapsed days`() {
        val gain = GoatGrowth.averageDailyGainGrams(
            listOf(
                WeightSample("m1", 30_000, measuredAtEpochMillis = 0L),
                WeightSample("m2", 32_160, measuredAtEpochMillis = 10 * 86_400_000L),
            ),
        )
        assertEquals(216L, gain)
    }

    @Test
    fun `average daily gain is absent until two chronological weights exist`() {
        assertEquals(null, GoatGrowth.averageDailyGainGrams(emptyList()))
        assertEquals(
            null,
            GoatGrowth.averageDailyGainGrams(
                listOf(WeightSample("m1", 30_000, measuredAtEpochMillis = 1L)),
            ),
        )
    }
}
