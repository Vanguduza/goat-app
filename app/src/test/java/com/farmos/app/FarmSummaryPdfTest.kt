package com.farmos.app

import com.farmos.domain.ops.MetricDefinition
import com.farmos.domain.ops.MetricResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Owner decision D-026: the printed farm summary carries every figure on Reports with its definition. */
class FarmSummaryPdfTest {
    private fun metric(n: Int, missing: Int = 0) = MetricResult(
        MetricDefinition("m$n", "Metric $n", "Count of every record of kind $n across the whole farm and all of its species and groups", "animals", "All time", "This farm"),
        n.toLong(), 10, missing,
    )

    @Test
    fun textIsWrappedToTheLineWidthWithoutLosingWords() {
        val text = "How: Sum of each active animal's latest recorded weight over every record on this device"
        val lines = wrapSummaryText(text, 20)
        assertTrue(lines.all { it.length <= 20 })
        assertEquals(text, lines.joinToString(" "))
        assertEquals(listOf("abcde", "fghij", "k"), wrapSummaryText("abcdefghijk", 5))
        assertEquals(listOf(""), wrapSummaryText("", 5))
    }

    @Test
    fun everyMetricIsPrintedOnceWithItsDefinitionAndNeverSplitAcrossPages() {
        val metrics = (1..40).map { metric(it, missing = if (it == 7) 3 else 0) }
        val pages = farmSummaryPages(metrics, linesPerPage = 20, width = 40)
        assertTrue(pages.size > 1)
        assertTrue(pages.all { it.size <= 20 })
        val headings = pages.flatten().filter { it.heading }.map { it.text }
        assertEquals(metrics.map { it.definition.name }, headings)
        pages.forEach { page ->
            // A page never ends on a heading or starts part-way through a metric.
            assertTrue(page.first().heading)
            assertTrue(!page.last().heading)
        }
        val text = pages.flatten().joinToString("\n") { it.text }
        assertTrue(text.contains("Partial: 3 of 13 record(s) have no value"))
        assertTrue(text.contains("Period: All time · Scope: This farm"))
    }

    @Test
    fun anEmptyFarmPrintsThatNothingIsRecorded() {
        assertEquals(listOf(listOf(SummaryLine("No animals are recorded on this device yet."))), farmSummaryPages(emptyList()))
    }
}
