package com.farmos.domain.goat

import kotlin.test.Test
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
}
