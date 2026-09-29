package com.farmos.app

import com.farmos.feature.ops.HealthTimelineEntry
import com.farmos.feature.ops.HealthTimelineKind
import com.farmos.feature.ops.HealthTimelineKind.LAB_RESULT
import com.farmos.feature.ops.HealthTimelineKind.OBSERVATION
import com.farmos.feature.ops.HealthTimelineKind.TREATMENT
import com.farmos.feature.ops.HealthTimelineKind.VET_VISIT
import com.farmos.feature.ops.HealthTimelineStream
import com.farmos.feature.ops.mergeHealthTimeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The farm-wide health timeline never presents a bounded record type's gap as complete history. */
class HealthTimelineMergeTest {
    private fun entry(kind: HealthTimelineKind, id: String, day: Long) = HealthTimelineEntry(kind, id, day, null, "goat", id)

    @Test
    fun unboundedStreamsMergeCompletelyNewestFirst() {
        val timeline = mergeHealthTimeline(
            listOf(
                HealthTimelineStream(listOf(entry(TREATMENT, "t1", 9), entry(TREATMENT, "t0", 2)), bounded = false),
                HealthTimelineStream(listOf(entry(VET_VISIT, "v1", 5)), bounded = false),
            ),
            totalCount = 3,
        )
        assertEquals(listOf("t1", "v1", "t0"), timeline.entries.map { it.id })
        assertNull(timeline.completeFromEpochDay)
        assertEquals(3, timeline.totalCount)
    }

    @Test
    fun aBoundedStreamCutsEveryTypeAfterItsOldestListedDay() {
        val timeline = mergeHealthTimeline(
            listOf(
                // Bounded: rows on day 10 and earlier may be missing beyond the list limit.
                HealthTimelineStream(listOf(entry(OBSERVATION, "o2", 14), entry(OBSERVATION, "o1", 10)), bounded = true),
                HealthTimelineStream(listOf(entry(LAB_RESULT, "l3", 12), entry(LAB_RESULT, "l2", 10), entry(LAB_RESULT, "l1", 5)), bounded = false),
            ),
            totalCount = 900,
        )
        assertEquals(listOf("o2", "l3"), timeline.entries.map { it.id })
        assertEquals(11L, timeline.completeFromEpochDay)
    }

    @Test
    fun theLatestCutoffAcrossBoundedStreamsWins() {
        val timeline = mergeHealthTimeline(
            listOf(
                HealthTimelineStream(listOf(entry(TREATMENT, "t2", 20), entry(TREATMENT, "t1", 8)), bounded = true),
                HealthTimelineStream(listOf(entry(OBSERVATION, "o2", 21), entry(OBSERVATION, "o1", 15)), bounded = true),
                HealthTimelineStream(emptyList(), bounded = true),
            ),
            totalCount = 1_000,
        )
        assertEquals(listOf("o2", "t2"), timeline.entries.map { it.id })
        assertEquals(16L, timeline.completeFromEpochDay)
    }

    @Test
    fun sameDayEntriesOrderByKindThenId() {
        val timeline = mergeHealthTimeline(
            listOf(
                HealthTimelineStream(listOf(entry(LAB_RESULT, "l1", 7)), bounded = false),
                HealthTimelineStream(listOf(entry(TREATMENT, "t-b", 7), entry(TREATMENT, "t-a", 7)), bounded = false),
                HealthTimelineStream(listOf(entry(OBSERVATION, "o1", 7)), bounded = false),
            ),
            totalCount = 4,
        )
        assertEquals(listOf("o1", "t-a", "t-b", "l1"), timeline.entries.map { it.id })
    }
}
