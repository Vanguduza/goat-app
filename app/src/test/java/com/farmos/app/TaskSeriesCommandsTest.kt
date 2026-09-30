package com.farmos.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.LocalAccountEntity
import com.farmos.core.model.LocalCommandContext
import com.farmos.data.herd.TaskSeriesCommands
import com.farmos.data.herd.TaskSeriesQueries
import com.farmos.domain.ops.CompleteTaskOccurrence
import com.farmos.domain.ops.CreateTaskSeries
import com.farmos.domain.ops.EditTaskSeries
import com.farmos.domain.ops.EndTaskSeries
import com.farmos.domain.ops.TaskAssignee
import com.farmos.domain.ops.TaskRecurrenceSchedule
import com.farmos.domain.replication.LocalPeerTransport
import com.farmos.domain.replication.SyncSession
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Owner decision D-020: a repeating task derives its occurrences; completing one stores it once on every
 * device; edits change this, this and future, or the series, and never a completed occurrence.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-rUS")
class TaskSeriesCommandsTest {
    private val farm = "88888888-8888-4888-8888-888888888888"
    private val databases = mutableListOf<FarmOsDatabase>()

    private fun database() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FarmOsDatabase::class.java)
        .allowMainThreadQueries()
        .build()
        .also { databases += it }

    @After
    fun tearDown() = databases.forEach { it.close() }

    private fun day(text: String) = LocalDate.parse(text).toEpochDay()
    private fun context(device: String = "A") = LocalCommandContext(farm, "manager-1", device, UUID.randomUUID().toString(), 1_790_000_000_000)

    private fun account(id: String) = LocalAccountEntity(
        accountId = id, farmId = farm, username = id, displayName = id, role = "WORKER", status = "ACTIVE",
        credentialKind = "PIN", credentialHash = "test-hash", failedAttempts = 0, lockedUntilEpochMillis = null,
        workerId = null, createdAtEpochMillis = 1,
    )

    private suspend fun FarmOsDatabase.titles(from: String, to: String, me: String? = null) =
        TaskSeriesQueries(this, farm).openOccurrences(day(from), day(to), me).map { "${LocalDate.ofEpochDay(it.dueEpochDay)} ${it.title}" }

    @Test
    fun occurrencesAreDerivedCompletedOnceAndEditedByScope(): Unit = runBlocking {
        val db = database()
        db.localAccess().upsertAccount(account("farai"))
        val tasks = TaskSeriesCommands(db, farm)
        tasks.create(CreateTaskSeries("water", "ops", "WATER_CHECK", "Check troughs", "DAILY", startEpochDay = day("2026-10-01"), assignee = TaskAssignee(accountId = "farai")), context())

        assertEquals(listOf("2026-10-01 Check troughs", "2026-10-02 Check troughs", "2026-10-03 Check troughs"), db.titles("2026-10-01", "2026-10-03"))
        assertEquals(3, db.titles("2026-10-01", "2026-10-03", me = "farai").size)
        assertEquals(0, db.titles("2026-10-01", "2026-10-03", me = "someone-else").size)

        // Completing the first occurrence stores it once; completing it again changes nothing.
        tasks.complete(CompleteTaskOccurrence("water", day("2026-10-01")), context())
        tasks.complete(CompleteTaskOccurrence("water", day("2026-10-01")), context())
        val doneId = TaskRecurrenceSchedule.occurrenceId("water", day("2026-10-01"))
        assertEquals("done", db.tasks().get(farm, doneId)!!.status)
        assertEquals(listOf("2026-10-02 Check troughs", "2026-10-03 Check troughs"), db.titles("2026-10-01", "2026-10-03"))

        // This occurrence alone: moved a day and retitled.
        tasks.edit(EditTaskSeries("water", day("2026-10-02"), "THIS", title = "Check troughs and floats", movedToEpochDay = day("2026-10-04")), context())
        assertEquals(listOf("2026-10-03 Check troughs", "2026-10-04 Check troughs", "2026-10-04 Check troughs and floats"), db.titles("2026-10-02", "2026-10-04"))

        // This and future from 10-05: every two days under a new series; earlier occurrences keep the old rule.
        tasks.edit(EditTaskSeries("water", day("2026-10-05"), "THIS_AND_FUTURE", newSeriesId = "water-2", recurrenceKind = "EVERY_N_DAYS", recurrenceInterval = 2), context())
        assertEquals(
            listOf("2026-10-03 Check troughs", "2026-10-04 Check troughs", "2026-10-04 Check troughs and floats", "2026-10-05 Check troughs", "2026-10-07 Check troughs"),
            db.titles("2026-10-03", "2026-10-08"),
        )

        // The whole series is retitled; the completed occurrence keeps what was done.
        tasks.edit(EditTaskSeries("water-2", day("2026-10-05"), "SERIES", title = "Troughs"), context())
        assertEquals(listOf("2026-10-05 Troughs", "2026-10-07 Troughs"), db.titles("2026-10-05", "2026-10-08"))
        assertEquals("Check troughs", db.tasks().get(farm, doneId)!!.title)

        // A completed occurrence is never changed.
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { tasks.edit(EditTaskSeries("water", day("2026-10-01"), "THIS", title = "Rewritten"), context()) }
        }

        // Ending the series after 10-07 leaves no later occurrence.
        tasks.end(EndTaskSeries("water-2", day("2026-10-07")), context())
        assertEquals(listOf("2026-10-05 Troughs", "2026-10-07 Troughs"), db.titles("2026-10-05", "2026-10-20"))
    }

    @Test
    fun assignmentIsCheckedAndCompletionConvergesAcrossDevices(): Unit = runBlocking {
        val aDb = database()
        val bDb = database()
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { TaskSeriesCommands(aDb, farm).create(CreateTaskSeries("s", "ops", "C", "Title", "DAILY", startEpochDay = 1, assignee = TaskAssignee(accountId = "nobody")), context()) }
        }
        TaskSeriesCommands(aDb, farm).create(CreateTaskSeries("feed", "ops", "FEED", "Feed calves", "WEEKDAYS", startEpochDay = day("2026-10-05")), context("A"))
        val a = RoomReplicaEndpoint(aDb, farm, "A", replicationAppliers).apply { registerPairedDevice("B", "B") }
        val b = RoomReplicaEndpoint(bDb, farm, "B", replicationAppliers).apply { registerPairedDevice("A", "A") }
        SyncSession.run(b, LocalPeerTransport(a), remoteDeviceId = "A")

        // Both devices complete the same occurrence offline, then synchronise.
        TaskSeriesCommands(aDb, farm).complete(CompleteTaskOccurrence("feed", day("2026-10-05")), context("A"))
        TaskSeriesCommands(bDb, farm).complete(CompleteTaskOccurrence("feed", day("2026-10-05")), context("B"))
        SyncSession.run(b, LocalPeerTransport(a), remoteDeviceId = "A")

        for (db in listOf(aDb, bDb)) {
            assertEquals(1, db.taskSeries().storedOccurrences(farm, "feed").size)
            assertTrue(db.titles("2026-10-05", "2026-10-06").single().startsWith("2026-10-06"))
        }
    }
}
