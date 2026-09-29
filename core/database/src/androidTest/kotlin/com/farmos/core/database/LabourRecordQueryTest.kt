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

/** Labour minutes per worker label are exhaustive and farm-scoped beyond the bounded entry list. */
@RunWith(AndroidJUnit4::class)
class LabourRecordQueryTest {
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
    fun workerTotalsCoverEveryEntryBeyondTheListBound() = runBlocking {
        val labour = database.labour()
        repeat(520) { labour.insert(LabourEntryEntity("a$it", farmA, "Tendai", "MILK", 60, 10L + it, null)) }
        labour.insert(LabourEntryEntity("a-rudo", farmA, "Rudo", "CHECK", 30, 3, "gate"))
        labour.insert(LabourEntryEntity("b1", farmB, "Tendai", "MILK", 999, 1, null))

        assertEquals(500, labour.recent(farmA, 500).size)
        assertEquals(521, labour.count(farmA))
        assertEquals(1, labour.count(farmB))
        assertEquals(
            listOf(LabourWorkerTotal("Rudo", 30, 1, 3), LabourWorkerTotal("Tendai", 31_200, 520, 529)),
            labour.totalsByWorkerName(farmA),
        )
        assertEquals(listOf(LabourWorkerTotal("Tendai", 999, 1, 1)), labour.totalsByWorkerName(farmB))
    }
}
