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

/** Per-asset maintenance aggregates are exhaustive and farm-scoped; the bounded list orders deterministically. */
@RunWith(AndroidJUnit4::class)
class AssetRecordQueryTest {
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
    fun maintenanceSummariesCoverEveryEventBeyondTheListBound() = runBlocking {
        val maintenance = database.maintenance()
        repeat(520) { maintenance.insert(MaintenanceEventEntity("a$it", farmA, "t1", "Service", 10L + it, null)) }
        maintenance.insert(MaintenanceEventEntity("p1", farmA, "p1", "Seal", 3, "leak"))
        maintenance.insert(MaintenanceEventEntity("b1", farmB, "t1", "Service", 999, null))

        assertEquals(500, maintenance.recent(farmA, 500).size)
        assertEquals(521, maintenance.count(farmA))
        assertEquals(1, maintenance.count(farmB))
        assertEquals(listOf(AssetServiceSummary("p1", 1, 3), AssetServiceSummary("t1", 520, 529)), maintenance.summaryByAsset(farmA))
        assertEquals(listOf(AssetServiceSummary("t1", 1, 999)), maintenance.summaryByAsset(farmB))
    }

    @Test
    fun sameDayMaintenanceOrdersNewestFirstThenById() = runBlocking {
        val maintenance = database.maintenance()
        maintenance.insert(MaintenanceEventEntity("m-c", farmA, "t1", "C", 5, null))
        maintenance.insert(MaintenanceEventEntity("m-a", farmA, "t1", "A", 5, null))
        maintenance.insert(MaintenanceEventEntity("m-b", farmA, "t1", "B", 6, null))

        assertEquals(listOf("m-b", "m-a", "m-c"), maintenance.recent(farmA, 10).map { it.id })
    }
}
