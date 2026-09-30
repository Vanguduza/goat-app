package com.farmos.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.LocalAccountEntity
import com.farmos.core.model.LocalCommandContext
import com.farmos.feature.ops.TaskAssigneeChange
import com.farmos.feature.ops.TaskEditDraft
import com.farmos.feature.ops.TaskEditScope
import com.farmos.feature.ops.TaskRepeat
import com.farmos.feature.ops.TaskSeriesDraft
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The task board's planning adapter (D-020): who may plan, what the board lists and how edits are sent. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-rUS")
class TaskPlanningTest {
    private val farm = "99999999-9999-4999-8999-999999999999"
    private val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FarmOsDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    @After
    fun tearDown() = database.close()

    private fun context() = LocalCommandContext(farm, "manager-1", "A", UUID.randomUUID().toString(), 1_790_000_000_000)

    private fun draft(repeat: TaskRepeat, start: LocalDate, assignee: String? = null) =
        TaskSeriesDraft("Check troughs", "ops", "WATER", start, repeat, 1, null, assignee)

    @Test
    fun supervisorsAndManagementPlanWorkersDoNot() {
        assertTrue(canPlanFarmWork("OWNER"))
        assertTrue(canPlanFarmWork("MANAGER"))
        assertTrue(canPlanFarmWork("supervisor"))
        assertTrue(canPlanFarmWork("farm_manager"))
        assertFalse(canPlanFarmWork("WORKER"))
        assertFalse(canPlanFarmWork("VIEWER"))
        assertFalse(canPlanFarmWork("buyer"))

        val worker = TaskPlanning(database, farm, canPlanWork = false, currentAccountId = "w-1")
        assertThrows(IllegalStateException::class.java) {
            runBlocking { worker.create(draft(TaskRepeat.DAILY, LocalDate.of(2026, 10, 1)), context()) }
        }
        assertEquals(0, runBlocking { database.taskSeries().active(farm).size })
    }

    @Test
    fun theBoardListsOccurrencesWithinTheHorizonAndOnlyRepeatingSeriesAsRepeating(): Unit = runBlocking {
        database.localAccess().upsertAccount(
            LocalAccountEntity("farai", farm, "farai", "Farai", "WORKER", "ACTIVE", "PIN", "test-hash", 0, null, null, 1),
        )
        val planning = TaskPlanning(database, farm, canPlanWork = true, currentAccountId = "manager-1")
        val today = LocalDate.of(2026, 10, 1).toEpochDay()
        planning.create(draft(TaskRepeat.DAILY, LocalDate.of(2026, 10, 1), assignee = "farai"), context())
        planning.create(draft(TaskRepeat.NONE, LocalDate.of(2026, 10, 3), assignee = "farai").copy(title = "Fix gate"), context())

        com.farmos.data.herd.WorkerRegisterCommands(database, farm).create(com.farmos.domain.ops.CreateFarmWorker("w1", "Tendai"), context())
        assertEquals(listOf("farai" to "Farai", "worker:w1" to "Tendai · worker"), planning.loadAssignees().map { it.key to it.label })
        val occurrences = planning.openOccurrences(today)
        // Daily from today through the 30-day horizon, plus the one-off assigned task.
        assertEquals(31 + 1, occurrences.size)
        assertEquals(today + TaskPlanning.HORIZON_DAYS, occurrences.maxOf { it.dueEpochDay })

        val labels = planning.repeatLabels()
        assertEquals(setOf("Daily", null), labels.values.toSet())
        val rows = planning.seriesRows(today, mapOf("farai" to "Farai"))
        assertEquals(listOf("Check troughs"), rows.map { it.title })
        assertEquals(today, rows.single().nextEpochDay)
        assertEquals("Farai", rows.single().assigneeLabel)
    }

    @Test
    fun thisAndFutureFromTheFirstOccurrenceChangesTheWholeSeries(): Unit = runBlocking {
        val planning = TaskPlanning(database, farm, canPlanWork = true, currentAccountId = null)
        val start = LocalDate.of(2026, 10, 1)
        planning.create(draft(TaskRepeat.DAILY, start), context())
        val seriesId = database.taskSeries().active(farm).single().id

        planning.edit(
            TaskEditDraft(seriesId, start.toEpochDay(), TaskEditScope.THIS_AND_FUTURE, "Troughs", TaskAssigneeChange(null), null, TaskRepeat.WEEKLY, null),
            context(),
        )
        val series = database.taskSeries().active(farm).single()
        assertEquals(seriesId, series.id)
        assertEquals("Troughs", series.title)
        assertEquals("WEEKLY", series.recurrenceKind)
        assertNull(series.endEpochDay)
    }
}
