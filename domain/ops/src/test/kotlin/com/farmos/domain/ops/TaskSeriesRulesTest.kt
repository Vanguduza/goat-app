package com.farmos.domain.ops

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TaskSeriesRulesTest {
    private fun create(kind: String = "DAILY", interval: Int = 1, end: Long? = null, assignee: TaskAssignee = TaskAssignee()) =
        CreateTaskSeries("s", "ops", "CODE", "Title", kind, interval, startEpochDay = 100, endEpochDay = end, assignee = assignee)

    @Test
    fun aSeriesNeedsAValidRuleAndOneAssignee() {
        assertNull(TaskSeriesRules.create(create()))
        assertNull(TaskSeriesRules.create(create("EVERY_N_WEEKS", 2, end = 200, assignee = TaskAssignee(workerId = "w-1"))))
        assertEquals("Unknown repeat: YEARLY", TaskSeriesRules.create(create("YEARLY")))
        assertEquals("A series cannot end before it starts", TaskSeriesRules.create(create(end = 99)))
        assertEquals("Assign a task to an account or a worker, not both", TaskSeriesRules.create(create(assignee = TaskAssignee("a", "w"))))
    }

    @Test
    fun editsStayWithinTheirScope() {
        fun edit(scope: String, moved: Long? = null, kind: String? = null, newId: String? = null) =
            TaskSeriesRules.edit(EditTaskSeries("s", 105, scope, newSeriesId = newId, movedToEpochDay = moved, recurrenceKind = kind))
        assertNull(edit("THIS", moved = 106))
        assertEquals("Only a single occurrence can be moved", edit("SERIES", moved = 106))
        assertEquals("A single occurrence cannot change how the task repeats", edit("THIS", kind = "WEEKLY"))
        assertEquals("Changing this and future occurrences needs a new series id", edit("THIS_AND_FUTURE", kind = "WEEKLY"))
        assertNull(edit("THIS_AND_FUTURE", kind = "WEEKLY", newId = "s-2"))
        assertEquals("Choose what to change: this, this and future, or the series", edit("ALL"))
    }
}
