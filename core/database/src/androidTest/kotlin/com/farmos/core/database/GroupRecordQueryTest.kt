package com.farmos.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Per-group census, grazing and feed reads are exhaustive, farm- and group-scoped, newest first. */
@RunWith(AndroidJUnit4::class)
class GroupRecordQueryTest {
    private lateinit var database: FarmOsDatabase
    private val farmA = "11111111-1111-4111-8111-111111111111"
    private val farmB = "22222222-2222-4222-8222-222222222222"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, FarmOsDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun groupReadsStayInsideOneFarmAndGroup() = runBlocking {
        val lifecycle = database.lifecycle()
        repeat(600) { lifecycle.insertCensus(GroupCensusEntity("c$it", farmA, "g1", 40, it.toLong())) }
        lifecycle.insertCensus(GroupCensusEntity("c-g2", farmA, "g2", 10, 5))
        lifecycle.insertCensus(GroupCensusEntity("c-b", farmB, "g1", 99, 5))
        val grazing = database.grazing()
        grazing.insert(GrazingSessionEntity("s-b", farmA, "p1", "g1", "goat", 5, null, 40))
        grazing.insert(GrazingSessionEntity("s-a", farmA, "p1", "g1", "goat", 5, 9, 40))
        grazing.insert(GrazingSessionEntity("s-g2", farmA, "p1", "g2", "sheep", 7, null, 10))
        grazing.insert(GrazingSessionEntity("s-farmB", farmB, "p1", "g1", "goat", 8, null, 99))
        val feed = database.feedIssues()
        feed.insert(FeedIssueEntity("f2", farmA, "i1", "g1", 1_000, 4))
        feed.insert(FeedIssueEntity("f1", farmA, "i1", "g1", 1_000, 6))
        feed.insert(FeedIssueEntity("f-none", farmA, "i1", null, 1_000, 6))
        feed.insert(FeedIssueEntity("f-b", farmB, "i1", "g1", 1_000, 6))

        val census = lifecycle.censusForGroup(farmA, "g1")
        assertEquals(600, census.size)
        assertEquals("c599", census.first().id)
        assertEquals(listOf("c-b"), lifecycle.censusForGroup(farmB, "g1").map { it.id })
        assertEquals(listOf("s-a", "s-b"), grazing.forGroup(farmA, "g1").map { it.id })
        assertEquals(listOf("s-farmB"), grazing.forGroup(farmB, "g1").map { it.id })
        assertEquals(listOf("f1", "f2"), feed.forGroup(farmA, "g1").map { it.id })
        assertEquals(listOf("f-b"), feed.forGroup(farmB, "g1").map { it.id })
    }
}
