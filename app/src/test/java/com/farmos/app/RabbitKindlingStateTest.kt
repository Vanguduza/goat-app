package com.farmos.app

import com.farmos.feature.rabbit.RabbitKindlingState
import com.farmos.feature.rabbit.rabbitKindlingState
import org.junit.Assert.assertEquals
import org.junit.Test

/** Kindling classification uses only recorded facts; the latest outcome is chosen deterministically. */
class RabbitKindlingStateTest {
    @Test
    fun onlyPregnantOrUnrecordedOutcomesAwaitKindling() {
        assertEquals(RabbitKindlingState.AWAITING_KINDLING, rabbitKindlingState(kindlingRecorded = false, latestOutcome = null))
        assertEquals(RabbitKindlingState.AWAITING_KINDLING, rabbitKindlingState(false, "pregnant"))
        assertEquals(RabbitKindlingState.NOT_PREGNANT_RECORDED, rabbitKindlingState(false, "false_pregnancy"))
        assertEquals(RabbitKindlingState.NOT_PREGNANT_RECORDED, rabbitKindlingState(false, "open"))
        assertEquals(RabbitKindlingState.KINDLED_OUTCOME_WITHOUT_KINDLING_RECORD, rabbitKindlingState(false, "kindled"))
        assertEquals(RabbitKindlingState.OUTCOME_NOT_RECOGNISED, rabbitKindlingState(false, "unknown_value"))
    }

    @Test
    fun aRecordedKindlingWinsOverAnyOutcome() {
        listOf(null, "pregnant", "false_pregnancy", "open", "kindled").forEach {
            assertEquals(RabbitKindlingState.KINDLED_RECORDED, rabbitKindlingState(kindlingRecorded = true, latestOutcome = it))
        }
    }

    @Test
    fun latestOutcomeIsTheLatestDayThenLowestIdWhateverTheInputOrder() {
        val facts = listOf(
            RabbitWaveFact("w1", 10, "o-a", "pregnant"),
            RabbitWaveFact("w1", 20, "o-c", "open"),
            RabbitWaveFact("w1", 20, "o-b", "false_pregnancy"),
            RabbitWaveFact("w1", 5, "o-z", "kindled"),
            RabbitWaveFact("w2", 3, "o-d", "open"),
            RabbitWaveFact("w2", 9, "o-e", "pregnant"),
        )
        val expected = mapOf("w1" to "false_pregnancy", "w2" to "pregnant")
        assertEquals(expected, latestPerWave(facts))
        assertEquals(expected, latestPerWave(facts.reversed()))
        assertEquals(expected, latestPerWave(facts.shuffled(java.util.Random(7))))
    }
}
