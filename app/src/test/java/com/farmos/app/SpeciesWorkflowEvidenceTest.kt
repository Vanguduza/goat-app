package com.farmos.app

import com.farmos.core.design.FarmWorkflowState
import com.farmos.feature.ops.CattleCalvingDueView
import com.farmos.feature.ops.CattleDueSource
import com.farmos.feature.ops.SheepLambProfile
import com.farmos.feature.ops.SheepMobLambingView
import com.farmos.feature.ops.cattleCalvingWorkflowSteps
import com.farmos.feature.ops.sheepLambDevelopmentSteps
import com.farmos.feature.ops.sheepMobLambingSteps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeciesWorkflowEvidenceTest {
    @Test fun elapsedMobDatesRemainPlansAndDoNotInventRamOut() {
        val steps = sheepMobLambingSteps(SheepMobLambingView("m", "Ewes", 15, 100, 240, 247))
        assertEquals(listOf(FarmWorkflowState.RECORDED, FarmWorkflowState.PLANNED, FarmWorkflowState.PLANNED), steps.map { it.state })
        assertEquals(3, steps.size)
        assertTrue(steps.none { it.label.contains("out", true) })
    }
    @Test fun missingEarlyLifeEventsAreNotInferredFromBirth() {
        val steps = sheepLambDevelopmentSteps(SheepLambProfile(lambId = "l", birthEpochDay = 100))
        assertEquals(listOf(FarmWorkflowState.RECORDED, FarmWorkflowState.NOT_RECORDED, FarmWorkflowState.NOT_RECORDED), steps.map { it.state })
        assertEquals("Not recorded", steps.last().value)
    }
    @Test fun weaningRecordDoesNotInventAMarkingRecord() {
        val steps = sheepLambDevelopmentSteps(SheepLambProfile(lambId = "l", weaningEpochDay = 180))
        assertEquals(FarmWorkflowState.NOT_RECORDED, steps[1].state)
        assertEquals(FarmWorkflowState.RECORDED, steps[2].state)
    }
    @Test fun storedCalvingExpectationIsStillAPlan() {
        val cow = CattleCalvingDueView("c", "C1", null, 1, "ai", "pregnant", CattleDueSource.STORED, 280, 280, 280)
        val steps = cattleCalvingWorkflowSteps(cow)
        assertEquals(FarmWorkflowState.PLANNED, steps.last().state)
        assertEquals("Expected date recorded at dry-off", steps.last().detail)
    }
    @Test fun absentPregnancyCheckStaysMissing() {
        val cow = CattleCalvingDueView("c", "C1", null, 1, "natural", null, CattleDueSource.PREDICTED, 270, 280, 290)
        val steps = cattleCalvingWorkflowSteps(cow)
        assertEquals(FarmWorkflowState.NOT_RECORDED, steps[1].state)
        assertEquals("No pregnancy check recorded", steps[1].value)
        assertEquals(FarmWorkflowState.PLANNED, steps.last().state)
    }
}
