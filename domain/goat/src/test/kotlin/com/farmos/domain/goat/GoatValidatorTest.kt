package com.farmos.domain.goat

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

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

    @Test
    fun `future date of birth is rejected`() {
        val result = GoatValidator.register(
            RegisterGoat(
                animalId = "a1",
                tag = "NALA-01",
                sex = GoatSex.FEMALE,
                dateOfBirthEpochDay = 20_000,
            ),
            todayEpochDay = 19_723,
        )
        assertIs<GoatValidationResult.Invalid>(result)
        assertEquals("DOB_FUTURE", result.code)
    }

    @Test
    fun `sold dead and culled are the only lifecycle commands`() {
        assertIs<GoatValidationResult.Valid>(
            GoatValidator.status(SetGoatStatus(animalId = "a1", status = GoatStatus.SOLD)),
        )
        assertIs<GoatValidationResult.Invalid>(
            GoatValidator.status(SetGoatStatus(animalId = "a1", status = GoatStatus.ACTIVE)),
        )
    }

    @Test
    fun `status command serializes lowercase wire values`() {
        val json = Json.encodeToString(
            SetGoatStatus.serializer(),
            SetGoatStatus(animalId = "a1", status = GoatStatus.DEAD),
        )
        assertTrue(json.contains("\"status\":\"dead\""))
        val decoded = Json.decodeFromString(SetGoatStatus.serializer(), """{"animalId":"a1","status":"sold"}""")
        assertEquals(GoatStatus.SOLD, decoded.status)
    }

    @Test
    fun `milk needs litres`() {
        assertIs<GoatValidationResult.Invalid>(
            GoatValidator.milk(RecordGoatMilk("m1", "a1", litresMilli = 0, occurredEpochDay = 19_723)),
        )
        assertIs<GoatValidationResult.Valid>(
            GoatValidator.milk(RecordGoatMilk("m1", "a1", litresMilli = 2_500, occurredEpochDay = 19_723)),
        )
    }

    @Test
    fun `goat bcs stays on the 1 to 5 scale`() {
        assertIs<GoatValidationResult.Invalid>(
            GoatValidator.bcs(RecordGoatBcs("b1", "a1", scoreTenths = 9, occurredEpochDay = 19_723)),
        )
        assertIs<GoatValidationResult.Valid>(
            GoatValidator.bcs(RecordGoatBcs("b1", "a1", scoreTenths = 30, occurredEpochDay = 19_723)),
        )
    }

    @Test
    fun `goat scc is a cell count not a diagnosis`() {
        assertIs<GoatValidationResult.Invalid>(
            GoatValidator.scc(RecordGoatScc("s1", "a1", cellsPerMl = 0, occurredEpochDay = 19_723)),
        )
        assertIs<GoatValidationResult.Valid>(
            GoatValidator.scc(RecordGoatScc("s1", "a1", cellsPerMl = 250_000, occurredEpochDay = 19_723)),
        )
    }

    @Test
    fun `kidding counts must add up`() {
        val invalid = GoatValidator.kidding(
            RecordGoatKidding(
                kiddingId = "k1",
                damAnimalId = "d1",
                bornCount = 2,
                liveCount = 2,
                deadCount = 1,
                occurredEpochDay = 19_723,
            ),
        )
        assertIs<GoatValidationResult.Invalid>(invalid)
        assertIs<GoatValidationResult.Valid>(
            GoatValidator.kidding(
                RecordGoatKidding(
                    kiddingId = "k1",
                    damAnimalId = "d1",
                    bornCount = 2,
                    liveCount = 2,
                    deadCount = 0,
                    occurredEpochDay = 19_723,
                ),
            ),
        )
    }

    @Test
    fun `heat needs a doe id`() {
        assertIs<GoatValidationResult.Invalid>(
            GoatValidator.heat(RecordGoatHeat("h1", "", occurredEpochDay = 19_723)),
        )
        assertIs<GoatValidationResult.Valid>(
            GoatValidator.heat(RecordGoatHeat("h1", "a1", occurredEpochDay = 19_723)),
        )
    }

    @Test
    fun `mating method stays on the listed set`() {
        assertIs<GoatValidationResult.Invalid>(
            GoatValidator.mating(RecordGoatMating("m1", "d1", method = "bucket", occurredEpochDay = 19_723, pregCheckTaskId = "t1")),
        )
        assertIs<GoatValidationResult.Valid>(
            GoatValidator.mating(RecordGoatMating("m1", "d1", method = "hand_mating", occurredEpochDay = 19_723, pregCheckTaskId = "t1")),
        )
    }

    @Test
    fun `pregnancy check is pregnant or open`() {
        assertIs<GoatValidationResult.Invalid>(
            GoatValidator.pregnancy(RecordGoatPregnancy("c1", "a1", result = "maybe", occurredEpochDay = 19_723)),
        )
        assertIs<GoatValidationResult.Valid>(
            GoatValidator.pregnancy(RecordGoatPregnancy("c1", "a1", result = "open", occurredEpochDay = 19_723)),
        )
    }

    @Test
    fun `lactation plan needs a doe and a task`() {
        assertIs<GoatValidationResult.Invalid>(
            GoatValidator.lactation(PlanGoatLactation("p1", "", occurredEpochDay = 19_723, checkTaskId = "t1")),
        )
        assertIs<GoatValidationResult.Valid>(
            GoatValidator.lactation(PlanGoatLactation("p1", "a1", occurredEpochDay = 19_723, checkTaskId = "t1")),
        )
    }

    @Test
    fun `kid record needs a tag and kidding`() {
        assertIs<GoatValidationResult.Invalid>(
            GoatValidator.kid(RegisterGoatKid("k1", "", "", sex = GoatSex.FEMALE, pedigreeLinkId = "p1")),
        )
        assertIs<GoatValidationResult.Valid>(
            GoatValidator.kid(RegisterGoatKid("k1", "kid-1", "NALA-K1", sex = GoatSex.FEMALE, pedigreeLinkId = "p1")),
        )
    }
}
