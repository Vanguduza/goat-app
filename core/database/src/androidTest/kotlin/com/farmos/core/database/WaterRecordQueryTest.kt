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

/** Water source totals are exhaustive and farm-scoped; the bounded list orders deterministically. */
@RunWith(AndroidJUnit4::class)
class WaterRecordQueryTest {
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
    fun sourceTotalsCoverEveryRecordBeyondTheListBound() = runBlocking {
        val water = database.water()
        repeat(520) { water.insert(WaterRecordEntity("a$it", farmA, "trough", 1_500, 10L + it)) }
        water.insert(WaterRecordEntity("a-bore", farmA, "borehole", 7, 3))
        water.insert(WaterRecordEntity("b1", farmB, "trough", 99_999, 1))

        assertEquals(500, water.recent(farmA, 500).size)
        assertEquals(521, water.count(farmA))
        assertEquals(1, water.count(farmB))
        assertEquals(
            listOf(WaterSourceTotal("borehole", 7, 1, 3, 3), WaterSourceTotal("trough", 780_000, 520, 10, 529)),
            water.totalsBySource(farmA),
        )
        assertEquals(listOf(WaterSourceTotal("trough", 99_999, 1, 1, 1)), water.totalsBySource(farmB))
    }

    @Test
    fun sameDayRecordsOrderNewestFirstThenById() = runBlocking {
        val water = database.water()
        water.insert(WaterRecordEntity("w-c", farmA, "trough", 1, 5))
        water.insert(WaterRecordEntity("w-a", farmA, "trough", 1, 5))
        water.insert(WaterRecordEntity("w-b", farmA, "trough", 1, 6))

        assertEquals(listOf("w-b", "w-a", "w-c"), water.recent(farmA, 10).map { it.id })
    }
}
