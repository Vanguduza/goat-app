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

/** Per-paddock grazing summaries are exhaustive and farm-scoped; open sessions are counted separately. */
@RunWith(AndroidJUnit4::class)
class PastureRecordQueryTest {
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
    fun paddockSummariesCoverEverySessionBeyondTheListBound() = runBlocking {
        val grazing = database.grazing()
        repeat(520) { grazing.insert(GrazingSessionEntity("a$it", farmA, "p1", "g1", "goat", 10L + it, 11L + it, 20)) }
        grazing.insert(GrazingSessionEntity("a-open", farmA, "p1", "g1", "goat", 600, null, 42))
        grazing.insert(GrazingSessionEntity("a-p2", farmA, "p2", "g2", "sheep", 3, 5, 30))
        grazing.insert(GrazingSessionEntity("b1", farmB, "p1", "g1", "goat", 1, null, 99))

        assertEquals(500, grazing.recent(farmA, 500).size)
        assertEquals("a-open", grazing.recent(farmA, 1).single().id)
        assertEquals(522, grazing.count(farmA))
        assertEquals(
            listOf(
                PaddockGrazingSummary("p1", 521, 1, 42, 10, 600, 530),
                PaddockGrazingSummary("p2", 1, 0, 0, 3, 3, 5),
            ),
            grazing.summaryByPaddock(farmA),
        )
        assertEquals(listOf(PaddockGrazingSummary("p1", 1, 1, 99, 1, 1, null)), grazing.summaryByPaddock(farmB))
    }

    @Test
    fun paddockListIncludesInactivePaddocksWithinTheFarmOnly() = runBlocking {
        val paddocks = database.paddocks()
        paddocks.insert(PaddockEntity("p2", farmA, "P2", "River", null, "river", false, active = false))
        paddocks.insert(PaddockEntity("p1", farmA, "P1", "Flat", 12_000, "trough", true, active = true))
        paddocks.insert(PaddockEntity("p9", farmB, "P9", "Other", null, "none", false, active = true))

        assertEquals(listOf("p1", "p2"), paddocks.forFarm(farmA).map { it.id })
        assertEquals(listOf("p1"), paddocks.active(farmA).map { it.id })
    }
}
