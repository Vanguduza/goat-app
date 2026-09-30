package com.farmos.domain.ops

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaskRecurrenceTest {
    private fun day(text: String) = LocalDate.parse(text).toEpochDay()
    private fun days(vararg text: String) = text.map { day(it) }

    private fun schedule(kind: RecurrenceKind, start: String, interval: Int = 1, end: String? = null) =
        TaskSeriesSchedule(TaskRecurrence(kind, interval), day(start), end?.let { day(it) })

    @Test
    fun aOneOffTaskOccursOnlyOnItsDay() {
        val once = schedule(RecurrenceKind.NONE, "2026-10-05")
        assertEquals(days("2026-10-05"), TaskRecurrenceSchedule.occurrences(once, day("2026-10-01"), day("2026-12-31")))
        assertNull(TaskRecurrenceSchedule.next(once, day("2026-10-05")))
    }

    @Test
    fun dailyWeekdaysAndWeeklyFollowTheCalendar() {
        // 2026-10-02 is a Friday.
        assertEquals(
            days("2026-10-02", "2026-10-03", "2026-10-04"),
            TaskRecurrenceSchedule.occurrences(schedule(RecurrenceKind.DAILY, "2026-10-02"), day("2026-10-01"), day("2026-10-04")),
        )
        assertEquals(
            days("2026-10-02", "2026-10-05", "2026-10-06"),
            TaskRecurrenceSchedule.occurrences(schedule(RecurrenceKind.WEEKDAYS, "2026-10-02"), day("2026-10-02"), day("2026-10-06")),
        )
        assertEquals(
            days("2026-10-02", "2026-10-09", "2026-10-16"),
            TaskRecurrenceSchedule.occurrences(schedule(RecurrenceKind.WEEKLY, "2026-10-02"), day("2026-09-01"), day("2026-10-20")),
        )
    }

    @Test
    fun everyNDaysAndWeeksCountFromTheFirstDay() {
        assertEquals(
            days("2026-10-10", "2026-10-13", "2026-10-16"),
            TaskRecurrenceSchedule.occurrences(schedule(RecurrenceKind.EVERY_N_DAYS, "2026-10-01", 3), day("2026-10-09"), day("2026-10-17")),
        )
        assertEquals(
            days("2026-10-01", "2026-10-15", "2026-10-29"),
            TaskRecurrenceSchedule.occurrences(schedule(RecurrenceKind.EVERY_N_WEEKS, "2026-10-01", 2), day("2026-10-01"), day("2026-11-04")),
        )
    }

    @Test
    fun monthlyKeepsTheStartDayAndUsesTheLastDayOfShorterMonths() {
        assertEquals(
            days("2027-01-31", "2027-02-28", "2027-03-31", "2027-04-30"),
            TaskRecurrenceSchedule.occurrences(schedule(RecurrenceKind.MONTHLY, "2027-01-31"), day("2027-01-01"), day("2027-04-30")),
        )
        assertEquals(day("2028-02-29"), TaskRecurrenceSchedule.next(schedule(RecurrenceKind.MONTHLY, "2028-01-31"), day("2028-01-31")))
    }

    @Test
    fun aSeriesEndsOnItsLastDay() {
        val bounded = schedule(RecurrenceKind.DAILY, "2026-10-01", end = "2026-10-03")
        assertEquals(days("2026-10-01", "2026-10-02", "2026-10-03"), TaskRecurrenceSchedule.occurrences(bounded, day("2026-09-01"), day("2026-12-01")))
        assertNull(TaskRecurrenceSchedule.next(bounded, day("2026-10-03")))
        assertFalse(TaskRecurrenceSchedule.isOccurrence(bounded, day("2026-10-04")))
    }

    @Test
    fun thisAndFutureSplitsAtAnOccurrenceOnly() {
        val weekly = schedule(RecurrenceKind.WEEKLY, "2026-10-02", end = "2026-12-31")
        val (before, after) = TaskRecurrenceSchedule.splitAt(weekly, day("2026-10-16"))
        assertEquals(day("2026-10-15"), before.endEpochDay)
        assertEquals(days("2026-10-02", "2026-10-09"), TaskRecurrenceSchedule.occurrences(before, day("2026-10-01"), day("2026-12-31")))
        assertEquals(day("2026-10-16"), after.startEpochDay)
        assertEquals(day("2026-12-31"), after.endEpochDay)
        assertFailsWith<IllegalArgumentException> { TaskRecurrenceSchedule.splitAt(weekly, day("2026-10-17")) }
        assertFailsWith<IllegalArgumentException> { TaskRecurrenceSchedule.splitAt(weekly, day("2026-10-02")) }
    }

    @Test
    fun occurrenceIdsAreStablePerSeriesAndDay() {
        val id = TaskRecurrenceSchedule.occurrenceId("series-1", day("2026-10-02"))
        assertEquals(id, TaskRecurrenceSchedule.occurrenceId("series-1", day("2026-10-02")))
        assertNotEquals(id, TaskRecurrenceSchedule.occurrenceId("series-1", day("2026-10-03")))
        assertNotEquals(id, TaskRecurrenceSchedule.occurrenceId("series-2", day("2026-10-02")))
    }

    @Test
    fun rulesAreValidated() {
        assertFailsWith<IllegalArgumentException> { TaskRecurrence(RecurrenceKind.EVERY_N_DAYS, 1) }
        assertFailsWith<IllegalArgumentException> { TaskRecurrence(RecurrenceKind.EVERY_N_WEEKS, 400) }
        assertFailsWith<IllegalArgumentException> { TaskRecurrence(RecurrenceKind.DAILY, 2) }
        assertFailsWith<IllegalArgumentException> { schedule(RecurrenceKind.DAILY, "2026-10-05", end = "2026-10-04") }
        assertTrue(TaskRecurrenceSchedule.occurrences(schedule(RecurrenceKind.DAILY, "2026-01-01"), day("2026-01-01"), day("2030-01-01")).size <= TaskRecurrenceSchedule.MAX_OCCURRENCES)
    }
}
