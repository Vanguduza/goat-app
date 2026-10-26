package com.farmos.app

import com.farmos.domain.ops.*
import org.junit.Assert.*
import org.junit.Test

class AnimalHealthReviewRulesTest {
    private fun data(species:String="goat",weights:List<HealthWeightEvidence> = emptyList())=AnimalHealthReviewInput(
        species,3000,weights,emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList())
    @Test fun missingRecordsNeverBecomeHealthyOrZeroWeight() {
        for(species in AnimalHealthReview.supportedSpecies) {
            val signals=AnimalHealthReview.signals(data(species))
            assertTrue(signals.any{it.code=="weight_baseline"})
            assertTrue(signals.any{it.code=="health_baseline"})
            assertFalse(signals.any{it.code in setOf("healthy","normal","clear_for_sale")})
            assertTrue(signals.all{it.evidenceIds.isEmpty()})
        }
    }
    @Test fun declineUsesTwoActualDatedWeightsAndRetainsTheirIds() {
        val signals=AnimalHealthReview.signals(data("cattle",listOf(
            HealthWeightEvidence("older",425000,1000),HealthWeightEvidence("latest",424725,2000))))
        val signal=signals.single{it.code=="weight_decrease"}
        assertEquals(listOf("older","latest"),signal.evidenceIds)
        assertTrue(signal.explanation.contains("0.275 kg"))
        assertEquals(HealthReviewAction.RECORD_OBSERVATION,signal.action)
        assertTrue(signal.explanation.contains("not a diagnosis"))
    }
    @Test fun futureAndInvalidWeightsDoNotCreateADiagnosisOrTrend() {
        val signals=AnimalHealthReview.signals(data("rabbit",listOf(
            HealthWeightEvidence("future",1,5000),HealthWeightEvidence("invalid",0,1000))))
        assertTrue(signals.any{it.code=="weight_baseline"})
        assertFalse(signals.any{it.code=="weight_decrease"})
    }
    @Test fun conflictingSameTimestampWeightsRequireRecordReview() {
        val signals=AnimalHealthReview.signals(data(weights=listOf(
            HealthWeightEvidence("a",1000,2000),HealthWeightEvidence("b",900,2000),HealthWeightEvidence("c",1200,1000))))
        assertTrue(signals.any{it.code=="weight_conflict"})
        assertFalse(signals.any{it.code=="weight_decrease"})
    }
    @Test fun recordFlagsAndWithdrawalEvidenceAreNotAutomaticallyResolved() {
        val signals=AnimalHealthReview.signals(data("sheep").copy(recordedRedFlagIds=listOf("obs"),activeWithdrawalIds=listOf("hold"),dueTaskIds=listOf("task")))
        assertEquals(listOf("recorded_red_flag","withdrawal","due_follow_up"),signals.take(3).map{it.code})
        assertTrue(signals[0].explanation.contains("not proof"))
        assertEquals(listOf("hold"),signals[1].evidenceIds)
        assertFalse(signals.any{it.explanation.contains("Give ")})
    }
}
