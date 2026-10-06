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

/**
 * Whole-farm counts shown on the farm home and health dashboard are exhaustive and farm-scoped:
 * they stay correct past the bounded lists (50 withdrawals, 100 completed tasks, 500 goats, 200
 * health rows) and keep the same filters those lists used.
 */
@RunWith(AndroidJUnit4::class)
class CountTruthQueryTest {
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
    fun homeCountsCoverEveryRowWithTheListFilters() = runBlocking {
        val animals = database.animals()
        repeat(520) { animals.insert(AnimalEntity("g$it", farmA, "G-$it", null, "goat", "FEMALE", "active", null, updatedAtEpochMillis = 1)) }
        animals.insert(AnimalEntity("g-sold", farmA, "G-SOLD", null, "goat", "FEMALE", "sold", null, updatedAtEpochMillis = 1))
        animals.insert(AnimalEntity("g-closed", farmA, "G-CLOSED", null, "goat", "FEMALE", "closed", null, updatedAtEpochMillis = 1))
        animals.insert(AnimalEntity("s1", farmA, "S-1", null, "sheep", "FEMALE", "active", null, updatedAtEpochMillis = 1))
        animals.insert(AnimalEntity("g-b", farmB, "G-B", null, "goat", "FEMALE", "active", null, updatedAtEpochMillis = 1))
        val tasks = database.tasks()
        repeat(130) { tasks.insert(TaskEntity("t$it", farmA, "goat", "CHECK", "Check $it", 1, "done", null, null, null, it.toLong())) }
        tasks.insert(TaskEntity("t-open", farmA, "goat", "CHECK", "Open", 1, "open", null, null, null, 1))
        tasks.insert(TaskEntity("t-b", farmB, "goat", "CHECK", "Other farm", 1, "done", null, null, null, 1))
        val lifecycle = database.lifecycle()
        repeat(60) { lifecycle.insertWithdrawal(WithdrawalWindowEntity("w$it", farmA, "tr", "Penicillin", "milk", 100L + it)) }
        lifecycle.insertWithdrawal(WithdrawalWindowEntity("w-today", farmA, "tr", "Penicillin", "meat", 100))
        lifecycle.insertWithdrawal(WithdrawalWindowEntity("w-ended", farmA, "tr", "Penicillin", "meat", 99))
        lifecycle.insertWithdrawal(WithdrawalWindowEntity("w-b", farmB, "tr", "Penicillin", "milk", 500))

        assertEquals(521, animals.countBySpecies(farmA, "goat"))
        assertEquals(1, animals.countBySpecies(farmB, "goat"))
        assertEquals(100, tasks.completedForFarm(farmA, 100).size)
        assertEquals(130, tasks.countCompletedForFarm(farmA))
        assertEquals(1, tasks.countCompletedForFarm(farmB))
        assertEquals(50, lifecycle.withdrawals(farmA, 50).size)
        assertEquals(61, lifecycle.activeWithdrawalCount(farmA, todayEpochDay = 100))
        assertEquals(62, lifecycle.withdrawalCount(farmA))
        assertEquals(1, lifecycle.activeWithdrawalCount(farmB, todayEpochDay = 100))
    }

    @Test
    fun healthCountsCoverEveryRowBeyondTheListBound() = runBlocking {
        val treatments = database.treatments()
        repeat(230) { treatments.insert(HealthTreatmentEntity("tr$it", farmA, null, "goat", "f1", "reason", 3, null, null, it.toLong())) }
        treatments.insert(HealthTreatmentEntity("tr-b", farmB, null, "goat", "f1", "reason", 3, null, null, 1))
        val observations = database.healthObservations()
        repeat(3) { observations.insert(HealthObservationEntity("o$it", farmA, null, "goat", "cough", null, false, it.toLong())) }
        val lifecycle = database.lifecycle()
        lifecycle.insertVetVisit(VetVisitEntity("v1", farmA, "goat", null, null, "check", "Dr Moyo", 5))
        lifecycle.insertLab(LabResultEntity("l1", farmA, null, null, "FEC", "450 epg", null, 5))
        lifecycle.insertLab(LabResultEntity("l-b", farmB, null, null, "FEC", "0 epg", null, 5))

        assertEquals(200, treatments.recent(farmA, 200).size)
        assertEquals(230, treatments.count(farmA))
        assertEquals(1, treatments.count(farmB))
        assertEquals(3, observations.count(farmA))
        assertEquals(0, observations.count(farmB))
        assertEquals(1, lifecycle.vetVisitCount(farmA))
        assertEquals(1, lifecycle.labResultCount(farmA))
        assertEquals(1, lifecycle.labResultCount(farmB))
    }
}
