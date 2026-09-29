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

/** Feed item totals are exhaustive, per item and farm-scoped; the bounded list orders deterministically. */
@RunWith(AndroidJUnit4::class)
class FeedRecordQueryTest {
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
    fun itemTotalsCoverEveryIssueBeyondTheListBound() = runBlocking {
        val feed = database.feedIssues()
        repeat(520) { feed.insert(FeedIssueEntity("a$it", farmA, "mash", "g1", 2_500, 10L + it)) }
        feed.insert(FeedIssueEntity("a-hay", farmA, "hay", null, 40_000, 3))
        feed.insert(FeedIssueEntity("b1", farmB, "mash", null, 99_999, 1))

        assertEquals(500, feed.recent(farmA, 500).size)
        assertEquals(521, feed.count(farmA))
        assertEquals(1, feed.count(farmB))
        assertEquals(
            listOf(FeedItemTotal("hay", 40_000, 1, 3, 3), FeedItemTotal("mash", 1_300_000, 520, 10, 529)),
            feed.totalsByItem(farmA),
        )
        assertEquals(listOf(FeedItemTotal("mash", 99_999, 1, 1, 1)), feed.totalsByItem(farmB))
    }

    @Test
    fun sameDayIssuesOrderNewestFirstThenById() = runBlocking {
        val feed = database.feedIssues()
        feed.insert(FeedIssueEntity("f-c", farmA, "mash", null, 1, 5))
        feed.insert(FeedIssueEntity("f-a", farmA, "mash", null, 1, 5))
        feed.insert(FeedIssueEntity("f-b", farmA, "mash", null, 1, 6))

        assertEquals(listOf("f-b", "f-a", "f-c"), feed.recent(farmA, 10).map { it.id })
    }
}
