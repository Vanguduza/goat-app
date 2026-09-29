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

/** Rabbit wave-event reads stay inside one farm, and newest first. */
@RunWith(AndroidJUnit4::class)
class RabbitRecordQueryTest {
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
    fun waveEventReadsAreFarmScopedAndNewestFirst() = runBlocking {
        val lifecycle = database.lifecycle()
        lifecycle.insertPalpation(RabbitPalpationEntity("p1", farmA, "w1", "pregnant", 10))
        lifecycle.insertPalpation(RabbitPalpationEntity("p2", farmA, "w2", "open", 12))
        lifecycle.insertPalpation(RabbitPalpationEntity("p3", farmB, "w1", "open", 13))
        lifecycle.insertKindling(RabbitKindlingEntity("k1", farmA, "w1", 8, 1, 30))
        lifecycle.insertKindling(RabbitKindlingEntity("k2", farmA, "w1", 2, 0, 31))
        lifecycle.insertKindling(RabbitKindlingEntity("k3", farmB, "w9", 5, 0, 31))
        lifecycle.insertFoster(RabbitFosterEntity("f1", farmA, "w1", "w2", 2, true, 32))
        lifecycle.insertFoster(RabbitFosterEntity("f2", farmB, "w1", "w2", 1, false, 32))
        lifecycle.insertWean(RabbitWeanEntity("n1", farmA, "w1", 7, 60))
        lifecycle.insertWean(RabbitWeanEntity("n2", farmB, "w1", 4, 60))
        lifecycle.insertMatingOutcome(RabbitMatingOutcomeEntity("o1", farmA, "w2", "false_pregnancy", 40))
        lifecycle.insertMatingOutcome(RabbitMatingOutcomeEntity("o2", farmB, "w2", "false_pregnancy", 40))

        assertEquals(listOf("p2", "p1"), lifecycle.rabbitPalpations(farmA).map { it.id })
        assertEquals(listOf("k2", "k1"), lifecycle.rabbitKindlings(farmA).map { it.id })
        assertEquals(listOf("w1"), lifecycle.rabbitKindledWaveIds(farmA))
        assertEquals(listOf("w9"), lifecycle.rabbitKindledWaveIds(farmB))
        assertEquals(listOf("f1"), lifecycle.rabbitFosters(farmA).map { it.id })
        assertEquals(listOf("n1"), lifecycle.rabbitWeans(farmA).map { it.id })
        assertEquals(listOf("o1"), lifecycle.rabbitMatingOutcomes(farmA).map { it.id })
        assertEquals(listOf("o2"), lifecycle.rabbitMatingOutcomes(farmB).map { it.id })
    }
}
