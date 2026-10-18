package com.farmos.feature.rabbit

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RabbitWorkflowVisualsTest {
    private val today = LocalDate.of(2026, 10, 18)
    private val matingDay = today.toEpochDay() - 40L

    private fun wave(
        id: String = "wave-a",
        cageLabel: String = "Cage A",
        kindlingRecorded: Boolean = false,
        latestOutcome: String? = null,
        events: List<RabbitWaveEventView> = emptyList(),
        mating: Long = matingDay,
        weaning: Long = mating + 58L,
    ) = RabbitWaveView(
        id = id,
        cageLabel = cageLabel,
        doeCount = 3,
        matingEpochDay = mating,
        nestInEpochDay = mating + 27L,
        kindlingEpochDay = mating + 31L,
        nestOutEpochDay = mating + 45L,
        rebreedEpochDay = mating + 44L,
        weanEpochDay = weaning,
        events = events,
        kindlingRecorded = kindlingRecorded,
        latestOutcome = latestOutcome,
    )

    @Test
    fun passedPlanDatesNeverBecomeRecordedActivity() {
        val milestones = rabbitWorkflowMilestones(wave())
        assertTrue(milestones.any { it.scheduledEpochDay < today.toEpochDay() })
        assertTrue(milestones.none { it.hasRecordedActivity })
        assertTrue(milestones.all { it.recordedEpochDays.isEmpty() })
    }

    @Test
    fun kindledOutcomeAloneCannotReplaceAKindlingRecord() {
        val outcome = RabbitWaveEventView("outcome-a", matingDay + 33L, "Outcome", "Kindled")
        val kindling = rabbitWorkflowMilestones(wave(latestOutcome = "kindled", events = listOf(outcome)))
            .single { it.key == "kindling" }
        assertFalse(kindling.hasRecordedActivity)
        assertTrue(kindling.recordedEpochDays.isEmpty())
    }

    @Test
    fun actualDatesRemainSeparateFromTheStoredPlan() {
        val actualDay = matingDay + 34L
        val actual = RabbitWaveEventView("kindling-a", actualDay, "Kindling", "Recorded litter")
        val milestones = rabbitWorkflowMilestones(wave(kindlingRecorded = true, events = listOf(actual)))
        val kindling = milestones.single { it.key == "kindling" }
        assertEquals(matingDay + 31L, kindling.scheduledEpochDay)
        assertEquals(listOf(actualDay), kindling.recordedEpochDays)
        assertTrue(kindling.hasRecordedActivity)
        assertFalse(milestones.single { it.key == "nest_in" }.hasRecordedActivity)
        assertFalse(milestones.single { it.key == "nest_out" }.hasRecordedActivity)
        assertFalse(milestones.single { it.key == "wean" }.hasRecordedActivity)
    }

    @Test
    fun multipleRecordDatesRetainedAndUnrelatedEventsDoNotCompleteStages() {
        val events = listOf(
            RabbitWaveEventView("kindling-b", matingDay + 34L, "Kindling", "Second litter"),
            RabbitWaveEventView("kindling-a", matingDay + 32L, "Kindling", "First litter"),
            RabbitWaveEventView("palpation-a", matingDay + 12L, "Palpation", "Positive"),
            RabbitWaveEventView("foster-a", matingDay + 35L, "Foster in", "Foster recorded"),
        )
        val milestones = rabbitWorkflowMilestones(wave(events = events))
        assertEquals(
            listOf(matingDay + 32L, matingDay + 34L),
            milestones.single { it.key == "kindling" }.recordedEpochDays,
        )
        assertEquals(listOf("kindling"), milestones.filter { it.hasRecordedActivity }.map { it.key })
    }

    @Test
    fun calendarPreservesStoredOrderingRatherThanImposingAnInterval() {
        val milestones = rabbitWorkflowMilestones(wave())
        assertEquals(milestones.map { it.scheduledEpochDay }.sorted(), milestones.map { it.scheduledEpochDay })
        assertTrue(milestones.indexOfFirst { it.key == "rebreed" } < milestones.indexOfFirst { it.key == "nest_out" })
        assertEquals(matingDay + 44L, milestones.single { it.key == "rebreed" }.scheduledEpochDay)
    }

    @Test
    fun cageWaveLinksUseIdentityAndNeverTheDisplayLabel() {
        val linked = wave(id = "linked", cageLabel = "Same name")
        val unrelated = wave(id = "unrelated", cageLabel = "Same name")
        val cage = RabbitCageView(
            id = "cage-a",
            code = "Same name",
            doeCapacity = 4,
            nestBoxes = emptyList(),
            waveIds = listOf("linked", "missing", "linked"),
        )
        assertEquals(listOf(linked), rabbitCageWaves(cage, listOf(unrelated, linked)))
        assertTrue(rabbitCageWaves(cage, listOf(unrelated)).isEmpty())
    }

    @Test
    fun boxCyclePreservesNativeStatesAndDoesNotCountUnknownAsReady() {
        val boxes = listOf("available", "sanitized", "assigned", "in_cage", "dirty", "unknown")
            .mapIndexed { index, status -> RabbitNestBoxView("box-$index", "B$index", status) }
        assertEquals(
            mapOf("ready" to 2, "assigned" to 1, "in_cage" to 1, "dirty" to 1),
            rabbitNestStageCounts(boxes),
        )
    }

    @Test
    fun todayBoundaryIsExplicitAndIndependentFromTheHostClock() {
        assertEquals(RabbitScheduledTiming.PAST, rabbitScheduledTiming(today.toEpochDay() - 1, today))
        assertEquals(RabbitScheduledTiming.TODAY, rabbitScheduledTiming(today.toEpochDay(), today))
        assertEquals(RabbitScheduledTiming.UPCOMING, rabbitScheduledTiming(today.toEpochDay() + 1, today))
    }

    @Test
    fun defaultWavePrefersCurrentOrUpcomingThenLatestHistory() {
        val day = today.toEpochDay()
        val old = wave(id = "old", mating = day - 180L)
        val recentPast = wave(id = "recent-past", mating = day - 70L)
        val current = wave(id = "current", mating = day - 10L)
        val upcoming = wave(id = "upcoming", mating = day + 4L)
        assertEquals(current, rabbitRelevantWave(listOf(old, upcoming, recentPast, current), today))
        assertEquals(upcoming, rabbitRelevantWave(listOf(recentPast, old, upcoming), today))
        assertEquals(recentPast, rabbitRelevantWave(listOf(old, recentPast), today))
        assertNull(rabbitRelevantWave(emptyList(), today))
    }
}
