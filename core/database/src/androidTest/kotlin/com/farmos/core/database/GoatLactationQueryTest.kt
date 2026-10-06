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

/** Goat milk aggregates cover every record, stay per goat and per farm, and sum each goat's latest day. */
@RunWith(AndroidJUnit4::class)
class GoatLactationQueryTest {
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
    fun milkTotalsAndLatestDayAreExhaustiveAndPerGoat() = runBlocking {
        val lifecycle = database.lifecycle()
        repeat(600) { lifecycle.insertMilk(GoatMilkEntity("n$it", farmA, "nala", 1_000, 10L + it / 2)) }
        // Latest day for nala is 309 (two records of 1 L each); a third milking that day adds 0.5 L.
        lifecycle.insertMilk(GoatMilkEntity("n-extra", farmA, "nala", 500, 309))
        lifecycle.insertMilk(GoatMilkEntity("z1", farmA, "zara", 2_250, 40))
        lifecycle.insertMilk(GoatMilkEntity("b1", farmB, "nala", 99_999, 500))

        assertEquals(
            listOf(GoatMilkTotal("nala", 600_500, 601, 10, 309), GoatMilkTotal("zara", 2_250, 1, 40, 40)),
            lifecycle.goatMilkTotals(farmA),
        )
        assertEquals(listOf(GoatMilkDayTotal("nala", 2_500), GoatMilkDayTotal("zara", 2_250)), lifecycle.goatMilkLatestDay(farmA))
        assertEquals(listOf(GoatMilkDayTotal("nala", 99_999)), lifecycle.goatMilkLatestDay(farmB))
    }

    @Test
    fun sccCountsAreFarmScopedPerGoat() = runBlocking {
        val lifecycle = database.lifecycle()
        lifecycle.insertGoatScc(GoatSccEntity("s1", farmA, "nala", 300_000, 40, 10))
        lifecycle.insertGoatScc(GoatSccEntity("s2", farmA, "nala", 250_000, 70, 40))
        lifecycle.insertGoatScc(GoatSccEntity("s3", farmA, "zara", 150_000, null, 12))
        lifecycle.insertGoatScc(GoatSccEntity("s4", farmB, "nala", 999_000, null, 1))

        assertEquals(listOf(RecordKeyCount("nala", 2), RecordKeyCount("zara", 1)), lifecycle.goatSccCounts(farmA))
        assertEquals(listOf(RecordKeyCount("nala", 1)), lifecycle.goatSccCounts(farmB))
    }
}
