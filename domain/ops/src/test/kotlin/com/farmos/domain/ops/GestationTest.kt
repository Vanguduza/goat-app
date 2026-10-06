package com.farmos.domain.ops

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GestationTest {
    @Test
    fun theOwnerDefaultsAreHeldInOnePlace() {
        assertEquals(150, GestationDefaults.period(GestationSpecies.GOAT).typicalDays)
        assertEquals(147, GestationDefaults.period(GestationSpecies.SHEEP).typicalDays)
        assertEquals(283, GestationDefaults.period(GestationSpecies.CATTLE).typicalDays)
        val rabbit = GestationDefaults.period(GestationSpecies.RABBIT)
        assertEquals(31, rabbit.earliestDays)
        assertEquals(33, rabbit.latestDays)
        assertEquals(GestationSpecies.entries.toSet(), GestationDefaults.periods.keys)
    }

    @Test
    fun aFarmOverrideReplacesTheDefaultForThatSpeciesOnly() {
        val own = GestationPeriod(146, 151, 156)
        assertEquals(own, GestationDefaults.period(GestationSpecies.GOAT, mapOf(GestationSpecies.GOAT to own)))
        assertEquals(147, GestationDefaults.period(GestationSpecies.SHEEP, mapOf(GestationSpecies.GOAT to own)).typicalDays)
    }

    @Test
    fun aBirthSupersedesAStoredDateWhichWinsOverThePrediction() {
        val goat = GestationDefaults.period(GestationSpecies.GOAT)
        val predicted = DueDates.estimate(20_000, goat)
        assertEquals(DueEstimate(DueSource.PREDICTED, 20_150, 20_145, 20_155), predicted)
        assertEquals(DueEstimate(DueSource.STORED, 20_140, 20_140, 20_140), DueDates.estimate(20_000, goat, storedDueEpochDay = 20_140))
        assertEquals(DueSource.BORN, DueDates.estimate(20_000, goat, storedDueEpochDay = 20_140, birthEpochDay = 20_148).source)
    }

    @Test
    fun impossiblePeriodsAreRejected() {
        assertFailsWith<IllegalArgumentException> { GestationPeriod(0, 150, 155) }
        assertFailsWith<IllegalArgumentException> { GestationPeriod(151, 150, 155) }
        assertFailsWith<IllegalArgumentException> { GestationPeriod(145, 150, 149) }
        assertFailsWith<IllegalArgumentException> { GestationPeriod(145, 150, 401) }
    }
}
