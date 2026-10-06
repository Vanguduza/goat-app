package com.farmos.domain.ops

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FarmMetricsTest {
    private val feedCost = MetricDefinition("feed-cost", "Feed cost", "Sum of recorded feed costs", "USD", "This month", "This farm, all feed records")

    @Test
    fun unknownValuesAreMissingNotZero() {
        val result = MetricResult.sumOfKnown(feedCost, listOf(1_500, null, 2_500, null))
        assertEquals(4_000, result.value)
        assertEquals(2, result.included)
        assertEquals(2, result.missing)
        assertFalse(result.complete)
        assertEquals("Partial: 2 of 4 record(s) have no value", result.completeness)

        val all = MetricResult.sumOfKnown(feedCost, listOf(1_500, 2_500))
        assertTrue(all.complete)
        assertEquals("Complete", all.completeness)
        assertTrue(MetricResult.count(feedCost, 0).complete)
    }

    @Test
    fun csvQuotesAndNeutralisesFormulas() {
        val csv = FarmCsv.write(
            listOf("Tag", "Name", "Note"),
            listOf(
                listOf("GT-1", "Nala, \"the doe\"", null),
                listOf("=HYPERLINK(\"x\")", "-5", "line\nbreak"),
            ),
        )
        assertEquals(
            "Tag,Name,Note\r\n" +
                "GT-1,\"Nala, \"\"the doe\"\"\",\r\n" +
                "\"'=HYPERLINK(\"\"x\"\")\",'-5,\"line\nbreak\"\r\n",
            csv,
        )
        assertFailsWith<IllegalArgumentException> { FarmCsv.write(listOf("A", "B"), listOf(listOf("only one"))) }
    }
}
