package com.farmos.feature.goat

import com.farmos.domain.goat.BcsSample
import com.farmos.domain.goat.GoatSex
import com.farmos.domain.goat.GoatSnapshot
import com.farmos.domain.goat.MilkSample
import com.farmos.domain.goat.WeightSample
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoatRecordHistoryTest {
    private fun millis(day: LocalDate, hour: Int = 0) = day.toEpochDay() * 86_400_000L + hour * 3_600_000L

    private fun goat(
        weights: List<WeightSample> = emptyList(),
        milk: List<MilkSample> = emptyList(),
        bcs: List<BcsSample> = emptyList(),
    ) = GoatSnapshot(
        animalId = "g1",
        farmId = "f1",
        tag = "T1",
        name = null,
        sex = GoatSex.FEMALE,
        latestWeightGrams = weights.lastOrNull()?.weightGrams,
        weightHistory = weights,
        milkHistory = milk,
        bcsHistory = bcs,
        syncPending = false,
    )

    @Test
    fun timelineIsNewestFirstWithStableTieBreaks() {
        val day = LocalDate.of(2026, 9, 1)
        val entries = GoatRecordHistory.timeline(
            goat(
                weights = listOf(WeightSample("w-old", 40_000, millis(day.minusDays(3))), WeightSample("w-new", 41_000, millis(day, hour = 23))),
                milk = listOf(MilkSample("m1", 2_000, day.toEpochDay())),
                bcs = listOf(BcsSample("b1", 35, day.minusDays(1).toEpochDay())),
            ),
        )
        assertEquals(listOf("w-new", "m1", "b1", "w-old"), entries.map { it.recordId })
        assertEquals("Score 3.5", entries[2].summary)
        assertEquals(listOf(GoatRecordKind.WEIGHT, GoatRecordKind.MILK, GoatRecordKind.BCS), GoatRecordHistory.kindsPresent(entries))
    }

    @Test
    fun weightDayUsesUtcCalendarDayConsistentWithCapture() {
        assertEquals(LocalDate.of(2026, 9, 1), GoatRecordHistory.weightDay(millis(LocalDate.of(2026, 9, 1), hour = 23)))
    }

    @Test
    fun adgMatchesTheDomainRuleAndExplainsItsInputs() {
        val first = WeightSample("a", 20_000, millis(LocalDate.of(2026, 1, 1)))
        val middle = WeightSample("b", 22_500, millis(LocalDate.of(2026, 1, 15)))
        val last = WeightSample("c", 26_000, millis(LocalDate.of(2026, 2, 10)))
        val breakdown = GoatRecordHistory.adg(goat(weights = listOf(last, first, middle)))!!
        assertEquals("a", breakdown.first.measurementId)
        assertEquals("c", breakdown.last.measurementId)
        assertEquals(3, breakdown.sampleCount)
        assertEquals(6_000L, breakdown.gainGrams)
        assertEquals(40L, breakdown.elapsedDays)
        assertEquals(150L, breakdown.averageDailyGainGrams)
    }

    @Test
    fun adgIsAbsentRatherThanZeroWithoutTwoTimedWeights() {
        assertNull(GoatRecordHistory.adg(goat()))
        assertNull(GoatRecordHistory.adg(goat(weights = listOf(WeightSample("a", 20_000, 0L)))))
        assertNull(GoatRecordHistory.adg(goat(weights = listOf(WeightSample("a", 20_000, 5L), WeightSample("b", 21_000, 5L)))))
    }

    @Test
    fun weightChangesAreSignedAndFirstWeightHasNoChange() {
        val ordered = listOf(
            WeightSample("a", 30_000, 1L),
            WeightSample("b", 29_500, 2L),
            WeightSample("c", 31_000, 3L),
        )
        assertEquals(listOf(null, -500L, 1_500L), GoatRecordHistory.weightChanges(ordered))
        assertEquals("-0.50", formatSignedKg(-500))
        assertEquals("+1.50", formatSignedKg(1_500))
    }
}
