package com.farmos.domain.ops

import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID

/** Owner decision D-020: how a task repeats. */
enum class RecurrenceKind {
    NONE,
    DAILY,

    /** Monday to Friday. */
    WEEKDAYS,

    /** Every week on the start day's weekday. */
    WEEKLY,
    EVERY_N_DAYS,
    EVERY_N_WEEKS,

    /** Every month on the start day's day of month; in a shorter month, its last day. */
    MONTHLY,
}

/** A recurrence rule. [interval] is the N of every N days or weeks, and 1 for every other kind. */
data class TaskRecurrence(val kind: RecurrenceKind, val interval: Int = 1) {
    init {
        when (kind) {
            RecurrenceKind.EVERY_N_DAYS, RecurrenceKind.EVERY_N_WEEKS ->
                require(interval in 2..MAX_INTERVAL) { "Repeat every 2 to $MAX_INTERVAL ${if (kind == RecurrenceKind.EVERY_N_DAYS) "days" else "weeks"}" }
            else -> require(interval == 1) { "Only every N days or weeks takes an interval" }
        }
    }

    companion object {
        const val MAX_INTERVAL = 365
        val NONE = TaskRecurrence(RecurrenceKind.NONE)
    }
}

/**
 * A task series: the rule, its first day and an optional last day (inclusive). A one-off task is a series
 * with [TaskRecurrence.NONE].
 */
data class TaskSeriesSchedule(val recurrence: TaskRecurrence, val startEpochDay: Long, val endEpochDay: Long? = null) {
    init {
        require(endEpochDay == null || endEpochDay >= startEpochDay) { "A series cannot end before it starts" }
    }
}

/** Which occurrences an edit of a repeating task changes (D-020). Completed occurrences are never rewritten. */
enum class SeriesEditScope { THIS, THIS_AND_FUTURE, SERIES }

object TaskRecurrenceSchedule {
    /** Upper bound on occurrences returned in one call, so a long range cannot exhaust memory. */
    const val MAX_OCCURRENCES = 1_000

    /** The occurrence days of [schedule] within [fromEpochDay]..[toEpochDay], in order. */
    fun occurrences(schedule: TaskSeriesSchedule, fromEpochDay: Long, toEpochDay: Long): List<Long> {
        val last = minOf(toEpochDay, schedule.endEpochDay ?: Long.MAX_VALUE)
        val days = mutableListOf<Long>()
        var day = firstOnOrAfter(schedule, maxOf(fromEpochDay, schedule.startEpochDay))
        while (day != null && day <= last && days.size < MAX_OCCURRENCES) {
            days += day
            day = firstOnOrAfter(schedule, day + 1)
        }
        return days
    }

    /** The first occurrence after [epochDay], or null when the series has ended. */
    fun next(schedule: TaskSeriesSchedule, epochDay: Long): Long? =
        firstOnOrAfter(schedule, maxOf(epochDay + 1, schedule.startEpochDay))?.takeIf { it <= (schedule.endEpochDay ?: Long.MAX_VALUE) }

    /** Whether [epochDay] is an occurrence of [schedule]. */
    fun isOccurrence(schedule: TaskSeriesSchedule, epochDay: Long): Boolean =
        epochDay >= schedule.startEpochDay && firstOnOrAfter(schedule, epochDay) == epochDay &&
            epochDay <= (schedule.endEpochDay ?: Long.MAX_VALUE)

    /**
     * Splits a series for an edit of this and future occurrences from [epochDay]: the original series ends on
     * the day before, and the edited series starts on [epochDay] with the original end. Occurrences before the
     * split, completed or not, keep the original rule.
     */
    fun splitAt(schedule: TaskSeriesSchedule, epochDay: Long): Pair<TaskSeriesSchedule, TaskSeriesSchedule> {
        require(isOccurrence(schedule, epochDay)) { "A series can only be split at one of its occurrences" }
        require(epochDay > schedule.startEpochDay) { "Editing from the first occurrence changes the whole series" }
        return schedule.copy(endEpochDay = epochDay - 1) to schedule.copy(startEpochDay = epochDay)
    }

    /**
     * The stable identity of a series occurrence. Every device derives the same id for the same series and
     * day, so two devices that create the same occurrence offline cannot duplicate it.
     */
    fun occurrenceId(seriesId: String, epochDay: Long): String = UUID.nameUUIDFromBytes("task-occurrence:$seriesId:$epochDay".toByteArray()).toString()

    private fun firstOnOrAfter(schedule: TaskSeriesSchedule, epochDay: Long): Long? {
        val start = schedule.startEpochDay
        val rule = schedule.recurrence
        return when (rule.kind) {
            RecurrenceKind.NONE -> start.takeIf { epochDay <= start }
            RecurrenceKind.DAILY -> epochDay
            RecurrenceKind.WEEKDAYS -> {
                var day = epochDay
                while (LocalDate.ofEpochDay(day).dayOfWeek in WEEKEND) day++
                day
            }
            RecurrenceKind.WEEKLY -> stepOnOrAfter(start, 7, epochDay)
            RecurrenceKind.EVERY_N_DAYS -> stepOnOrAfter(start, rule.interval.toLong(), epochDay)
            RecurrenceKind.EVERY_N_WEEKS -> stepOnOrAfter(start, 7L * rule.interval, epochDay)
            RecurrenceKind.MONTHLY -> {
                val dayOfMonth = LocalDate.ofEpochDay(start).dayOfMonth
                generateSequence(LocalDate.ofEpochDay(epochDay).withDayOfMonth(1)) { it.plusMonths(1) }
                    .map { month -> month.withDayOfMonth(minOf(dayOfMonth, month.lengthOfMonth())).toEpochDay() }
                    .first { it >= epochDay }
            }
        }
    }

    private fun stepOnOrAfter(start: Long, step: Long, epochDay: Long): Long {
        if (epochDay <= start) return start
        val steps = (epochDay - start + step - 1) / step
        return start + steps * step
    }

    private val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
}
